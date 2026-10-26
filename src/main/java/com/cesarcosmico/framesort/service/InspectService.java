package com.cesarcosmico.framesort.service;

import com.cesarcosmico.framesort.config.CommandsConfig;
import com.cesarcosmico.framesort.config.FrameSortSettings;
import com.cesarcosmico.framesort.config.InspectSettings;
import com.cesarcosmico.framesort.config.PadType;
import com.cesarcosmico.framesort.item.FrameCodec;
import com.cesarcosmico.framesort.item.ItemTagCodec;
import com.cesarcosmico.framesort.model.BlockKey;
import com.cesarcosmico.framesort.service.TargetResolver.Destination;
import com.cesarcosmico.framesort.service.TargetResolver.Target;
import com.cesarcosmico.framesort.text.ChatPager;
import com.cesarcosmico.framesort.text.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;

public final class InspectService {

    public static final String INSPECT_PERMISSION = "framesort.inspect";
    private static final ClickCallback.Options PAGE_BUTTON = ClickCallback.Options.builder()
            .uses(ClickCallback.UNLIMITED_USES).build();

    // How a listing was opened: its page buttons check the same again.
    private enum Opened {
        GESTURE,
        WHERE
    }

    private record Source(Block block, boolean pad, @Nullable UUID owner, int range) {
    }

    private final Supplier<FrameSortSettings> settings;
    private final Supplier<Messages> messages;
    private final TargetIndex index;
    private final TargetResolver resolver;
    private final TrustService trust;
    private final SorterService sorters;
    private final PadService pads;
    private final CommandsConfig commands;
    private final HighlightService highlights;
    private final Plugin plugin;

    public InspectService(Plugin plugin, HighlightService highlights, Supplier<FrameSortSettings> settings,
                          Supplier<Messages> messages, TargetIndex index, TargetResolver resolver, TrustService trust,
                          SorterService sorters, PadService pads, CommandsConfig commands) {
        this.plugin = plugin;
        this.highlights = highlights;
        this.settings = settings;
        this.messages = messages;
        this.index = index;
        this.resolver = resolver;
        this.trust = trust;
        this.sorters = sorters;
        this.pads = pads;
        this.commands = commands;
    }

    /** {@code pad} is the pad's type, or {@code null} for a sorter. */
    public void inspectSource(Player player, Block source, @Nullable PadType pad, ItemStack filter) {
        UUID owner = pad != null ? pads.ownerOf(source) : sorters.ownerOf(source);
        if (trust.canView(player, owner)) {
            int range = pad != null ? pad.range() : settings.get().sorter().range();
            open(player, new Source(source, pad != null, owner, range), filter, Opened.GESTURE);
            return;
        }
        Messages text = messages.get();
        Component kind = text.get(pad != null ? "inspect.source.pad" : "inspect.source.sorter");
        player.sendMessage(owner == null
                ? text.get("inspect.no-owner", Placeholder.component("source", kind))
                : text.get("inspect.not-trusted", Placeholder.component("source", kind),
                        Placeholder.unparsed("player", trust.name(owner))));
    }

    private void open(Player player, Source shown, ItemStack filter, Opened opened) {
        if (!shown.pad() && SorterService.opens(filter)) {
            player.sendMessage(messages.get().get("inspect.opens",
                    Placeholder.component("item", filter.effectiveName())));
        }
        // A copy: the player keeps using the item in their hand, and the page buttons keep this one.
        render(player, shown, filter.clone(), opened, 1);
    }

