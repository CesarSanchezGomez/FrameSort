package com.cesarcosmico.framesort.service;

import com.cesarcosmico.framesort.config.FrameSortSettings;
import com.cesarcosmico.framesort.config.TargetSettings;
import com.cesarcosmico.framesort.model.BlockKey;
import com.cesarcosmico.framesort.model.FramePosition;
import com.cesarcosmico.framesort.model.TargetRegistration;
import com.cesarcosmico.framesort.model.TargetSet;
import com.cesarcosmico.framesort.text.ChatPager;
import com.cesarcosmico.framesort.text.Messages;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * What players see with the tool: a source's targets (highlighted for them only and listed page by page), what a
 * target frame accepts, and marking frames as targets.
 */
public final class InspectService {

    public static final String MARK_PERMISSION = "framesort.target.create";

    private static final Color CONTAINER_COLOR = Color.LIME;
    private static final Color DROP_COLOR = Color.YELLOW;
    private static final Color LAVA_COLOR = Color.RED;

    /** The last list a player inspected, so the page buttons can show another page of it. */
    private record Listing(Component title, List<Component> lines) {
    }

    private record Highlight(Location location, Color color) {
    }

    private final Plugin plugin;
    private final Supplier<FrameSortSettings> settings;
    private final Supplier<Messages> messages;
    private final TargetIndex index;
    private final TargetResolver resolver;
    private final SorterService sorters;
    private final PadService pads;
    private final String commandName;
    private final String pageCommand;
    private final Map<UUID, Listing> listings = new HashMap<>();
    private final Map<UUID, ScheduledTask> highlights = new HashMap<>();

    public InspectService(Plugin plugin, Supplier<FrameSortSettings> settings, Supplier<Messages> messages,
                          TargetIndex index, TargetResolver resolver, SorterService sorters,
                          PadService pads, String commandName) {
        this.plugin = plugin;
        this.settings = settings;
        this.messages = messages;
        this.index = index;
        this.resolver = resolver;
        this.sorters = sorters;
        this.pads = pads;
        this.commandName = commandName;
        this.pageCommand = "/" + commandName + " inspect %d";
    }

    /**
     * Lists and highlights a sorter's or pad's targets. With an item in {@code filter}, only the targets that item
     * would go to, split into containers and drop spots, like a real delivery would choose them.
     */
    public void inspectSource(Player player, Block source, boolean pad, ItemStack filter) {
        Messages text = messages.get();
        Component kind = text.get(pad ? "inspect.source.pad" : "inspect.source.sorter");
        BlockKey center = FrameGeometry.key(source);
        List<Component> lines = new ArrayList<>();
        List<Highlight> marks = new ArrayList<>();
        Component title;
        if (filter.isEmpty()) {
            title = text.get("inspect.title", Placeholder.component("source", kind), coordinates(source.getLocation()));
            List<ItemFrame> frames = new ArrayList<>(index.near(center, settings.get().delivery().maxDistance()));
            frames.sort(Comparator.comparingLong(frame -> distanceSquared(frame, center)));
            for (ItemFrame frame : frames) {
                if (frame.getItem().isEmpty() || !FrameGeometry.attachedLoaded(frame) || !allowed(frame)) {
                    continue;
                }
                lines.add(line(text, frame, center));
                marks.add(highlight(frame));
            }
        } else {
            title = text.get("inspect.title-item", Placeholder.component("source", kind),
                    Placeholder.component("item", filter.effectiveName()), coordinates(source.getLocation()));
            filtered(text, source, center, filter, lines, marks);
        }
        listings.put(player.getUniqueId(), new Listing(title, List.copyOf(lines)));
        ChatPager.send(player, text, title, lines, 1, settings.get().inspect().pageSize(), pageCommand);
        highlight(player, marks);
    }

    /** Shows another page of the last inspected list. */
    public boolean showPage(Player player, int page) {
        Listing listing = listings.get(player.getUniqueId());
        if (listing == null) {
            return false;
        }
        ChatPager.send(player, messages.get(), listing.title(), listing.lines(), page,
                settings.get().inspect().pageSize(), pageCommand);
        return true;
    }

