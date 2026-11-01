package com.cesarcosmico.framesort.service;

import com.cesarcosmico.framesort.api.TargetBindEvent;
import com.cesarcosmico.framesort.config.FrameSortSettings;
import com.cesarcosmico.framesort.config.InspectSettings;
import com.cesarcosmico.framesort.item.FrameCodec;
import com.cesarcosmico.framesort.item.ItemTagCodec;
import com.cesarcosmico.framesort.item.PadCodec;
import com.cesarcosmico.framesort.model.BlockKey;
import com.cesarcosmico.framesort.model.ItemReference;
import com.cesarcosmico.framesort.model.MatchFacts;
import com.cesarcosmico.framesort.model.MatchTier;
import com.cesarcosmico.framesort.model.Ranking;
import com.cesarcosmico.framesort.model.TrustList;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.BundleContents;
import io.papermc.paper.datacomponent.item.ItemContainerContents;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ItemFrame;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

public final class TargetResolver {

    public enum Destination {
        CONTAINER,
        DROPPED,
        DESTROYED;

        public Color color(InspectSettings.Colors colors) {
            return switch (this) {
                case CONTAINER -> colors.container();
                case DROPPED -> colors.dropped();
                case DESTROYED -> colors.lava();
            };
        }
    }

    public record Target(ItemFrame frame, Destination destination) {
    }

    public record Level(List<ItemFrame> containers, List<ItemFrame> drops, List<ItemFrame> lava) {
        public Level {
            containers = List.copyOf(containers);
            drops = List.copyOf(drops);
            lava = List.copyOf(lava);
        }

        /** The nearest drop spot, else the nearest lava, or {@code null} when the level has neither. */
        public @Nullable Target end() {
            if (!drops.isEmpty()) {
                return new Target(drops.getFirst(), Destination.DROPPED);
            }
            return lava.isEmpty() ? null : new Target(lava.getFirst(), Destination.DESTROYED);
        }
    }

    /** The frames a delivery would use, best level first; {@code stale} if a cached frame changed. */
    public record Plan(List<Level> levels, boolean stale) {
        public Plan {
            levels = List.copyOf(levels);
        }
    }

    // Shared by the frames of an entry that hold the same item, so it keeps one copy of it.
    private record Shown(ItemStack item, int priority) {
    }

    private record Match(ItemFrame frame, Shown shown) {
    }

    private record Found(Match match, long distanceSquared) {
    }

    private record CacheKey(BlockKey source, @Nullable UUID owner, int range, ItemStack item) {
    }

    // Levels go best first, and each level nearest first.
    private record Entry(long epoch, List<List<Match>> levels, int cost) {
    }

    // Memory follows the matches an entry holds, not how many entries there are.
    private static final int MAX_CACHE_COST = 8_000_000;
    // What an entry weighs by itself, in matches: its key, its copies of the item and its lists.
    private static final int ENTRY_COST = 16;

    private final Supplier<FrameSortSettings> settings;
    private final TargetIndex index;
    private final TagCatalog tags;
    private final CustomItemResolver customItems;
    private final Supplier<TrustList> trust;
    private final Map<CacheKey, Entry> cache = new HashMap<>();
    private int cacheCost;

    public TargetResolver(Supplier<FrameSortSettings> settings, TargetIndex index, TagCatalog tags,
                          CustomItemResolver customItems, Supplier<TrustList> trust) {
        this.settings = settings;
        this.index = index;
        this.tags = tags;
        this.customItems = customItems;
        this.trust = trust;
    }

    public Plan plan(Block source, @Nullable UUID owner, int range, ItemStack item) {
        FrameSortSettings current = settings.get();
        List<Level> levels = new ArrayList<>();
        boolean stale = false;
        for (List<Match> tied : matches(source, owner, range, item)) {
            Map<Destination, List<ItemFrame>> split = new EnumMap<>(Destination.class);
            for (Match match : tied) {
                ItemFrame frame = match.frame();
                if (isStale(match)) {
                    stale = true;
                    continue;
                }
                Destination destination = destination(current, frame);
                if (destination == Destination.CONTAINER && !takes(FrameGeometry.attachedBlock(frame), item)) {
                    continue;
                }
                if (!wouldLoop(current, source, frame, destination)) {
                    split.computeIfAbsent(destination, key -> new ArrayList<>()).add(frame);
                }
            }
            if (!split.isEmpty()) {
                levels.add(new Level(split.getOrDefault(Destination.CONTAINER, List.of()),
                        split.getOrDefault(Destination.DROPPED, List.of()),
                        split.getOrDefault(Destination.DESTROYED, List.of())));
            }
        }
        return new Plan(levels, stale);
    }