    private void render(Player player, Source shown, ItemStack filter, Opened opened, int page) {
        Block source = shown.block();
        boolean pad = shown.pad();
        UUID owner = shown.owner();
        Messages text = messages.get();
        Component kind = text.get(pad ? "inspect.source.pad" : "inspect.source.sorter");
        BlockKey center = FrameGeometry.key(source);
        List<Component> lines = new ArrayList<>();
        List<HighlightService.Highlight> marks = new ArrayList<>();
        List<Component> facts = new ArrayList<>();
        facts.add(text.get("inspect.details.place", coordinates(source.getLocation())));
        facts.add(owner == null ? text.get("inspect.details.no-owner")
                : text.get("inspect.details.owner", Placeholder.unparsed("player", trust.name(owner))));
        if (!pad) {
            facts.add(text.get("inspect.details.sorted",
                    Placeholder.unparsed("count", String.valueOf(sorters.sorted(source)))));
        }
        TagResolver details = Placeholder.component("details", Component.join(JoinConfiguration.newlines(), facts));
        Component title;
        if (filter.isEmpty()) {
            title = text.get("inspect.title", Placeholder.component("source", kind), details);
            List<ItemFrame> frames = new ArrayList<>(resolver.targets(source, owner, shown.range()));
            frames.sort(Comparator.comparingLong(frame -> FrameGeometry.distanceSquared(frame, center)));
            for (ItemFrame frame : frames) {
                Destination destination = resolver.destination(frame);
                lines.add(text.get("inspect.line",
                        Placeholder.component("entry", entry(text, frame, destination, center))));
                marks.add(highlight(frame, destination));
            }
        } else {
            title = text.get("inspect.title-item", Placeholder.component("source", kind),
                    Placeholder.component("item", filter.effectiveName()), details);
            filtered(text, source, owner, shown.range(), center, filter, lines, marks);
        }
        ChatPager.send(player, text, title, lines, page, settings.get().inspect().pageSize(),
                target -> pageButton(center, pad, filter, opened, target));
        highlight(player, marks);
    }

    private ClickEvent<?> pageButton(BlockKey source, boolean pad, ItemStack filter, Opened opened, int page) {
        return ClickEvent.callback(ClickCallback.widen(viewer -> turnPage(viewer, source, pad, filter, opened, page),
                Player.class), PAGE_BUTTON);
    }

    private void turnPage(Player viewer, BlockKey key, boolean pad, ItemStack filter, Opened opened, int page) {
        // Paper keeps the button after FrameSort is disabled without a server stop; nothing may run then.
        if (!plugin.isEnabled()) {
            return;
        }
        Source source = sourceAt(viewer, key, pad);
        if (source == null || !mayView(viewer, opened) || !trust.canView(viewer, source.owner())) {
            viewer.sendMessage(messages.get().get("inspect.gone"));
            return;
        }
        render(viewer, source, filter, opened, page);
    }

    // The sorter or pad still at the key, or null; never loads its chunk.
    private @Nullable Source sourceAt(Player viewer, BlockKey key, boolean pad) {
        World world = viewer.getServer().getWorld(key.world());
        if (world == null || !world.isChunkLoaded(key.x() >> 4, key.z() >> 4)) {
            return null;
        }
        Block block = world.getBlockAt(key.x(), key.y(), key.z());
        if (pad) {
            PadType type = pads.padAt(block);
            return type == null ? null : new Source(block, true, pads.ownerOf(block), type.range());
        }
        return sorters.isSorter(block)
                ? new Source(block, false, sorters.ownerOf(block), settings.get().sorter().range()) : null;
    }

    // The same checks as when the listing was opened: the gesture's permission, or the where command's.
    private boolean mayView(Player viewer, Opened opened) {
        return switch (opened) {
            case GESTURE -> viewer.hasPermission(INSPECT_PERMISSION);
            case WHERE -> commands.usageFor("where", viewer::hasPermission) != null;
        };
    }

    /**
     * Inspects the nearest sorter or pad whose range reaches {@code player} and that they may see, as if {@code item}
     * were in the off hand; false if there is none.
     */
    public boolean inspectNearest(Player player, ItemStack item) {
        Block here = player.getLocation().getBlock();
        BlockKey key = FrameGeometry.key(here);
        long best = Long.MAX_VALUE;
        Source nearest = null;
        for (Source source : sourcesNear(here)) {
            // Like inspecting by hand, and like its page buttons: only a pad whose column is complete.
            if (source.pad() && pads.padAt(source.block()) == null) {
                continue;
            }
            long distance = FrameGeometry.key(source.block()).distanceSquared(key);
            if (distance <= best && trust.canView(player, source.owner())) {
                best = distance;
                nearest = source;
            }
        }
        if (nearest == null) {
            return false;
        }
        open(player, nearest, item, Opened.WHERE);
        return true;
    }

