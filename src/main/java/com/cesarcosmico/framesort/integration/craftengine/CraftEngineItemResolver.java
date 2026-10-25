package com.cesarcosmico.framesort.integration.craftengine;

import com.cesarcosmico.framesort.model.Delivery;
import com.cesarcosmico.framesort.model.ItemReference;
import com.cesarcosmico.framesort.service.CustomItemResolver;
import net.kyori.adventure.text.Component;
import net.momirealms.craftengine.bukkit.api.BukkitAdaptor;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.bukkit.block.behavior.SimpleStorageBlockBehavior;
import net.momirealms.craftengine.bukkit.block.entity.DrawerBlockEntityController;
import net.momirealms.craftengine.bukkit.block.entity.SimpleStorageBlockEntityController;
import net.momirealms.craftengine.bukkit.item.BukkitItemDefinition;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.BlockPos;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/** Created only when CraftEngine is enabled, so its classes are never loaded otherwise. */
public final class CraftEngineItemResolver implements CustomItemResolver {

    private static final String PREFIX = ItemReference.CRAFT_ENGINE + ":";

    @Override
    public @Nullable String id(ItemStack item) {
        // Reads the ID stored on the item, not CraftEngine's loaded item list, which is empty while it starts up or
        // reloads: an item never changes destination during a /ce reload.
        Key id = CraftEngineItems.getCustomItemId(item);
        return id == null ? null : PREFIX + id.asString();
    }

    @Override
    public @Nullable Component name(String reference) {
        BukkitItemDefinition item = CraftEngineItems.byId(reference.substring(PREFIX.length()));
        // CraftEngine translates this name for each player as the message goes out, as it does in inventories.
        return item == null ? null : item.buildBukkitItem().effectiveName();
    }

    @Override
    public @Nullable ItemStack item(ItemReference.Custom reference, int amount) {
        // Null before CraftEngine has loaded its items, during /ce reload, and for an ID it doesn't know.
        BukkitItemDefinition item = CraftEngineItems.byId(reference.reference().substring(PREFIX.length()));
        if (item == null) {
            return null;
        }
        ItemStack built = item.buildBukkitItem();
        built.setAmount(amount);
        return built;
    }

    @Override
    public @Nullable String blockId(Block block) {
        ImmutableBlockState state = CraftEngineBlocks.getCustomBlockState(block);
        // Null for a vanilla block, and for a CraftEngine one while /ce reload has it unbound.
        return state == null || state.isEmpty() ? null : PREFIX + state.owner().value().id().asString();
    }

    @Override
    public @Nullable ItemStack blockItem(ItemReference.Custom block, int amount) {
        BlockDefinition definition = CraftEngineBlocks.byId(Key.of(block.reference().substring(PREFIX.length())));
        // The block's own ID unless its settings name another item; there may be no item with that ID.
        Key item = definition == null ? null : definition.defaultState().settings().itemId();
        return item == null ? null : item(new ItemReference.Custom(PREFIX + item.asString()), amount);
    }

    @Override
    public boolean isCustomBlock(Block block) {
        // True for any of CraftEngine's block slots, also while a /ce reload has them unbound.
        return CraftEngineBlocks.isCustomBlock(block);
    }

    @Override
    public boolean isContainer(Block block) {
        BlockEntity entity = blockEntity(block);
        return entity != null && (storage(entity) != null || drawer(entity) != null);
    }

    @Override
    public int store(Block block, ItemStack items) {
        BlockEntity entity = blockEntity(block);
        if (entity == null) {
            return Delivery.GONE;
        }
        // addItem leaves in items what didn't fit, so the amount offered is read first.
        int offered = items.getAmount();
        SimpleStorageBlockEntityController storage = storage(entity);
        if (storage != null) {
            int left = storage.inventory().addItem(items).values().stream().mapToInt(ItemStack::getAmount).sum();
            if (left < offered) {
                // CraftEngine saves a chunk's block entities only once told one changed. Its hoppers tell it and
                // Bukkit's addItem doesn't, so without this the items are gone when the chunk unloads.
                entity.world().blockEntityChanged(entity.pos());
            }
            return left;
        }
        DrawerBlockEntityController drawer = drawer(entity);
        if (drawer == null) {
            return Delivery.GONE;
        }
        // addItem would replace what a drawer holds; put takes only its item, as many as fit, and has it saved.
        return offered - drawer.put(BukkitAdaptor.adapt(items), offered);
    }

    // CraftEngine creates the block entity as its block is placed, and it outlives /ce reload, so FrameSort only reads
    // it. No other block reaches these internals, which can change with any CraftEngine update.
    private static @Nullable BlockEntity blockEntity(Block block) {
        if (!CraftEngineBlocks.isCustomBlock(block)) {
            return null;
        }
        return BukkitAdaptor.adapt(block.getWorld()).storageWorld()
                .getBlockEntityAtIfLoaded(new BlockPos(block.getX(), block.getY(), block.getZ()), false);
    }

    // Both leave out a block whose allow_input is false: the pack keeps automatic input out of it.
    private static @Nullable SimpleStorageBlockEntityController storage(BlockEntity entity) {
        SimpleStorageBlockBehavior behavior = entity.blockState().behavior().getFirst(SimpleStorageBlockBehavior.class);
        return behavior == null || !behavior.canPlaceItem
                ? null : entity.controller.get(SimpleStorageBlockEntityController.class, 0);
    }

    private static @Nullable DrawerBlockEntityController drawer(BlockEntity entity) {
        DrawerBlockEntityController drawer = entity.controller.get(DrawerBlockEntityController.class, 0);
        return drawer == null || !drawer.behavior.canPlaceItem ? null : drawer;
    }
}