    private static boolean isStale(Match match) {
        ItemFrame frame = match.frame();
        return !frame.isValid() || !FrameGeometry.attachedLoaded(frame)
                || !frame.getItem().equals(match.shown().item());
    }

    public Destination destination(ItemFrame frame) {
        return destination(settings.get(), frame);
    }

    private boolean takes(Block container, ItemStack item) {
        if (container.getType() == Material.COMPOSTER) {
            return item.getType().isCompostable();
        }
        return !(tags.isShulkerBox(container.getType()) && tags.isShulkerBox(item.getType()));
    }

    private Destination destination(FrameSortSettings current, ItemFrame frame) {
        if (TargetBlocks.isLava(frame)) {
            return Destination.DESTROYED;
        }
        return current.delivery().insertIntoContainers()
                && TargetBlocks.isContainer(FrameGeometry.attachedBlock(frame), customItems)
                ? Destination.CONTAINER : Destination.DROPPED;
    }

    public Component blockName(Block block) {
        String id = customItems.blockId(block);
        Component custom = id == null ? null : customItems.blockName(new ItemReference.Custom(id));
        return custom == null ? Component.translatable(block.getType()) : custom;
    }

    private List<List<Match>> matches(Block source, @Nullable UUID owner, int range, ItemStack item) {
        BlockKey key = FrameGeometry.key(source);
        CacheKey cacheKey = new CacheKey(key, owner, range, item.asOne());
        long epoch = index.epoch(key.world());
        Entry entry = cache.get(cacheKey);
        if (entry != null && entry.epoch() == epoch) {
            return entry.levels();
        }
        List<List<Match>> levels = compute(source, owner, key, range, cacheKey.item());
        store(cacheKey, new Entry(epoch, levels, ENTRY_COST + levels.stream().mapToInt(List::size).sum()));
        return levels;
    }

    private void store(CacheKey key, Entry entry) {
        forget(key);
        if (cacheCost + entry.cost() > MAX_CACHE_COST) {
            clear();
        }
        cache.put(key, entry);
        cacheCost += entry.cost();
    }

    private void forget(CacheKey key) {
        Entry removed = cache.remove(key);
        if (removed != null) {
            cacheCost -= removed.cost();
        }
    }

    public void forget(Block source, @Nullable UUID owner, int range, ItemStack item) {
        forget(new CacheKey(FrameGeometry.key(source), owner, range, item.asOne()));
    }

    public void clear() {
        cache.clear();
        cacheCost = 0;
    }

    public void forget(World world) {
        UUID id = world.getUID();
        cache.keySet().removeIf(key -> key.source().world().equals(id));
        cacheCost = cache.values().stream().mapToInt(Entry::cost).sum();
    }

    private int priority(ItemStack frameItem, ItemStack item, @Nullable String itemId) {
        List<ItemStack> contents = contents(frameItem);
        int direct = facts(frameItem, contents, item, itemId).priority(false);
        // Nothing inside the frame item can beat the exact item, so its contents aren't matched.
        List<Integer> nested = direct == MatchTier.EXACT.priority(false) ? List.of() : contents.stream()
                .map(content -> facts(content, contents(content), item, itemId).priority(true)).toList();
        return MatchTier.best(direct, nested, isDefaultTarget(frameItem));
    }

    public boolean isDefaultTarget(ItemStack frameItem) {
        ItemReference target = settings.get().delivery().defaultTargetItem();
        return target != null && target.matches(frameItem.getType(), customItems.id(frameItem));
    }

    /** The tag a tagged item stands for, or {@code null} when it has none or no tag has that name any more. */
    public TagCatalog.@Nullable TagView tag(ItemStack frameItem) {
        NamespacedKey key = ItemTagCodec.read(frameItem);
        return key == null ? null : tags.find(key);
    }

    /** {@code null} for a vanilla item. */
    public @Nullable String customId(ItemStack item) {
        return customItems.id(item);
    }

    public static List<ItemStack> contents(ItemStack item) {
        List<ItemStack> contents = new ArrayList<>();
        ItemContainerContents container = item.getData(DataComponentTypes.CONTAINER);
        if (container != null) {
            contents.addAll(container.contents());
        }
        BundleContents bundle = item.getData(DataComponentTypes.BUNDLE_CONTENTS);
        if (bundle != null) {
            contents.addAll(bundle.contents());
        }
        contents.removeIf(ItemStack::isEmpty);
        return List.copyOf(contents);
    }

    /** Whether {@code source} may send to {@code frame} at all, whatever the item; the caller checks the distance. */
    public boolean reaches(Block source, @Nullable UUID owner, ItemFrame frame) {
        FrameSortSettings current = settings.get();
        ItemStack shown = frame.getItem();
        return !shown.isEmpty() && eligible(current, source, owner, frame, shown)
                && !wouldLoop(current, source, frame, destination(current, frame))
                && new TargetBindEvent(source, frame).callEvent();
    }

