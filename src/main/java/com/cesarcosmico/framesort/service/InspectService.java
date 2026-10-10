package com.cesarcosmico.framesort.service;

import com.cesarcosmico.framesort.config.CommandsConfig;
import com.cesarcosmico.framesort.config.FrameSortSettings;
import com.cesarcosmico.framesort.config.InspectSettings;
import com.cesarcosmico.framesort.config.TargetSettings;
import com.cesarcosmico.framesort.item.ItemTagCodec;
import com.cesarcosmico.framesort.model.BlockKey;
import com.cesarcosmico.framesort.model.FramePosition;
import com.cesarcosmico.framesort.model.TargetRegistration;
import com.cesarcosmico.framesort.text.ChatPager;
import com.cesarcosmico.framesort.text.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;

public final class InspectService {

    private static final String MARK_PERMISSION = "framesort.target.create";

    private record Listing(Component title, List<Component> lines) {
    }

    private final Supplier<FrameSortSettings> settings;
    private final Supplier<Messages> messages;
    private final TargetIndex index;
    private final TargetResolver resolver;
    private final SorterService sorters;
    private final PadService pads;
    private final CommandsConfig commands;
    private final Map<UUID, Listing> listings = new HashMap<>();
    private final HighlightService highlights;

    public InspectService(HighlightService highlights, Supplier<FrameSortSettings> settings,
                          Supplier<Messages> messages, TargetIndex index, TargetResolver resolver,
                          SorterService sorters, PadService pads, CommandsConfig commands) {
        this.highlights = highlights;
        this.settings = settings;
        this.messages = messages;
        this.index = index;
        this.resolver = resolver;
        this.sorters = sorters;
        this.pads = pads;
        this.commands = commands;
    }

    /** With an item in {@code filter}, only where that item would go, chosen like a real delivery. */
    public void inspectSource(Player player, Block source, boolean pad, ItemStack filter) {
        Messages text = messages.get();
        Component kind = text.get(pad ? "inspect.source.pad" : "inspect.source.sorter");
        BlockKey center = FrameGeometry.key(source);
        List<Component> lines = new ArrayList<>();
        List<HighlightService.Highlight> marks = new ArrayList<>();
        TagResolver sorted = Placeholder.component("sorted", pad ? Component.empty()
                : text.get("inspect.sorted", Placeholder.unparsed("count", String.valueOf(sorters.sorted(source)))));
        Component title;
        if (filter.isEmpty()) {
            title = text.get("inspect.title", Placeholder.component("source", kind), coordinates(source.getLocation()),
                    sorted);
            List<ItemFrame> frames = new ArrayList<>(resolver.targets(source));
            frames.sort(Comparator.comparingLong(frame -> distanceSquared(frame, center)));
            for (ItemFrame frame : frames) {
                lines.add(line(text, frame, center));
                marks.add(highlight(frame));
            }
        } else {
            title = text.get("inspect.title-item", Placeholder.component("source", kind),
                    Placeholder.component("item", filter.effectiveName()), coordinates(source.getLocation()), sorted);
            filtered(text, source, center, filter, lines, marks);
        }
        listings.put(player.getUniqueId(), new Listing(title, List.copyOf(lines)));
        ChatPager.send(player, text, title, lines, 1, settings.get().inspect().pageSize(), pageCommand(player));
        highlight(player, marks);
    }

    /** Inspects the nearest sorter or pad in delivery range as if {@code item} were in the off hand; false if none. */
    public boolean inspectNearest(Player player, ItemStack item) {
        BlockKey here = FrameGeometry.key(player.getLocation().getBlock());
        int radius = settings.get().delivery().maxDistance();
        long best = (long) radius * radius;
        Block nearest = null;
        boolean pad = false;
        for (Block sorter : sorters.blocks()) {
            long distance = FrameGeometry.key(sorter).distanceSquared(here);
            if (distance <= best) {
                best = distance;
                nearest = sorter;
            }
        }
        for (Block top : pads.near(here, radius)) {
            long distance = FrameGeometry.key(top).distanceSquared(here);
            if (distance <= best) {
                best = distance;
                nearest = top;
                pad = true;
            }
        }
        if (nearest == null) {
            return false;
        }
        inspectSource(player, nearest, pad, item);
        return true;
    }

