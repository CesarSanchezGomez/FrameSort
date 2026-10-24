package com.cesarcosmico.framesort.service;

import com.cesarcosmico.framesort.config.FrameSortSettings;
import com.cesarcosmico.framesort.config.SorterSettings;
import com.cesarcosmico.framesort.item.ItemLook;
import com.cesarcosmico.framesort.item.ItemOverlay;
import com.cesarcosmico.framesort.item.ItemTemplate;
import com.cesarcosmico.framesort.item.Keys;
import com.cesarcosmico.framesort.item.MarkerCodec;
import com.cesarcosmico.framesort.item.SorterCodec;
import com.cesarcosmico.framesort.model.BlockKey;
import com.cesarcosmico.framesort.model.ItemReference;
import io.papermc.paper.datacomponent.DataComponentType;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.BundleContents;
import io.papermc.paper.datacomponent.item.ItemContainerContents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.data.type.Chest;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/** Sorters are spread over the ticks so they never all act at once. */
public final class SorterService {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    // Shulker boxes inside bundles inside shulker boxes…: deep enough for any real setup, and bounded.
    private static final int MAX_DEPTH = 8;
    // A turn sends a stack's worth however the items are packed, so bundles of single items don't crawl; the same
    // bound caps the tries when items have nowhere to go, so one that doesn't fit never stalls the rest.
    private static final int PER_TURN = 64;
    // A few small specks in the plugin's colours over the block: enough to see a turn, never enough to hide the build.
    private static final Particle.DustTransition ACTIVITY = new Particle.DustTransition(Color.fromRGB(0xF7B733),
            Color.fromRGB(0xFC4A1A), 0.6f);
    private static final int ACTIVITY_SPECKS = 4;
    // A sorter item's own look; anything else on a dropped container (its name, contents, lock, loot) is the block's.
    private static final Set<DataComponentType> SORTER_LOOK = Set.of(DataComponentTypes.ITEM_NAME,
            DataComponentTypes.LORE, DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE);

    private static final class Sorter {
        private final Block block;
        private final UUID owner;
        private long due;

        private Sorter(Block block, UUID owner, long due) {
            this.block = block;
            this.owner = owner;
            this.due = due;
        }
    }

    /** What the recipes were built from, so a reload that changes none of it sends nothing to the players. */
    private record RecipeSource(SorterSettings.@Nullable Recipe recipe, Set<Material> blocks, ItemLook item,
                                ItemReference activator, boolean requireMarked) {
    }

    private final Plugin plugin;
    private final Supplier<FrameSortSettings> settings;
    private final DeliveryService delivery;
    private final CustomItemResolver customItems;
    private final Map<BlockKey, Sorter> sorters = new HashMap<>();
    private final Set<NamespacedKey> recipes = new HashSet<>();
    private @Nullable RecipeSource registered;
    private long tick;
    private int nextPhase;

    public SorterService(Plugin plugin, Supplier<FrameSortSettings> settings, DeliveryService delivery,
                         CustomItemResolver customItems) {
        this.plugin = plugin;
        this.settings = settings;
        this.delivery = delivery;
        this.customItems = customItems;
    }

    /** Registers the sorters of a chunk that just loaded; only the catalog's containers are read. */
    public void load(Chunk chunk) {
        Set<Material> blocks = settings.get().sorter().blocks();
        for (BlockState state : chunk.getTileEntities(block -> blocks.contains(block.getType()), false)) {
            if (state instanceof Container container) {
                UUID owner = SorterCodec.owner(container);
                if (owner != null) {
                    register(state.getBlock(), owner);
                }
            }
        }
    }

    public void unload(Chunk chunk) {
        UUID world = chunk.getWorld().getUID();
        sorters.keySet().removeIf(key -> key.world().equals(world) && key.x() >> 4 == chunk.getX()
                && key.z() >> 4 == chunk.getZ());
    }