    public void inspectFrame(Player player, ItemFrame frame) {
        Messages text = messages.get();
        List<Component> lines = new ArrayList<>();
        lines.add(status(text, frame));
        if (index.isTarget(frame)) {
            UUID owner = FrameCodec.owner(frame);
            lines.add(owner != null ? text.get("frame.owner", Placeholder.unparsed("player", trust.name(owner)))
                    : text.get("frame.no-owner"));
        }

        lines.add(switch (resolver.destination(frame)) {
            case CONTAINER -> text.get("frame.into.container",
                    Placeholder.component("block", resolver.blockName(FrameGeometry.attachedBlock(frame))));
            case DROPPED -> text.get("frame.into.dropped");
            case DESTROYED -> text.get("frame.into.lava");
        });
        if (sorters.isSorter(FrameGeometry.attachedBlock(frame))) {
            lines.add(text.get("frame.on-sorter"));
        }

        ItemStack shown = frame.getItem();
        List<ItemStack> contents = TargetResolver.contents(shown);
        if (ItemTagCodec.read(shown) != null) {
            TagCatalog.TagView view = resolver.tag(shown);
            if (view != null) {
                String tagCommand = commands.usageFor("tag-show", player::hasPermission);
                // Only a line that has the click says to click it.
                Component accepts = text.get(tagCommand == null ? "frame.accepts.tag" : "frame.accepts.tag-link",
                        Placeholder.unparsed("tag", view.key().asString()),
                        Placeholder.unparsed("count",
                                String.valueOf(view.materials().size() + view.customItems().size())));
                lines.add(tagCommand == null ? accepts
                        : accepts.clickEvent(ClickEvent.runCommand(tagCommand + " " + view.key().asString())));
            }
        } else if (contents.isEmpty()) {
            lines.add(text.get("frame.accepts.exact", Placeholder.component("item", shown.effectiveName())));
            String customId = resolver.customId(shown);
            // The ID, not the name: an anvil renames an item, and two custom items can share a name.
            lines.add(customId != null
                    ? text.get("frame.accepts.custom", Placeholder.unparsed("id", customId))
                    : text.get("frame.accepts.similar",
                            Placeholder.component("material", Component.translatable(shown.getType()))));
        }
        if (!contents.isEmpty()) {
            lines.add(text.get("frame.accepts.contents",
                    Placeholder.unparsed("count", String.valueOf(contents.size()))));
        }
        if (resolver.isDefaultTarget(shown)) {
            lines.add(text.get("frame.accepts.default"));
        }

        long sources = sourcesNear(frame.getLocation().getBlock()).stream()
                .filter(source -> resolver.reaches(source.block(), source.owner(), frame))
                .count();
        lines.add(text.get("frame.sources", Placeholder.unparsed("count", String.valueOf(sources))));
        // One message, like a page of a list, so the card stays together in chat.
        player.sendMessage(text.get("frame.layout",
                Placeholder.component("title", text.get("frame.title", coordinates(frame.getLocation()))),
                Placeholder.component("lines", Component.join(JoinConfiguration.newlines(), lines))));
        highlight(player, List.of(highlight(frame, resolver.destination(frame))));
    }

    public void toggleMark(Player player, ItemFrame frame) {
        Messages text = messages.get();
        if (!player.hasPermission(TargetIndex.MARK_PERMISSION)) {
            player.sendActionBar(text.get("mark.no-permission"));
            return;
        }
        if (!index.markable(frame)) {
            player.sendActionBar(text.get("mark.wrong-type"));
            return;
        }
        boolean mark = !index.isMarked(frame);
        UUID owner = FrameCodec.owner(frame);
        if (!mark && settings.get().ownership() && owner != null && !owner.equals(player.getUniqueId())) {
            player.sendActionBar(text.get("mark.not-yours", Placeholder.unparsed("player", trust.name(owner))));
            return;
        }
        if (mark) {
            index.mark(frame, player.getUniqueId());
        } else {
            index.unmark(frame);
        }
        showMark(player, frame, mark);
    }

    /** How marking or unmarking a frame answers, by hand or by automark: the action bar, and the item glowing. */
    public void showMark(Player player, ItemFrame frame, boolean marked) {
        player.sendActionBar(messages.get().get(marked ? "mark.marked" : "mark.unmarked"));
        InspectSettings.Colors colors = settings.get().inspect().colors();
        highlight(player, List.of(new HighlightService.Highlight(frame, marked ? colors.container() : colors.lava())));
    }