    public boolean showPage(Player player, int page) {
        Listing listing = listings.get(player.getUniqueId());
        if (listing == null) {
            return false;
        }
        ChatPager.send(player, messages.get(), listing.title(), listing.lines(), page,
                settings.get().inspect().pageSize(), pageCommand(player));
        return true;
    }

    public void inspectFrame(Player player, ItemFrame frame) {
        Messages text = messages.get();
        List<Component> lines = new ArrayList<>();
        lines.add(status(text, frame));

        lines.add(switch (resolver.destination(frame)) {
            case CONTAINER -> text.get("frame.into.container",
                    Placeholder.component("block", blockName(FrameGeometry.attachedBlock(frame))));
            case DROPPED -> text.get("frame.into.dropped");
            case DESTROYED -> text.get("frame.into.lava");
        });

        ItemStack shown = frame.getItem();
        if (ItemTagCodec.read(shown) != null) {
            TagCatalog.TagView view = resolver.tag(shown);
            if (view != null) {
                String tagCommand = commands.usageFor("tag-show", player::hasPermission);
                Component accepts = text.get("frame.accepts.tag",
                        Placeholder.unparsed("tag", view.key().asString()),
                        Placeholder.unparsed("count", String.valueOf(view.materials().size())));
                lines.add(tagCommand == null ? accepts
                        : accepts.clickEvent(ClickEvent.runCommand(tagCommand + " " + view.key().asString())));
            }
        } else {
            lines.add(text.get("frame.accepts.exact", Placeholder.component("item", shown.effectiveName())));
            List<ItemStack> contents = TargetResolver.contents(shown);
            if (!contents.isEmpty()) {
                lines.add(text.get("frame.accepts.contents",
                        Placeholder.unparsed("count", String.valueOf(contents.size()))));
            } else {
                lines.add(text.get("frame.accepts.similar",
                        Placeholder.component("material", Component.translatable(shown.getType()))));
            }
        }
        if (shown.getType() == settings.get().delivery().defaultTargetItem()) {
            lines.add(text.get("frame.accepts.default"));
        }

        BlockKey key = FrameGeometry.key(frame.getLocation().getBlock());
        int radius = settings.get().delivery().maxDistance();
        Stream<Block> inRange = Stream.concat(
                sorters.blocks().stream()
                        .filter(block -> FrameGeometry.key(block).distanceSquared(key) <= (long) radius * radius),
                pads.near(key, radius).stream());
        long sources = inRange.filter(source -> resolver.reaches(source, frame)).count();
        lines.add(text.get("frame.sources", Placeholder.unparsed("count", String.valueOf(sources))));
        // One message, like a page of a list, so the card stays together in chat.
        player.sendMessage(text.get("frame.layout",
                Placeholder.component("title", text.get("frame.title", coordinates(frame.getLocation()))),
                Placeholder.component("lines", Component.join(JoinConfiguration.newlines(), lines))));
        highlight(player, List.of(highlight(frame)));
    }

    public void toggleMark(Player player, ItemFrame frame) {
        Messages text = messages.get();
        TargetSettings targets = settings.get().targets();
        if (targets.registration() == TargetRegistration.AUTOMATIC) {
            player.sendMessage(text.get("mark.automatic"));
            return;
        }
        if (!player.hasPermission(MARK_PERMISSION)) {
            player.sendMessage(text.get("mark.no-permission"));
            return;
        }
        if (!targets.frameTypes().contains(frame.getType())) {
            player.sendMessage(text.get("mark.wrong-type"));
            return;
        }
        Set<FramePosition> position = FrameGeometry.positions(frame);
        if (!targets.allows(position)) {
            player.sendMessage(text.get("mark.wrong-position",
                    Placeholder.component("position", positionName(text, position)),
                    Placeholder.component("allowed", Component.join(JoinConfiguration.commas(true),
                            targets.positions().stream().sorted().map(p -> positionName(text, Set.of(p))).toList()))));
            return;
        }
        boolean mark = !index.isMarked(frame);
        index.setMarked(frame, player.getUniqueId(), mark);
        player.sendMessage(text.get(mark ? "mark.marked" : "mark.unmarked"));
        InspectSettings.Colors colors = settings.get().inspect().colors();
        highlight(player, List.of(new HighlightService.Highlight(frame, mark ? colors.container() : colors.lava())));
    }