    /** Registers again every sorter in the loaded chunks, for the catalog may have changed. */
    public void loadAll() {
        sorters.clear();
        for (World world : plugin.getServer().getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                load(chunk);
            }
        }
    }

    /**
     * Makes a sorter of {@code block}, just placed by {@code player} from {@code hand}; false when it makes none. A
     * container taken out of the catalog still keeps the mark, and sorts again once it is back.
     */
    public boolean placed(Player player, Block block, ItemStack hand) {
        if (MarkerCodec.read(hand, Keys.SORTER_ITEM) == null
                || !(block.getState(false) instanceof Container container)) {
            return false;
        }
        SorterCodec.mark(container, player.getUniqueId());
        if (!settings.get().sorter().blocks().contains(block.getType())) {
            return false;
        }
        register(block, player.getUniqueId());
        return true;
    }

    /** @return whether the block was a sorter */
    public boolean broken(Block block) {
        return sorters.remove(FrameGeometry.key(block)) != null;
    }

    /**
     * Turns what a broken sorter dropped back into a sorter item. The drops also hold what spilled out of it, so only
     * an item alike to the block's own drop stands for it; any such one will do, as they are alike. A shulker box
     * can't hold another, so its own drop, which carries its contents, is the only one of its kind.
     */
    public void markDrop(BlockState state, List<Item> drops) {
        if (!(state instanceof Container container) || SorterCodec.owner(container) == null) {
            return;
        }
        Material material = state.getType();
        ItemStack alike = ItemStack.of(material);
        Component name = container.customName();
        if (name != null) {
            alike.setData(DataComponentTypes.CUSTOM_NAME, name);
        }
        boolean shulker = Tag.SHULKER_BOXES.isTagged(material);
        for (Item drop : drops) {
            ItemStack stack = drop.getItemStack();
            if (stack.getAmount() == 1 && stack.getType() == material && (shulker || stack.isSimilar(alike))) {
                ItemStack sorter = createItem(material, 1);
                sorter.copyDataFrom(stack, type -> !SORTER_LOOK.contains(type));
                drop.setItemStack(sorter);
                return;
            }
        }
    }

    /** The owner of the sorter on {@code block}, or {@code null} when it is no sorter. */
    public @Nullable UUID ownerOf(Block block) {
        Sorter sorter = sorters.get(FrameGeometry.key(block));
        return sorter == null ? null : sorter.owner;
    }

    public boolean isSorter(Block block) {
        return sorters.containsKey(FrameGeometry.key(block));
    }

    /** Whether turning a sorter's block into {@code to} ends it; an axe may turn a copper chest into another one. */
    public boolean endsSorter(Block block, Material to) {
        return isSorter(block) && !settings.get().sorter().blocks().contains(to);
    }

    /** The title a sorter's inventory shows instead of the container's own name, or {@code null} to keep that name. */
    public @Nullable Component title(Block block) {
        String title = settings.get().sorter().customName();
        return title != null && isSorter(block) ? MINI_MESSAGE.deserialize(title) : null;
    }

    public void wake(Block block) {
        Sorter sorter = sorters.get(FrameGeometry.key(block));
        if (sorter != null) {
            sorter.due = Math.min(sorter.due, tick + 1);
        }
    }

    public List<Block> blocks() {
        return sorters.values().stream().map(sorter -> sorter.block).toList();
    }

    /** A sorter item for {@code material}: placing it makes a sorter. */
    public ItemStack createItem(Material material, int amount) {
        ItemStack item = new ItemTemplate(material, Map.of(), settings.get().sorter().item())
                .create(amount, Placeholder.component("block", Component.translatable(material)));
        return MarkerCodec.mark(item, Keys.SORTER_ITEM, "sorter");
    }

    /** {@code null} when CraftEngine can't build the activator now: an ID it doesn't know, or a /ce reload. */
    public @Nullable ItemStack createActivator(int amount) {
        SorterSettings sorter = settings.get().sorter();
        ItemStack item = switch (sorter.activator()) {
            case ItemReference.Vanilla(Material material) ->
                    new ItemTemplate(material, sorter.activatorComponents(), sorter.activatorLook()).create(amount);
            case ItemReference.Custom custom -> {
                ItemStack built = customItems.item(custom, amount);
                yield built == null ? null : ItemOverlay.apply(built, sorter.activatorLook());
            }
        };
        return item == null ? null : MarkerCodec.mark(item, Keys.ACTIVATOR, "sorter");
    }

    /** How many items this sorter has sent; the count stays with the block. */
    public long sorted(Block block) {
        return block.getState(false) instanceof Container container ? SorterCodec.sorted(container) : 0;
    }

    /**
     * Registers one recipe per container of the catalog, unless nothing they are built from changed. A CraftEngine
     * activator or ingredient can't be built before CraftEngine loads its items: then nothing is registered, and
     * CraftEngine's reload calls this again.
     */
    public void registerRecipes() {
        SorterSettings sorter = settings.get().sorter();
        RecipeSource source = new RecipeSource(sorter.recipe(), sorter.blocks(), sorter.item(), sorter.activator(),
                sorter.requireMarked());
        if (source.equals(registered)) {
            return;
        }
        removeRecipes();
        SorterSettings.Recipe recipe = sorter.recipe();
        if (recipe == null) {
            registered = source;
            return;
        }
        Map<Character, RecipeChoice> ingredients = ingredients(recipe, sorter);
        if (ingredients == null) {
            return;
        }
        for (Material material : sorter.blocks()) {
            NamespacedKey key = new NamespacedKey(plugin, "sorter_" + material.name().toLowerCase(Locale.ROOT));
            ShapedRecipe shaped = new ShapedRecipe(key, createItem(material, 1));
            shaped.shape(recipe.shape().toArray(String[]::new));
            shaped.setIngredient(SorterSettings.Recipe.CONTAINER, RecipeChoice.predicateChoice(
                    item -> isRecipeContainer(item, material), ItemStack.of(material)));
            ingredients.forEach(shaped::setIngredient);
            if (plugin.getServer().addRecipe(shaped, true)) {
                recipes.add(key);
            }
        }
        registered = source;
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            discover(player);
        }
    }

    /** Takes the recipes out: while FrameSort is off, nothing may call into it. */
    public void removeRecipes() {
        for (NamespacedKey key : recipes) {
            plugin.getServer().removeRecipe(key, false);
        }
        recipes.clear();
        registered = null;
    }

    /** Shows the sorter recipes in the player's recipe book. */
    public void discover(Player player) {
        player.discoverRecipes(recipes);
    }

    public void tick() {
        tick++;
        SorterSettings sorter = settings.get().sorter();
        for (Sorter current : List.copyOf(sorters.values())) {
            if (current.due > tick) {
                continue;
            }
            Block block = current.block;
            // WorldEdit, a command or another plugin can change the block without any event FrameSort hears.
            if (!(block.getState(false) instanceof Container container) || SorterCodec.owner(container) == null) {
                sorters.remove(FrameGeometry.key(block));
                continue;
            }
            if (sorter.disableWhenPowered() && block.isBlockPowered()) {
                current.due = tick + 1;
                continue;
            }
            current.due = tick + sorter.tickRate();
            // A double chest would sort both halves: it waits until it is a single chest again.
            boolean doubleChest = block.getBlockData() instanceof Chest chest && chest.getType() != Chest.Type.SINGLE;
            if (sorter.blocks().contains(block.getType()) && !doubleChest) {
                dispense(current, sorter);
            }
        }
    }

    private void register(Block block, UUID owner) {
        SorterSettings sorter = settings.get().sorter();
        sorters.put(FrameGeometry.key(block), new Sorter(block, owner, tick + 1 + (nextPhase++ % sorter.tickRate())));
    }

    private void dispense(Sorter sorter, SorterSettings settings) {
        int sent = 0;
        // What found nowhere to go this turn is not tried again.
        List<ItemStack> missed = new ArrayList<>();
        for (int tries = 0; tries < PER_TURN && sent < PER_TURN; tries++) {
            // Other plugins' handlers run during a delivery and may break the container.
            if (!(sorter.block.getState(false) instanceof Container container)) {
                return;
            }
            Slot slot = pick(container.getInventory(), missed);
            if (slot == null) {
                break;
            }
            ItemStack tried = slot.get();
            int kept = Math.max(0, tried.getAmount() - (PER_TURN - sent));
            int moved = delivery.deliver(sorter.block, sorter.owner, settings.range(), new SlotSource(slot, kept));
            if (moved == 0) {
                missed.add(tried);
            }
            sent += moved;
        }
        if (sent == 0 || !(sorter.block.getState(false) instanceof Container container)) {
            return;
        }
        SorterCodec.addSorted(container, sent);
        if (settings.showActivity()) {
            sorter.block.getWorld().spawnParticle(Particle.DUST_COLOR_TRANSITION,
                    sorter.block.getLocation().add(0.5, 1.15, 0.5), ACTIVITY_SPECKS, 0.2, 0.05, 0.2, 0, ACTIVITY);
        }
    }

    /**
     * The choices of the activator and the other letters, the same for every container; {@code null} while a
     * CraftEngine item among them can't be built.
     */
    private @Nullable Map<Character, RecipeChoice> ingredients(SorterSettings.Recipe recipe, SorterSettings sorter) {
        Map<Character, RecipeChoice> choices = new HashMap<>();
        if (recipe.uses(SorterSettings.Recipe.ACTIVATOR)) {
            ItemStack example = sorter.requireMarked() ? createActivator(1) : example(sorter.activator());
            if (example == null) {
                return null;
            }
            choices.put(SorterSettings.Recipe.ACTIVATOR, RecipeChoice.predicateChoice(
                    item -> settings.get().sorter().isActivator(item, customItems::id), example));
        }
        for (Map.Entry<Character, ItemReference> ingredient : recipe.ingredients().entrySet()) {
            ItemReference reference = ingredient.getValue();
            ItemStack example = example(reference);
            if (example == null) {
                return null;
            }
            choices.put(ingredient.getKey(), RecipeChoice.predicateChoice(
                    item -> reference.matches(item.getType(), customItems.id(item)), example));
        }
        return choices;
    }

    private @Nullable ItemStack example(ItemReference reference) {
        return switch (reference) {
            case ItemReference.Vanilla(Material material) -> ItemStack.of(material);
            case ItemReference.Custom custom -> customItems.item(custom, 1);
        };
    }

    // An empty container of that kind: a filled shulker box would lose what it holds, and a sorter is one already.
    private boolean isRecipeContainer(ItemStack item, Material material) {
        return item.getType() == material && customItems.id(item) == null
                && TargetResolver.contents(item).isEmpty() && MarkerCodec.read(item, Keys.SORTER_ITEM) == null;
    }

    /** A sorter opens a shulker box or bundle with contents first; it goes whole once nothing inside can go. */
    public static boolean opens(ItemStack item) {
        return !TargetResolver.contents(item).isEmpty();
    }

    private static @Nullable Slot pick(Inventory inventory, List<ItemStack> missed) {
        List<ItemStack> contents = Arrays.stream(inventory.getContents())
                .map(item -> item == null ? ItemStack.empty() : item).toList();
        for (int index : shuffledFilled(contents)) {
            Slot found = pick(new Slot.InventorySlot(inventory, index), contents.get(index), missed, 0);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * Something in {@code slot} that has not missed this turn: a stack inside it first, or else the slot itself, so a
     * shulker box or bundle goes whole once nothing inside it can go. {@code item} is what the slot holds, read once.
     */
    private static @Nullable Slot pick(Slot slot, ItemStack item, List<ItemStack> missed, int depth) {
        if (missed.stream().anyMatch(item::isSimilar)) {
            return null;
        }
        if (depth < MAX_DEPTH) {
            ItemContainerContents container = item.getData(DataComponentTypes.CONTAINER);
            BundleContents bundle = item.getData(DataComponentTypes.BUNDLE_CONTENTS);
            List<ItemStack> inside = container != null ? container.contents()
                    : bundle != null ? bundle.contents() : List.of();
            for (int index : shuffledFilled(inside)) {
                Slot child = container != null ? new Slot.ContainerSlot(slot, index) : new Slot.BundleSlot(slot, index);
                Slot found = pick(child, inside.get(index), missed, depth + 1);
                if (found != null) {
                    return found;
                }
            }
        }
        return slot;
    }

    private static List<Integer> shuffledFilled(List<ItemStack> items) {
        List<Integer> filled = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            if (!items.get(i).isEmpty()) {
                filled.add(i);
            }
        }
        Collections.shuffle(filled, ThreadLocalRandom.current());
        return filled;
    }
}
