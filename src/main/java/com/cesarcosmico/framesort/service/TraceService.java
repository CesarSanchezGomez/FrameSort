package com.cesarcosmico.framesort.service;

import com.cesarcosmico.framesort.config.FrameSortSettings;
import com.cesarcosmico.framesort.text.Messages;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** With nobody tracing, a delivery costs one empty-map check. */
public final class TraceService {

    // At most one action bar per player this often, so a busy sorter does not flood it.
    private static final long MIN_INTERVAL_MILLIS = 150;
    // The trail is a two-strand helix around the straight path: an arc would hit the ceiling underground.
    static final double HELIX_RADIUS = 0.3;
    static final double TURN_LENGTH = 1.5;
    private static final double POINT_SPACING = 0.2;
    // Bounds the particles of one delivery; on long paths the points spread out instead.
    private static final int MAX_POINTS = 120;
    private static final double BLOCKS_PER_TICK = 1.5;
    private static final int MIN_TICKS = 4;
    private static final int MAX_TICKS = 20;
    private static final float DUST_SIZE = 0.7f;

    private final Plugin plugin;
    private final Server server;
    private final Supplier<FrameSortSettings> settings;
    private final Supplier<Messages> messages;
    private final Map<UUID, Long> until = new HashMap<>();
    private final Map<UUID, Long> lastShown = new HashMap<>();

    public TraceService(Plugin plugin, Supplier<FrameSortSettings> settings, Supplier<Messages> messages) {
        this.plugin = plugin;
        this.server = plugin.getServer();
        this.settings = settings;
        this.messages = messages;
    }

    public int start(Player player, int seconds) {
        int granted = Math.clamp(seconds, 1, settings.get().inspect().traceMaxSeconds());
        UUID id = player.getUniqueId();
        long deadline = System.currentTimeMillis() + granted * 1000L;
        until.put(id, deadline);
        // A restart or stop changes the deadline, so a stale run does nothing.
        player.getScheduler().runDelayed(plugin, task -> {
            if (until.remove(id, deadline)) {
                lastShown.remove(id);
                player.sendMessage(messages.get().get("trace.ended"));
            }
        }, () -> forget(id), granted * 20L);
        return granted;
    }

    public boolean stop(Player player) {
        lastShown.remove(player.getUniqueId());
        return until.remove(player.getUniqueId()) != null;
    }

    private void forget(UUID player) {
        until.remove(player);
        lastShown.remove(player);
    }

    public void report(DeliveryService.Delivered delivered) {
        if (until.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        int radius = settings.get().inspect().traceRadius();
        for (Iterator<Map.Entry<UUID, Long>> it = until.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Long> entry = it.next();
            Player player = server.getPlayer(entry.getKey());
            if (player == null) {
                it.remove();
                continue;
            }
            if (entry.getValue() < now) {
                it.remove();
                player.sendMessage(messages.get().get("trace.ended"));
                continue;
            }
            Location from = delivered.from();
            if (!player.getWorld().equals(from.getWorld())
                    || player.getLocation().distanceSquared(from) > (double) radius * radius) {
                continue;
            }
            Long last = lastShown.get(entry.getKey());
            if (last != null && now - last < MIN_INTERVAL_MILLIS) {
                continue;
            }
            lastShown.put(entry.getKey(), now);
            show(player, delivered);
        }
    }

    private void show(Player player, DeliveryService.Delivered delivered) {
        Location to = delivered.to();
        player.sendActionBar(messages.get().get("trace.action-bar",
                Placeholder.component("item", delivered.item().effectiveName()),
                Placeholder.unparsed("amount", String.valueOf(delivered.amount())),
                Placeholder.component("kind", messages.get().get("trace.kind."
                        + delivered.kind().name().toLowerCase(Locale.ROOT))),
                Placeholder.unparsed("x", String.valueOf(to.getBlockX())),
                Placeholder.unparsed("y", String.valueOf(to.getBlockY())),
                Placeholder.unparsed("z", String.valueOf(to.getBlockZ()))));
        drawHelix(player, delivered.from(), to, delivered.kind().color(settings.get().inspect().colors()));
    }

    private void drawHelix(Player player, Location from, Location to, Color color) {
        double length = from.distance(to);
        if (length == 0) {
            return;
        }
        Particle.DustOptions[] tones = {new Particle.DustOptions(color, DUST_SIZE),
                new Particle.DustOptions(lighter(color), DUST_SIZE)};
        int ticks = (int) Math.clamp(Math.ceil(length / BLOCKS_PER_TICK), MIN_TICKS, MAX_TICKS);
        player.getScheduler().runAtFixedRate(plugin,
                new Helix(player, from, to, length, Math.max(POINT_SPACING, length / MAX_POINTS), ticks, tones),
                null, 1L, 1L);
    }

    static Vector helixPoint(Vector from, Vector axis, Vector u, Vector v, double distance, double phase) {
        double angle = phase + 2 * Math.PI * distance / TURN_LENGTH;
        return from.clone().add(axis.clone().multiply(distance))
                .add(u.clone().multiply(HELIX_RADIUS * Math.cos(angle)))
                .add(v.clone().multiply(HELIX_RADIUS * Math.sin(angle)));
    }

    // Halfway to white; Color#mixColors is documented as dyeing, not as a mix.
    private static Color lighter(Color color) {
        return Color.fromRGB((color.getRed() + 255) / 2, (color.getGreen() + 255) / 2, (color.getBlue() + 255) / 2);
    }

    /** Draws the strands up to where the head has reached, one step per tick, then cancels itself. */
    private static final class Helix implements Consumer<ScheduledTask> {

        private final Player player;
        private final World world;
        private final Vector start;
        private final Vector axis;
        private final Vector u;
        private final Vector v;
        private final double length;
        private final double spacing;
        private final int ticks;
        private final Particle.DustOptions[] tones;
        private int tick;
        private double drawn;

        private Helix(Player player, Location from, Location to, double length, double spacing, int ticks,
                      Particle.DustOptions[] tones) {
            this.player = player;
            this.world = from.getWorld();
            this.start = from.toVector();
            this.axis = to.toVector().subtract(start).multiply(1 / length);
            // Any vector not parallel to the axis gives the plane the strands turn in.
            Vector side = Math.abs(axis.getY()) < 0.9 ? new Vector(0, 1, 0) : new Vector(1, 0, 0);
            this.u = axis.getCrossProduct(side).normalize();
            this.v = axis.getCrossProduct(u);
            this.length = length;
            this.spacing = spacing;
            this.ticks = ticks;
            this.tones = tones;
        }

        @Override
        public void accept(ScheduledTask task) {
            tick++;
            if (!player.getWorld().equals(world)) {
                task.cancel();
                return;
            }
            double head = length * tick / ticks;
            for (; drawn <= head; drawn += spacing) {
                for (int strand = 0; strand < tones.length; strand++) {
                    Vector point = helixPoint(start, axis, u, v, drawn, strand * Math.PI);
                    player.spawnParticle(Particle.DUST, point.getX(), point.getY(), point.getZ(), 1, 0, 0, 0, 0,
                            tones[strand]);
                }
            }
            if (tick >= ticks) {
                task.cancel();
            }
        }
    }
}