    public void forget(UUID player) {
        listings.remove(player);
        highlights.clear(player);
    }

    public void stopAll() {
        highlights.clearAll();
        listings.clear();
    }

    private void filtered(Messages text, Block source, BlockKey center, ItemStack filter,
                          List<Component> lines, List<HighlightService.Highlight> marks) {
        TargetResolver.Plan plan = resolver.plan(source, filter);
        List<ItemFrame> into = plan.containers();
        List<ItemFrame> overflow = plan.drops().stream().filter(frame -> !FrameGeometry.isLava(frame)).toList();
        boolean lava = plan.drops().stream().anyMatch(FrameGeometry::isLava);
        if (!into.isEmpty()) {
            lines.add(text.get("inspect.section.containers"));
            into.forEach(frame -> {
                lines.add(line(text, frame, center));
                marks.add(highlight(frame));
            });
        }
        if (!overflow.isEmpty()) {
            lines.add(text.get("inspect.section.dropped"));
            overflow.forEach(frame -> {
                lines.add(line(text, frame, center));
                marks.add(highlight(frame));
            });
        }
        if (!into.isEmpty() || !overflow.isEmpty() || lava) {
            lines.add(text.get(overflow.isEmpty() && lava ? "inspect.excess.lava" : "inspect.excess.stay"));
        }
    }

    private Component line(Messages text, ItemFrame frame, BlockKey center) {
        Component target = switch (resolver.destination(frame)) {
            case CONTAINER -> blockName(FrameGeometry.attachedBlock(frame));
            case DROPPED -> text.get("inspect.target.dropped");
            case DESTROYED -> text.get("inspect.target.lava");
        };
        Location location = frame.getLocation();
        return text.get("inspect.line",
                Placeholder.component("item", frame.getItem().effectiveName()),
                Placeholder.component("target", target),
                coordinates(location),
                Placeholder.unparsed("distance",
                        String.valueOf(Math.round(Math.sqrt(distanceSquared(frame, center))))));
    }

    private Component status(Messages text, ItemFrame frame) {
        TargetSettings targets = settings.get().targets();
        Set<FramePosition> position = FrameGeometry.positions(frame);
        TagResolver where = Placeholder.component("position", positionName(text, position));
        if (!targets.frameTypes().contains(frame.getType())) {
            return text.get("frame.status.wrong-type");
        }
        if (!targets.allows(position)) {
            return text.get("frame.status.not-allowed", where);
        }
        return index.isTarget(frame) ? text.get("frame.status.target", where) : text.get("frame.status.unmarked");
    }

    private HighlightService.Highlight highlight(ItemFrame frame) {
        return new HighlightService.Highlight(frame,
                resolver.destination(frame).color(settings.get().inspect().colors()));
    }

    private void highlight(Player player, List<HighlightService.Highlight> marks) {
        highlights.show(player, marks, settings.get().inspect().highlightSeconds());
    }

    // Links follow commands.yml and the viewer's permissions; without them the page buttons are plain text.
    private @Nullable String pageCommand(Player player) {
        String inspect = commands.usageFor("inspect", player::hasPermission);
        return inspect == null ? null : inspect + " %d";
    }

    private static long distanceSquared(ItemFrame frame, BlockKey center) {
        return FrameGeometry.key(frame.getLocation().getBlock()).distanceSquared(center);
    }

    private static Component blockName(Block block) {
        return Component.translatable(block.getType());
    }

    // A frame on a container without a facing counts as every side: it is just "a side".
    private static Component positionName(Messages text, Set<FramePosition> position) {
        return position.size() == 1
                ? text.get("position." + position.iterator().next().name().toLowerCase(Locale.ROOT))
                : text.get("position.side");
    }

    private static TagResolver coordinates(Location location) {
        return TagResolver.resolver(
                Placeholder.unparsed("x", String.valueOf(location.getBlockX())),
                Placeholder.unparsed("y", String.valueOf(location.getBlockY())),
                Placeholder.unparsed("z", String.valueOf(location.getBlockZ())));
    }
}