    /** The frames in range that {@code source} may send to, whatever the item. */
    public List<ItemFrame> targets(Block source, @Nullable UUID owner, int range) {
        List<ItemFrame> found = new ArrayList<>();
        for (TargetIndex.Near near : index.near(FrameGeometry.key(source), range)) {
            if (reaches(source, owner, near.frame())) {
                found.add(near.frame());
            }
        }
        return List.copyOf(found);
    }

    private List<List<Match>> compute(Block source, @Nullable UUID owner, BlockKey key, int range, ItemStack item) {
        FrameSortSettings current = settings.get();
        String itemId = customItems.id(item);
        Map<ItemStack, Shown> seen = new HashMap<>();
        List<Found> found = new ArrayList<>();
        for (TargetIndex.Near near : index.near(key, range)) {
            ItemFrame frame = near.frame();
            ItemStack inFrame = frame.getItem();
            if (inFrame.isEmpty()) {
                continue;
            }
            Shown shown = seen.computeIfAbsent(inFrame, stack -> new Shown(stack, priority(stack, item, itemId)));
            // The event goes last: other plugins' handlers cost more than matching the item.
            if (shown.priority() != MatchTier.NO_MATCH && eligible(current, source, owner, frame, inFrame)
                    && new TargetBindEvent(source, frame).callEvent()) {
                found.add(new Found(new Match(frame, shown), near.distanceSquared()));
            }
        }
        found.sort(Comparator.comparingLong(Found::distanceSquared));
        Ranking<Match> ranking = new Ranking<>();
        found.forEach(match -> ranking.add(match.match().shown().priority(), match.match()));
        return ranking.levels();
    }

    private boolean eligible(FrameSortSettings current, Block source, @Nullable UUID owner, ItemFrame frame,
                             ItemStack shown) {
        if (!FrameGeometry.attachedLoaded(frame)) {
            return false;
        }
        Block attached = FrameGeometry.attachedBlock(frame);
        if (attached.equals(source) || SorterFrames.makesSorter(current.sorter(), frame, shown, customItems)) {
            return false;
        }
        return !current.ownership() || trust.get().allows(owner, FrameCodec.owner(frame));
    }

    private boolean wouldLoop(FrameSortSettings current, Block source, ItemFrame frame, Destination destination) {
        if (isSorter(current, FrameGeometry.attachedBlock(frame)) && isSorter(current, source)) {
            return true;
        }
        return destination == Destination.DROPPED && landsOnPad(FrameGeometry.dropPoint(frame).getBlock());
    }

    private static boolean landsOnPad(Block drop) {
        Block block = drop;
        while (block.isPassable() && block.getY() > block.getWorld().getMinHeight()) {
            block = block.getRelative(BlockFace.DOWN);
        }
        // A carpet or slab on the pad still leaves the items in its pickup space.
        return hasPadEntry(block) || hasPadEntry(block.getRelative(BlockFace.DOWN));
    }

    // Read from the chunk: PadService delivers through this class, so it can't be asked.
    private static boolean hasPadEntry(Block block) {
        return PadCodec.read(block.getChunk()).containsKey(FrameGeometry.key(block));
    }

    // Read from the world: SorterService delivers through this class, so it can't be asked.
    private boolean isSorter(FrameSortSettings current, Block block) {
        if (!SorterFrames.isSorterBlock(block)) {
            return false;
        }
        for (ItemFrame frame : block.getWorld().getNearbyEntitiesByType(ItemFrame.class,
                block.getLocation().toCenterLocation(), 1)) {
            if (FrameGeometry.attachedBlock(frame).equals(block)
                    && SorterFrames.holdsActivator(current.sorter(), frame, frame.getItem(), customItems)) {
                return true;
            }
        }
        return false;
    }

    private MatchFacts facts(ItemStack target, List<ItemStack> targetContents, ItemStack item,
                             @Nullable String itemId) {
        boolean tagged = ItemTagCodec.read(target) != null;
        TagCatalog.TagView tag = tagged ? tag(target) : null;
        boolean inTag = tag != null && (itemId == null ? tag.lookup().contains(item.getType())
                : tag.customItems().contains(itemId));
        return new MatchFacts(tagged, inTag, !targetContents.isEmpty(), target.isSimilar(item),
                sameKind(target, item, itemId));
    }

    private boolean sameKind(ItemStack target, ItemStack item, @Nullable String itemId) {
        String targetId = customItems.id(target);
        return targetId == null ? itemId == null && target.getType() == item.getType() : targetId.equals(itemId);
    }
}
