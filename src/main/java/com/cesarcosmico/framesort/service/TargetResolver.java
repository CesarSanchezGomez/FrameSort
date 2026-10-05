package com.cesarcosmico.framesort.service;

import com.cesarcosmico.framesort.api.TargetBindEvent;
import com.cesarcosmico.framesort.config.FrameSortSettings;
import com.cesarcosmico.framesort.model.BlockKey;
import com.cesarcosmico.framesort.model.MatchTier;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.BundleContents;
import io.papermc.paper.datacomponent.item.ItemContainerContents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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

/**
 * Finds the frames that accept an item around a source (a sorter or pad) and the priority each one matches at.
 * Results are cached per source and item until the world's targets change; callers still check each frame when
 * they use it, because a cached frame may have been emptied or removed since.
 */
public final class TargetResolver {

    /** A frame that accepts the item, the priority it accepts it at and what the frame held at the time. */
    public record Match(ItemFrame frame, int priority, ItemStack frameItem) {
    }

    private record CacheKey(BlockKey source, ItemStack item) {
    }

    private record Entry(long epoch, List<Match> matches) {
    }

    private static final int NO_MATCH = -1;
    // Bounds memory on servers with many distinct items; a full clear is cheap to rebuild from the index.
    private static final int MAX_CACHED = 4096;
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final Supplier<FrameSortSettings> settings;
    private final TargetIndex index;
    private final TagCatalog tags;
    private final Map<CacheKey, Entry> cache = new HashMap<>();

    public TargetResolver(Supplier<FrameSortSettings> settings, TargetIndex index, TagCatalog tags) {
        this.settings = settings;
        this.index = index;
        this.tags = tags;
    }

    public List<Match> matches(Block source, ItemStack item) {
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

    /** Drops a cached result that turned out to be stale. */
    public void forget(Block source, ItemStack item) {
        cache.remove(new CacheKey(FrameGeometry.key(source), item.asOne()));
    }

    public void clear() {
        cache.clear();
    }

    /** The best priority this frame item accepts {@code item} at, or {@code -1}. */
    public int priority(ItemStack frameItem, ItemStack item) {
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

    /** The existing tag a frame item stands for through its name, or {@code null}. */
    public TagCatalog.@Nullable TagView tag(ItemStack frameItem) {
        NamespacedKey key = tagOf(frameItem);
        return key == null ? null : tags.find(key);
    }

    /** The tag a frame item stands for through its name, or {@code null}. */
    public static @Nullable NamespacedKey tagOf(ItemStack frameItem) {
        Component name = frameItem.getData(DataComponentTypes.CUSTOM_NAME);
        return name == null ? null : TagCatalog.parseName(PLAIN.serialize(name));
    }

    /** What a shulker box or bundle item holds; empty for any other item. */
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
        return contents;
    }

    private List<Match> compute(Block source, BlockKey key, ItemStack item) {
        FrameSortSettings current = settings.get();
        List<Match> matches = new ArrayList<>();
        for (ItemFrame frame : index.near(key, current.delivery().maxDistance())) {
            ItemStack shown = frame.getItem();
            if (shown.isEmpty() || !current.targets().positions().contains(FrameGeometry.position(frame))) {
                continue;
            }
            Block attached = FrameGeometry.attachedBlock(frame);
            // Never send a source's items back into itself, and never treat a sorter's own frame as a target.
            if (attached.equals(source)
                    || (current.sorter().isActivator(shown) && attached.getType() == Material.DISPENSER)) {
                continue;
            }
            int priority = priority(shown, item);
            if (priority == NO_MATCH || !new TargetBindEvent(source, frame).callEvent()) {
                continue;
            }
            matches.add(new Match(frame, priority, shown.clone()));
        }
        return List.copyOf(matches);
    }

    private int direct(ItemStack target, ItemStack item, boolean nested) {
        if (target.isSimilar(item)) {
            return MatchTier.EXACT.priority(nested);
        }
        NamespacedKey tag = tagOf(target);
        if (tag != null && tags.contains(tag, item.getType())) {
            return MatchTier.TAG.priority(nested);
        }
        // A filled shulker box or bundle stands for its contents, not for its own material.
        if (target.getType() == item.getType() && contents(target).isEmpty()) {
            return MatchTier.SIMILAR.priority(nested);
        }
        return NO_MATCH;
    }
}
