package com.cesarcosmico.framesort.service;

import com.cesarcosmico.framesort.api.TargetBindEvent;
import com.cesarcosmico.framesort.config.FrameSortSettings;
import com.cesarcosmico.framesort.config.InspectSettings;
import com.cesarcosmico.framesort.item.FrameCodec;
import com.cesarcosmico.framesort.item.ItemTagCodec;
import com.cesarcosmico.framesort.item.PadCodec;
import com.cesarcosmico.framesort.model.BlockKey;
import com.cesarcosmico.framesort.model.ItemReference;
import com.cesarcosmico.framesort.model.MatchTier;
import com.cesarcosmico.framesort.model.Ranking;
import com.cesarcosmico.framesort.model.TargetRegistration;
import com.cesarcosmico.framesort.model.TrustList;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.BundleContents;
import io.papermc.paper.datacomponent.item.ItemContainerContents;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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

/** Cached until the world's targets change; callers recheck each frame, which may have changed since. */
public final class TargetResolver {

    /** Where a delivery leaves the items sent to a frame. */
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

    /** Frames tied at one priority, split by where the items end up, each list nearest to the source first. */
    public record Level(List<ItemFrame> containers, List<ItemFrame> drops, List<ItemFrame> lava) {
        public Level {
            containers = List.copyOf(containers);
            drops = List.copyOf(drops);
            lava = List.copyOf(lava);
        }

        /**
         * Takes whatever the containers leave: the nearest drop spot, else the nearest lava. The other drop spots and
         * lava of the level never get anything; {@code null} when it has neither.
         */
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

    private record Match(ItemFrame frame, int priority, ItemStack frameItem) {
    }

    // The owner is part of the key: a pad rebuilt on the same block by someone else must not reuse its matches.
    private record CacheKey(BlockKey source, @Nullable UUID owner, ItemStack item) {
    }

    private record Entry(long epoch, List<Match> matches) {
    }

    private static final int NO_MATCH = -1;
    // Bounds memory on servers with many distinct items; a full clear is cheap to rebuild from the index.
    private static final int MAX_CACHED = 4096;

    private final Supplier<FrameSortSettings> settings;
    private final TargetIndex index;
    private final TagCatalog tags;
    private final CustomItemResolver customItems;
    private final Supplier<TrustList> trust;
    private final Map<CacheKey, Entry> cache = new HashMap<>();

    public TargetResolver(Supplier<FrameSortSettings> settings, TargetIndex index, TagCatalog tags,
                          CustomItemResolver customItems, Supplier<TrustList> trust) {
        this.settings = settings;
        this.index = index;
        this.tags = tags;
        this.customItems = customItems;
        this.trust = trust;
    }

    /**
     * Deliveries and inspections both plan here, so an inspection always shows what a delivery would do.
     * {@code owner} is the source's owner, or {@code null} when nobody owns it.
     */
    public Plan plan(Block source, @Nullable UUID owner, ItemStack item) {
        FrameSortSettings current = settings.get();
        Ranking<ItemFrame> ranking = new Ranking<>();
        boolean stale = false;
        for (Match match : matches(source, owner, item)) {
            ItemFrame frame = match.frame();
            if (!frame.isValid() || !FrameGeometry.attachedLoaded(frame)
                    || !frame.getItem().equals(match.frameItem())) {
                stale = true;
                continue;
            }
            if (FrameGeometry.attachedBlock(frame).getType() == Material.COMPOSTER && !item.getType().isCompostable()) {
                continue;
            }
            if (wouldLoop(current, source, frame)) {
                continue;
            }
            ranking.add(match.priority(), frame);
        }
        // Nearest first within a level, so filling is predictable and an inspection lists the real order.
        BlockKey origin = FrameGeometry.key(source);
        Comparator<ItemFrame> nearest = Comparator.comparingLong(frame -> FrameGeometry.distanceSquared(frame, origin));
        List<Level> levels = new ArrayList<>();
        for (List<ItemFrame> tied : ranking.levels()) {
            Map<Destination, List<ItemFrame>> split = new EnumMap<>(Destination.class);
            for (ItemFrame frame : tied.stream().sorted(nearest).toList()) {
                split.computeIfAbsent(destination(current, frame), key -> new ArrayList<>()).add(frame);
            }
            levels.add(new Level(split.getOrDefault(Destination.CONTAINER, List.of()),
                    split.getOrDefault(Destination.DROPPED, List.of()),
                    split.getOrDefault(Destination.DESTROYED, List.of())));
        }
        return new Plan(levels, stale);
    }

