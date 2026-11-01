package com.cesarcosmico.framesort.service;

import com.cesarcosmico.framesort.api.TargetBindEvent;
import com.cesarcosmico.framesort.config.FrameSortSettings;
import com.cesarcosmico.framesort.config.InspectSettings;
import com.cesarcosmico.framesort.item.FrameCodec;
import com.cesarcosmico.framesort.item.ItemTagCodec;
import com.cesarcosmico.framesort.item.PadCodec;
import com.cesarcosmico.framesort.model.BlockKey;
import com.cesarcosmico.framesort.model.Delivery;
import com.cesarcosmico.framesort.model.Delivery.Route;
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
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import java.util.function.ToIntBiFunction;

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
            ItemFrame end = Delivery.end(drops.isEmpty() ? null : drops.getFirst(),
                    lava.isEmpty() ? null : lava.getFirst());
            return end == null ? null : new Target(end, drops.isEmpty() ? Destination.DESTROYED : Destination.DROPPED);
        }
    }

    /** {@code end} is what ended a level and took {@code remaining}, or {@code null} if they stayed. */
    public record Filled(int remaining, @Nullable Target end) {
    }

    private record Shown(ItemStack item, int priority) {
    }

    private record Match(ItemFrame frame, Shown shown) {
    }

    private record Candidate(Match match, long distanceSquared) {
    }

    private record CacheKey(BlockKey source, @Nullable UUID owner, int range, ItemStack item) {
    }

    private record Entry(long epoch, List<List<Match>> levels, int weight) {
    }

    // A match takes 28 bytes, so this is about 220 MB at most, and only far beyond 200 sorters and 400 pads.
    private static final int MAX_CACHED_MATCHES = 8_000_000;
    // An entry's key, item copies and lists take about 430 bytes.
    private static final int ENTRY_WEIGHT_IN_MATCHES = 16;

    private final Supplier<FrameSortSettings> settings;
    private final TargetIndex index;
    private final TagCatalog tags;
    private final CustomItemResolver customItems;
    private final Supplier<TrustList> trust;
    private final Map<CacheKey, Entry> cache = new HashMap<>();
    private int cachedWeight;

    public TargetResolver(Supplier<FrameSortSettings> settings, TargetIndex index, TagCatalog tags,
                          CustomItemResolver customItems, Supplier<TrustList> trust) {
        this.settings = settings;
        this.index = index;
        this.tags = tags;
        this.customItems = customItems;
        this.trust = trust;
    }

    /** The frames a delivery could use, best level first, as {@link #deliver} would go through them. */
    public List<Level> plan(Block source, @Nullable UUID owner, int range, ItemStack item) {
        FrameSortSettings current = settings.get();
        CacheKey key = cacheKey(source, owner, range, item);
        List<Level> levels = new ArrayList<>();
        for (List<Match> tied : matches(key, source)) {
            Map<Route, List<ItemFrame>> split = new EnumMap<>(Route.class);
            for (Match match : tied) {
                split.computeIfAbsent(route(current, key, source, match), route -> new ArrayList<>())
                        .add(match.frame());
            }
            split.remove(Route.SKIP);
            if (!split.isEmpty()) {
                levels.add(new Level(split.getOrDefault(Route.FILL, List.of()),
                        split.getOrDefault(Route.DROP, List.of()),
                        split.getOrDefault(Route.DESTROY, List.of())));
            }
        }
        return List.copyOf(levels);
    }

    /**
     * Offers {@code item} to the frames {@link #plan} lists, stopping as soon as nothing is left. {@code offer} and
     * {@code commit} are those of {@link Delivery#deliver}.
     */
    public Filled deliver(Block source, @Nullable UUID owner, int range, ItemStack item,
                          ToIntBiFunction<ItemFrame, Integer> offer, IntConsumer commit) {
        FrameSortSettings current = settings.get();
        CacheKey key = cacheKey(source, owner, range, item);
        Delivery.Outcome<Match> outcome = Delivery.deliver(item.getAmount(), matches(key, source),
                match -> route(current, key, source, match),
                (match, offered) -> offer.applyAsInt(match.frame(), offered), commit);
        Match end = outcome.end();
        Destination ending = outcome.endDestroys() ? Destination.DESTROYED : Destination.DROPPED;
        return new Filled(outcome.remaining(), end == null ? null : new Target(end.frame(), ending));
    }

    private Route route(FrameSortSettings current, CacheKey key, Block source, Match match) {
        ItemFrame frame = match.frame();
        Block attached = FrameGeometry.attachedBlock(frame);
        if (isStale(match, attached)) {
            forget(key);
            return Route.SKIP;
        }
        Destination destination = destination(current, attached);
        boolean refused = destination == Destination.CONTAINER && !takes(attached, key.item());
        if (refused || wouldLoop(current, source, frame, attached, destination)) {
            return Route.SKIP;
        }
        return switch (destination) {
            case CONTAINER -> Route.FILL;
            case DROPPED -> Route.DROP;
            case DESTROYED -> Route.DESTROY;
        };
    }

    private static boolean isStale(Match match, Block attached) {
        ItemFrame frame = match.frame();
        return !frame.isValid() || !FrameGeometry.isLoaded(attached)
                || !frame.getItem().equals(match.shown().item());
    }

    public Destination destination(ItemFrame frame) {
        return destination(settings.get(), FrameGeometry.attachedBlock(frame));
    }

    private boolean takes(Block container, ItemStack item) {
        if (container.getType() == Material.COMPOSTER) {
            return item.getType().isCompostable();
        }
        return !(tags.isShulkerBox(container.getType()) && tags.isShulkerBox(item.getType()));
    }

    private Destination destination(FrameSortSettings current, Block attached) {
        if (TargetBlocks.isLava(attached)) {
            return Destination.DESTROYED;
        }
        return current.delivery().insertIntoContainers() && TargetBlocks.isContainer(attached, customItems)
                ? Destination.CONTAINER : Destination.DROPPED;
    }

    public Component blockName(Block block) {
        String id = customItems.blockId(block);
        Component custom = id == null ? null : customItems.blockName(new ItemReference.Custom(id));
        return custom == null ? Component.translatable(block.getType()) : custom;
    }

    private static CacheKey cacheKey(Block source, @Nullable UUID owner, int range, ItemStack item) {
        return new CacheKey(FrameGeometry.key(source), owner, range, item.asOne());
    }

    private List<List<Match>> matches(CacheKey key, Block source) {
        long epoch = index.epoch(key.source().world());
        Entry entry = cache.get(key);
        if (entry != null && entry.epoch() == epoch) {
            return entry.levels();
        }
        List<List<Match>> levels = compute(key, source);
        store(key, new Entry(epoch, levels, ENTRY_WEIGHT_IN_MATCHES + levels.stream().mapToInt(List::size).sum()));
        return levels;
    }

    private void store(CacheKey key, Entry entry) {
        forget(key);
        if (cachedWeight + entry.weight() > MAX_CACHED_MATCHES) {
            clear();
        }
        cache.put(key, entry);
        cachedWeight += entry.weight();
    }

    private void forget(CacheKey key) {
        Entry removed = cache.remove(key);
        if (removed != null) {
            cachedWeight -= removed.weight();
        }
    }

    public void clear() {
        cache.clear();
        cachedWeight = 0;
    }

    public void forget(World world) {
        UUID id = world.getUID();
        cache.keySet().removeIf(key -> key.source().world().equals(id));
        cachedWeight = cache.values().stream().mapToInt(Entry::weight).sum();
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
        Block attached = FrameGeometry.attachedBlock(frame);
        return !shown.isEmpty() && eligible(current, source, owner, frame, attached, shown)
                && !wouldLoop(current, source, frame, attached, destination(current, attached))
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

    private List<List<Match>> compute(CacheKey key, Block source) {
        FrameSortSettings current = settings.get();
        UUID owner = key.owner();
        ItemStack item = key.item();
        String itemId = customItems.id(item);
        Map<ItemStack, Shown> seen = new HashMap<>();
        List<Candidate> found = new ArrayList<>();
        for (TargetIndex.Near near : index.near(key.source(), key.range())) {
            ItemFrame frame = near.frame();
            ItemStack inFrame = frame.getItem();
            if (inFrame.isEmpty()) {
                continue;
            }
            Shown shown = seen.computeIfAbsent(inFrame, stack -> new Shown(stack, priority(stack, item, itemId)));
            // The event goes last: other plugins' handlers cost more than matching the item.
            if (shown.priority() != MatchTier.NO_MATCH
                    && eligible(current, source, owner, frame, FrameGeometry.attachedBlock(frame), inFrame)
                    && new TargetBindEvent(source, frame).callEvent()) {
                found.add(new Candidate(new Match(frame, shown), near.distanceSquared()));
            }
        }
        found.sort(Comparator.comparingLong(Candidate::distanceSquared));
        Ranking<Match> ranking = new Ranking<>();
        found.forEach(candidate -> ranking.add(candidate.match().shown().priority(), candidate.match()));
        return ranking.levels();
    }

    private boolean eligible(FrameSortSettings current, Block source, @Nullable UUID owner, ItemFrame frame,
                             Block attached, ItemStack shown) {
        if (!FrameGeometry.isLoaded(attached) || attached.equals(source)) {
            return false;
        }
        if (SorterFrames.isSorterBlock(attached)
                && SorterFrames.holdsActivator(current.sorter(), frame, shown, customItems)) {
            return false;
        }
        return !current.ownership() || trust.get().allows(owner, FrameCodec.owner(frame));
    }

    private boolean wouldLoop(FrameSortSettings current, Block source, ItemFrame frame, Block attached,
                              Destination destination) {
        if (isSorter(current, attached) && isSorter(current, source)) {
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