    /** Explains one frame: whether it is a target, where its items go, what it accepts and who sends to it. */
    public void inspectFrame(Player player, ItemFrame frame) {
        Messages text = messages.get();
        player.sendMessage(text.get("frame.header", coordinates(frame.getLocation())));
        player.sendMessage(status(text, frame));

        Block attached = FrameGeometry.attachedBlock(frame);
        Inventory inventory = DeliveryService.inventory(attached);
        if (DeliveryService.isLava(frame)) {
            player.sendMessage(text.get("frame.into.lava"));
        } else if (inventory != null && settings.get().delivery().insertIntoContainers()) {
            player.sendMessage(text.get("frame.into.container", Placeholder.component("block", blockName(attached))));
        } else {
            player.sendMessage(text.get("frame.into.dropped"));
        }

        ItemStack shown = frame.getItem();
        TagCatalog.TagView view = resolver.tag(shown);
        if (view != null) {
            player.sendMessage(text.get("frame.accepts.tag",
                    Placeholder.unparsed("tag", view.key().asString()),
                    Placeholder.unparsed("count", String.valueOf(view.materials().size())))
                    .clickEvent(ClickEvent.runCommand("/" + commandName + " tag " + view.key().asString())));
        }
        player.sendMessage(text.get("frame.accepts.exact", Placeholder.component("item", shown.effectiveName())));
        List<ItemStack> contents = TargetResolver.contents(shown);
        if (!contents.isEmpty()) {
            player.sendMessage(text.get("frame.accepts.contents",
                    Placeholder.unparsed("count", String.valueOf(contents.size()))));
        } else {
            player.sendMessage(text.get("frame.accepts.similar",
                    Placeholder.component("material", Component.translatable(shown.getType()))));
        }
        if (shown.getType() == settings.get().delivery().defaultTargetItem()) {
            player.sendMessage(text.get("frame.accepts.default"));
        }

        BlockKey key = FrameGeometry.key(frame.getLocation().getBlock());
        int radius = settings.get().delivery().maxDistance();
        long sources = sorters.blocks().stream()
                .filter(block -> FrameGeometry.key(block).distanceSquared(key) <= (long) radius * radius)
                .count() + pads.near(key, radius).size();
        player.sendMessage(text.get("frame.sources", Placeholder.unparsed("count", String.valueOf(sources))));
        highlight(player, List.of(highlight(frame)));
    }