    public Destination destination(ItemFrame frame) {
        return destination(settings.get(), frame);
    }

    private static Destination destination(FrameSortSettings current, ItemFrame frame) {
        if (FrameGeometry.isLava(frame)) {
            return Destination.DESTROYED;
        }
        return current.delivery().insertIntoContainers()
                && FrameGeometry.isContainer(FrameGeometry.attachedBlock(frame))
                ? Destination.CONTAINER : Destination.DROPPED;
    }

    private List<Match> matches(Block source, @Nullable UUID owner, ItemStack item) {
        BlockKey key = FrameGeometry.key(source);
        CacheKey cacheKey = new CacheKey(key, owner, item.asOne());
        long epoch = index.epoch(key.world());
        Entry entry = cache.get(cacheKey);
        if (entry != null && entry.epoch() == epoch) {
            return entry.matches();
        }
        List<Match> matches = compute(source, owner, key, cacheKey.item());
        if (cache.size() >= MAX_CACHED) {
            cache.clear();
        }
        cache.put(cacheKey, new Entry(epoch, matches));
        return matches;
    }

    public void forget(Block source, @Nullable UUID owner, ItemStack item) {
        cache.remove(new CacheKey(FrameGeometry.key(source), owner, item.asOne()));
    }

    public void clear() {
        cache.clear();
    }

    /** The best priority this frame item accepts {@code item} at, or {@code -1}. */
    private int priority(ItemStack frameItem, ItemStack item, @Nullable String itemId) {
        int best = direct(frameItem, item, itemId, false);
        if (best != MatchTier.EXACT.priority(false)) {
            for (ItemStack content : contents(frameItem)) {
                int nested = direct(content, item, itemId, true);
                if (nested != NO_MATCH && (best == NO_MATCH || nested < best)) {
                    best = nested;
                }
            }
        }
        if (best == NO_MATCH && isDefaultTarget(frameItem)) {
            best = MatchTier.DEFAULT_PRIORITY;
        }
        return best;
    }

    /** Whether a frame holding {@code frameItem} takes whatever matches no other frame. */
    public boolean isDefaultTarget(ItemStack frameItem) {
        ItemReference target = settings.get().delivery().defaultTargetItem();
        return target != null && target.matches(frameItem.getType(), customItems.id(frameItem));
    }

    /** The tag a tagged item stands for, or {@code null} when the item has none. */
    public TagCatalog.@Nullable TagView tag(ItemStack frameItem) {
        NamespacedKey key = ItemTagCodec.read(frameItem);
        return key == null ? null : tags.find(key);
    }

    /** The reference to {@code item} as tags.yml writes it, or {@code null} for a vanilla item. */
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
        return eligible(current, source, owner, frame) && !wouldLoop(current, source, frame)
                && new TargetBindEvent(source, frame).callEvent();
    }

    /** The frames in range that {@code source} may send to, whatever the item. */
    public List<ItemFrame> targets(Block source, @Nullable UUID owner) {
        List<ItemFrame> found = new ArrayList<>();
        for (ItemFrame frame : index.near(FrameGeometry.key(source), settings.get().delivery().maxDistance())) {
            if (reaches(source, owner, frame)) {
                found.add(frame);
            }
        }
        return List.copyOf(found);
    }

    private List<Match> compute(Block source, @Nullable UUID owner, BlockKey key, ItemStack item) {
        FrameSortSettings current = settings.get();
        String itemId = customItems.id(item);
        List<Match> matches = new ArrayList<>();
        for (ItemFrame frame : index.near(key, current.delivery().maxDistance())) {
            if (!eligible(current, source, owner, frame)) {
                continue;
            }
            ItemStack shown = frame.getItem();
            int priority = priority(shown, item, itemId);
            // The event goes last: other plugins' handlers cost more than matching the item.
            if (priority == NO_MATCH || !new TargetBindEvent(source, frame).callEvent()) {
                continue;
            }
            matches.add(new Match(frame, priority, shown.clone()));
        }
        return List.copyOf(matches);
    }