    private void filtered(Messages text, Block source, @Nullable UUID owner, int range, BlockKey center,
                          ItemStack filter, List<Component> lines, List<HighlightService.Highlight> marks) {
        List<TargetResolver.Level> levels = resolver.plan(source, owner, range, filter).levels();
        for (int i = 0; i < levels.size(); i++) {
            TargetResolver.Level level = levels.get(i);
            Target end = level.end();
            List<Target> targets = Stream.concat(
                    level.containers().stream().map(frame -> new Target(frame, Destination.CONTAINER)),
                    Stream.ofNullable(end)).toList();
            Component header = text.get(i == 0 ? "inspect.level.first" : "inspect.level.next",
                    Placeholder.unparsed("number", String.valueOf(i + 1)));
            for (int j = 0; j < targets.size(); j++) {
                Target target = targets.get(j);
                Component branch = text.get(j < targets.size() - 1 ? "inspect.branch.middle" : "inspect.branch.last",
                        Placeholder.component("entry", entry(text, target.frame(), target.destination(), center)));
                // A header shares the pager line of its first target, so the total and the page size count targets.
                lines.add(j == 0 ? Component.join(JoinConfiguration.newlines(), header, branch) : branch);
                marks.add(highlight(target.frame(), target.destination()));
            }
            if (end != null) {
                if (end.destination() == Destination.DESTROYED) {
                    endWith(lines, text.get("inspect.excess.lava"));
                }
                return;
            }
        }
        if (!lines.isEmpty()) {
            endWith(lines, text.get("inspect.excess.stay"));
        }
    }

    private static void endWith(List<Component> lines, Component closing) {
        lines.set(lines.size() - 1, Component.join(JoinConfiguration.newlines(), lines.getLast(), closing));
    }

    private Component entry(Messages text, ItemFrame frame, Destination destination, BlockKey center) {
        Component target = switch (destination) {
            case CONTAINER -> resolver.blockName(FrameGeometry.attachedBlock(frame));
            case DROPPED -> text.get("inspect.target.dropped");
            case DESTROYED -> text.get("inspect.target.lava");
        };
        Location location = frame.getLocation();
        return text.get("inspect.entry",
                Placeholder.component("item", frame.getItem().effectiveName()),
                Placeholder.component("target", target),
                coordinates(location),
                Placeholder.unparsed("distance",
                        String.valueOf(Math.round(Math.sqrt(FrameGeometry.distanceSquared(frame, center))))));
    }

    private Component status(Messages text, ItemFrame frame) {
        if (!index.markable(frame)) {
            return text.get("frame.status.wrong-type");
        }
        return text.get(index.isTarget(frame) ? "frame.status.target" : "frame.status.unmarked");
    }

    private HighlightService.Highlight highlight(ItemFrame frame, Destination destination) {
        return new HighlightService.Highlight(frame, destination.color(settings.get().inspect().colors()));
    }

    private List<Source> sourcesNear(Block center) {
        int range = settings.get().sorter().range();
        long rangeSquared = (long) range * range;
        BlockKey key = FrameGeometry.key(center);
        return Stream.concat(
                sorters.blocks().stream()
                        .filter(sorter -> FrameGeometry.key(sorter).distanceSquared(key) <= rangeSquared)
                        .map(sorter -> new Source(sorter, false, sorters.ownerOf(sorter), range)),
                pads.reaching(center).stream()
                        .map(pad -> new Source(pad.top(), true, pad.owner(), pad.type().range()))).toList();
    }

    private void highlight(Player player, List<HighlightService.Highlight> marks) {
        highlights.show(player, marks, settings.get().inspect().highlightSeconds());
    }

    private static TagResolver coordinates(Location location) {
        return TagResolver.resolver(
                Placeholder.unparsed("x", String.valueOf(location.getBlockX())),
                Placeholder.unparsed("y", String.valueOf(location.getBlockY())),
                Placeholder.unparsed("z", String.valueOf(location.getBlockZ())));
    }
}