    /** Marks or unmarks a frame as a target (manual registration). */
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
        FramePosition position = FrameGeometry.position(frame);
        if (!targets.positions().contains(position)) {
            player.sendMessage(text.get("mark.wrong-position",
                    Placeholder.component("position", positionName(text, position)),
                    Placeholder.component("allowed", Component.join(JoinConfiguration.commas(true),
                            targets.positions().stream().sorted().map(p -> positionName(text, p)).toList()))));
            return;
        }
        boolean mark = !index.isMarked(frame);
        index.setMarked(frame, player.getUniqueId(), mark);
        player.sendMessage(text.get(mark ? "mark.marked" : "mark.unmarked"));
        highlight(player, List.of(new Highlight(frame.getLocation(), mark ? CONTAINER_COLOR : LAVA_COLOR)));
    }

    public void forget(UUID player) {
        listings.remove(player);
        ScheduledTask task = highlights.remove(player);
        if (task != null) {
            task.cancel();
        }
    }

    public void stopAll() {
        highlights.values().forEach(ScheduledTask::cancel);
        highlights.clear();
        listings.clear();
    }

    private void filtered(Messages text, Block source, BlockKey center, ItemStack filter,
                          List<Component> lines, List<Highlight> marks) {
        boolean insert = settings.get().delivery().insertIntoContainers();
        TargetSet<ItemFrame> containers = new TargetSet<>();
        TargetSet<ItemFrame> drops = new TargetSet<>();
        for (TargetResolver.Match match : resolver.matches(source, filter)) {
            ItemFrame frame = match.frame();
            if (!frame.isValid() || !FrameGeometry.attachedLoaded(frame)) {
                continue;
            }
            if (insert && DeliveryService.inventory(FrameGeometry.attachedBlock(frame)) != null) {
                containers.add(match.priority(), frame);
            } else {
                drops.add(match.priority(), frame);
            }
        }
        List<ItemFrame> into = containers.targets();
        List<ItemFrame> overflow = drops.targets().stream().filter(frame -> !DeliveryService.isLava(frame)).toList();
        boolean lava = drops.targets().stream().anyMatch(DeliveryService::isLava);
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
        Block attached = FrameGeometry.attachedBlock(frame);
        Component target;
        if (DeliveryService.isLava(frame)) {
            target = text.get("inspect.target.lava");
        } else if (settings.get().delivery().insertIntoContainers() && DeliveryService.inventory(attached) != null) {
            target = blockName(attached);
        } else {
            target = text.get("inspect.target.dropped");
        }
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
        FramePosition position = FrameGeometry.position(frame);
        TagResolver where = Placeholder.component("position", positionName(text, position));
        if (!targets.frameTypes().contains(frame.getType())) {
            return text.get("frame.status.wrong-type");
        }
        if (!targets.positions().contains(position)) {
            return text.get("frame.status.not-allowed", where);
        }
        return index.isTarget(frame) ? text.get("frame.status.target", where) : text.get("frame.status.unmarked");
    }

    private boolean allowed(ItemFrame frame) {
        return settings.get().targets().positions().contains(FrameGeometry.position(frame));
    }

    private Highlight highlight(ItemFrame frame) {
        Color color;
        if (DeliveryService.isLava(frame)) {
            color = LAVA_COLOR;
        } else if (settings.get().delivery().insertIntoContainers()
                && DeliveryService.inventory(FrameGeometry.attachedBlock(frame)) != null) {
            color = CONTAINER_COLOR;
        } else {
            color = DROP_COLOR;
        }
        return new Highlight(frame.getLocation(), color);
    }

    // Particles sent to this player only; the player's scheduler drops the task if they leave.
    private void highlight(Player player, List<Highlight> marks) {
        forgetHighlight(player.getUniqueId());
        if (marks.isEmpty()) {
            return;
        }
        int runs = settings.get().inspect().highlightSeconds() * 2;
        int[] left = {runs};
        ScheduledTask task = player.getScheduler().runAtFixedRate(plugin, scheduled -> {
            if (left[0]-- <= 0) {
                scheduled.cancel();
                highlights.remove(player.getUniqueId(), scheduled);
                return;
            }
            for (Highlight mark : marks) {
                player.spawnParticle(Particle.DUST, mark.location(), 6, 0.15, 0.15, 0.15, 0,
                        new Particle.DustOptions(mark.color(), 1.3f));
            }
        }, () -> highlights.remove(player.getUniqueId()), 1, 10);
        if (task != null) {
            highlights.put(player.getUniqueId(), task);
        }
    }

    private void forgetHighlight(UUID player) {
        ScheduledTask task = highlights.remove(player);
        if (task != null) {
            task.cancel();
        }
    }

    private static long distanceSquared(ItemFrame frame, BlockKey center) {
        return FrameGeometry.key(frame.getLocation().getBlock()).distanceSquared(center);
    }

    private static Component blockName(Block block) {
        return Component.translatable(block.getType());
    }

    private static Component positionName(Messages text, FramePosition position) {
        return text.get("position." + position.name().toLowerCase(Locale.ROOT));
    }

    private static TagResolver coordinates(Location location) {
        return TagResolver.resolver(
                Placeholder.unparsed("x", String.valueOf(location.getBlockX())),
                Placeholder.unparsed("y", String.valueOf(location.getBlockY())),
                Placeholder.unparsed("z", String.valueOf(location.getBlockZ())));
    }
}