    private boolean eligible(FrameSortSettings current, Block source, @Nullable UUID owner, ItemFrame frame) {
        ItemStack shown = frame.getItem();
        if (shown.isEmpty() || !FrameGeometry.attachedLoaded(frame)) {
            return false;
        }
        Block attached = FrameGeometry.attachedBlock(frame);
        // Unmarked frames only count on containers, so a decorative frame never becomes a drop spot or trash can.
        if (current.targets().registration() == TargetRegistration.AUTOMATIC && !FrameGeometry.isContainer(attached)) {
            return false;
        }
        // Never send a source's items back into itself, and never treat a sorter's own frame as a target.
        if (attached.equals(source) || attached.getType() == Material.DISPENSER
                && current.sorter().isActivatorFrame(frame, customItems::id)) {
            return false;
        }
        // Anyone can hang a frame near a sorter: it takes the owner's items only if the owner trusts its owner.
        return !current.ownership() || trust.get().allows(owner, FrameCodec.owner(frame));
    }

    // Never cached: sorters come and go, and the blocks below a drop spot change, without touching any target frame.
    private boolean wouldLoop(FrameSortSettings current, Block source, ItemFrame frame) {
        if (isSorter(current, FrameGeometry.attachedBlock(frame)) && isSorter(current, source)) {
            return true;
        }
        return destination(current, frame) == Destination.DROPPED
                && landsOnPad(FrameGeometry.dropPoint(frame).getBlock());
    }

    private static boolean landsOnPad(Block drop) {
        Block block = drop;
        while (block.isPassable() && block.getY() > block.getWorld().getMinHeight()) {
            block = block.getRelative(BlockFace.DOWN);
        }
        // A carpet or slab on the pad still leaves the items in its pickup space.
        return isPadTop(block) || isPadTop(block.getRelative(BlockFace.DOWN));
    }

    // Read from the chunk: PadService delivers through this class, so it can't be asked.
    private static boolean isPadTop(Block block) {
        return PadCodec.read(block.getChunk()).containsKey(FrameGeometry.key(block));
    }

    // Read from the world: SorterService delivers through this class, so it can't be asked.
    private boolean isSorter(FrameSortSettings current, Block block) {
        if (block.getType() != Material.DISPENSER) {
            return false;
        }
        for (ItemFrame frame : block.getWorld().getNearbyEntitiesByType(ItemFrame.class,
                block.getLocation().toCenterLocation(), 1)) {
            if (FrameGeometry.attachedBlock(frame).equals(block)
                    && current.sorter().isActivatorFrame(frame, customItems::id)) {
                return true;
            }
        }
        return false;
    }

    private int direct(ItemStack target, ItemStack item, @Nullable String itemId, boolean nested) {
        // A tagged item stands only for its tag, never for itself or its material.
        if (ItemTagCodec.read(target) != null) {
            TagCatalog.TagView tag = tag(target);
            // A custom item is only a vanilla material underneath, so only a tags.yml tag that lists it stands for it.
            return tag != null && (itemId == null ? tag.lookup().contains(item.getType())
                    : tag.customItems().contains(itemId))
                    ? MatchTier.TAG.priority(nested) : NO_MATCH;
        }
        // A filled shulker box or bundle in the frame stands only for what it holds; inside it, only for itself.
        boolean filled = !contents(target).isEmpty();
        if (filled && !nested) {
            return NO_MATCH;
        }
        if (target.isSimilar(item)) {
            return MatchTier.EXACT.priority(nested);
        }
        return !filled && sameKind(target, item, itemId) ? MatchTier.SIMILAR.priority(nested) : NO_MATCH;
    }

    // Many custom items share one vanilla material, so a custom item's kind is its ID, never its material.
    private boolean sameKind(ItemStack target, ItemStack item, @Nullable String itemId) {
        String targetId = customItems.id(target);
        return targetId == null ? itemId == null && target.getType() == item.getType() : targetId.equals(itemId);
    }
}
