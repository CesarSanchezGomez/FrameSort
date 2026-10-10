package com.cesarcosmico.framesort.service;

import com.cesarcosmico.framesort.api.TargetBindEvent;
import com.cesarcosmico.framesort.config.FrameSortSettings;
import com.cesarcosmico.framesort.item.ItemTagCodec;
import com.cesarcosmico.framesort.model.BlockKey;
import com.cesarcosmico.framesort.model.MatchTier;
import com.cesarcosmico.framesort.model.TargetSet;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.BundleContents;
import io.papermc.paper.datacomponent.item.ItemContainerContents;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.ItemFrame;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Cached until the world's targets change; callers recheck each frame, which may have changed since. */
public final class TargetResolver {

    /** The frames a delivery would use, split by where the items end up; {@code stale} if a cached frame changed. */
    public record Plan(List<ItemFrame> containers, List<ItemFrame> drops, boolean stale) {
        public Plan {
            containers = List.copyOf(containers);
            drops = List.copyOf(drops);
        }
    }

    private record Match(ItemFrame frame, int priority, ItemStack frameItem) {
    }

    private record CacheKey(BlockKey source, ItemStack item) {
    }

    private record Entry(long epoch, List<Match> matches) {
    }

    private static final int NO_MATCH = -1;
    // Bounds memory on servers with many distinct items; a full clear is cheap to rebuild from the index.
    private static final int MAX_CACHED = 4096;

    private final Supplier<FrameSortSettings> settings;
    private final TargetIndex index;
    private final TagCatalog tags;
    private final Map<CacheKey, Entry> cache = new HashMap<>();

    public TargetResolver(Supplier<FrameSortSettings> settings, TargetIndex index, TagCatalog tags) {
        this.settings = settings;
        this.index = index;
        this.tags = tags;
    }

    // Deliveries and inspections both plan here, so an inspection always shows what a delivery would do.
    public Plan plan(Block source, ItemStack item) {
        boolean insert = settings.get().delivery().insertIntoContainers();
        TargetSet<ItemFrame> containers = new TargetSet<>();
        TargetSet<ItemFrame> drops = new TargetSet<>();
        boolean stale = false;
        for (Match match : matches(source, item)) {
            ItemFrame frame = match.frame();
            if (!frame.isValid() || !FrameGeometry.attachedLoaded(frame)
                    || !frame.getItem().equals(match.frameItem())) {
                stale = true;
                continue;
            }
            Block attached = FrameGeometry.attachedBlock(frame);
            if (attached.getType() == Material.COMPOSTER && !item.getType().isCompostable()) {
                continue;
            }
            if (insert && FrameGeometry.isContainer(attached)) {
                containers.add(match.priority(), frame);
            } else {
                drops.add(match.priority(), frame);
            }
        }
        return new Plan(containers.targets(), drops.targets(), stale);
    }

    private List<Match> matches(Block source, ItemStack item) {
        BlockKey key = FrameGeometry.key(source);
        CacheKey cacheKey = new CacheKey(key, item.asOne());
        long epoch = index.epoch(key.world());
        Entry entry = cache.get(cacheKey);
        if (entry != null && entry.epoch() == epoch) {
            return entry.matches();
        }
        List<Match> matches = compute(source, key, cacheKey.item());
        if (cache.size() >= MAX_CACHED) {
            cache.clear();
        }
        cache.put(cacheKey, new Entry(epoch, matches));
        return matches;
    }

    public void forget(Block source, ItemStack item) {
        cache.remove(new CacheKey(FrameGeometry.key(source), item.asOne()));
    }

    public void clear() {
        cache.clear();
    }

    /** The best priority this frame item accepts {@code item} at, or {@code -1}. */
    private int priority(ItemStack frameItem, ItemStack item) {
        int best = direct(frameItem, item, false);
        if (best != MatchTier.EXACT.priority(false)) {
            for (ItemStack content : contents(frameItem)) {
                int nested = direct(content, item, true);
                if (nested != NO_MATCH && (best == NO_MATCH || nested < best)) {
                    best = nested;
                }
            }
        }
        Material fallback = settings.get().delivery().defaultTargetItem();
        if (best == NO_MATCH && fallback != null && frameItem.getType() == fallback) {
            best = MatchTier.DEFAULT_PRIORITY;
        }
        return best;
    }

    /** The tag a tagged item stands for, or {@code null} when the item has none. */
    public TagCatalog.@Nullable TagView tag(ItemStack frameItem) {
        NamespacedKey key = ItemTagCodec.read(frameItem);
        return key == null ? null : tags.find(key);
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
    public boolean reaches(Block source, ItemFrame frame) {
        return eligible(settings.get(), source, frame) && new TargetBindEvent(source, frame).callEvent();
    }

    /** The frames in range that {@code source} may send to, whatever the item. */
    public List<ItemFrame> targets(Block source) {
        List<ItemFrame> found = new ArrayList<>();
        for (ItemFrame frame : index.near(FrameGeometry.key(source), settings.get().delivery().maxDistance())) {
            if (reaches(source, frame)) {
                found.add(frame);
            }
        }
        return List.copyOf(found);
    }

    private List<Match> compute(Block source, BlockKey key, ItemStack item) {
        FrameSortSettings current = settings.get();
        List<Match> matches = new ArrayList<>();
        for (ItemFrame frame : index.near(key, current.delivery().maxDistance())) {
            if (!eligible(current, source, frame)) {
                continue;
            }
            ItemStack shown = frame.getItem();
            int priority = priority(shown, item);
            // The event goes last: other plugins' handlers cost more than matching the item.
            if (priority == NO_MATCH || !new TargetBindEvent(source, frame).callEvent()) {
                continue;
            }
            matches.add(new Match(frame, priority, shown.clone()));
        }
        return List.copyOf(matches);
    }

    private static boolean eligible(FrameSortSettings current, Block source, ItemFrame frame) {
        ItemStack shown = frame.getItem();
        if (shown.isEmpty() || !FrameGeometry.attachedLoaded(frame)
                || !current.targets().allows(FrameGeometry.positions(frame))) {
            return false;
        }
        Block attached = FrameGeometry.attachedBlock(frame);
        // Never send a source's items back into itself, and never treat a sorter's own frame as a target.
        return !attached.equals(source)
                && !(current.sorter().isActivator(shown) && attached.getType() == Material.DISPENSER);
    }

    private int direct(ItemStack target, ItemStack item, boolean nested) {
        // A tagged item stands only for its tag, never for itself or its material.
        if (ItemTagCodec.read(target) != null) {
            TagCatalog.TagView tag = tag(target);
            return tag != null && tag.lookup().contains(item.getType()) ? MatchTier.TAG.priority(nested) : NO_MATCH;
        }
        if (target.isSimilar(item)) {
            return MatchTier.EXACT.priority(nested);
        }
        // A filled shulker box or bundle stands for its contents, not for its own material.
        if (target.getType() == item.getType() && contents(target).isEmpty()) {
            return MatchTier.SIMILAR.priority(nested);
        }
        return NO_MATCH;
    }
}
