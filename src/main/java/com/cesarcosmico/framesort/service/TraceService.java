package com.cesarcosmico.framesort.service;

import com.cesarcosmico.framesort.config.FrameSortSettings;
import com.cesarcosmico.framesort.text.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/** Distances are in blocks. */
public final class TraceService {

    private static final class Turn {
        private final long millis;
        private final int tick;
        private final Location from;
        private final Set<Location> drawn = new HashSet<>();

        private Turn(long millis, int tick, Location from) {
            this.millis = millis;
            this.tick = tick;
            this.from = from;
        }
    }

    private static final long MIN_INTERVAL_MILLIS = 150;
    // Shorter than this, a trail has no direction to draw.
    private static final double MIN_TRAIL_LENGTH = 1e-3;
    // A path closer to vertical than this takes its ring's plane from the x axis instead of the y axis.
    private static final double NEARLY_VERTICAL = 0.9;
    // From the source's face: inside the block nobody would see the start.
    private static final double START_BEYOND_CENTRE = 0.55;
    private static final int RING_TRAILS = 6;
    private static final double RING_RADIUS = 0.2;
    private static final double BLOCKS_PER_TICK = 1.0;
    private static final int MIN_TICKS = 8;
    private static final int MAX_TICKS = 40;
    private static final int STAGGER_TICKS = 2;
    private static final int SPLASH = 5;
    private static final double SPLASH_RADIUS = 0.5;
    private static final int SPLASH_TICKS = 6;

    private final Plugin plugin;
    private final Server server;
    private final Supplier<FrameSortSettings> settings;
    private final Supplier<Messages> messages;
    private final TrustService trust;
    // Keyed by the player, not the UUID: the retired callback of start() drops them when they leave.
    private final Map<Player, Long> until = new HashMap<>();
    private final Map<Player, Turn> lastShown = new HashMap<>();

    public TraceService(Plugin plugin, Supplier<FrameSortSettings> settings, Supplier<Messages> messages,
                        TrustService trust) {
        this.plugin = plugin;
        this.server = plugin.getServer();
        this.settings = settings;
        this.messages = messages;
        this.trust = trust;
    }

    public int start(Player player, int seconds) {
        int granted = Math.clamp(seconds, 1, settings.get().inspect().traceMaxSeconds());
        long deadline = System.currentTimeMillis() + granted * 1000L;
        until.put(player, deadline);
        // A restart or stop changes the deadline, so a stale run does nothing.
        player.getScheduler().runDelayed(plugin, task -> {
            if (until.remove(player, deadline)) {
                ended(player);
            }
        }, () -> stop(player), granted * 20L);
        return granted;
    }

    public boolean stop(Player player) {
        lastShown.remove(player);
        return until.remove(player) != null;
    }

    public void report(DeliveryService.Delivered delivered) {
        if (until.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        int radius = settings.get().inspect().traceRadius();
        for (Iterator<Map.Entry<Player, Long>> it = until.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Player, Long> entry = it.next();
            Player player = entry.getKey();
            if (entry.getValue() < now) {
                it.remove();
                ended(player);
                continue;
            }
            Location from = delivered.from();
            if (!sees(player, delivered, radius)) {
                continue;
            }
            Turn last = lastShown.get(player);
            int tick = server.getCurrentTick();
            if (startsNewTurn(last, tick, from)) {
                if (last != null && now - last.millis < MIN_INTERVAL_MILLIS) {
                    continue;
                }
                last = new Turn(now, tick, from);
                lastShown.put(player, last);
            }
            if (last.drawn.add(delivered.to())) {
                show(player, delivered);
            }
        }
    }

    private void ended(Player player) {
        lastShown.remove(player);
        player.sendMessage(messages.get().get("trace.ended"));
    }

    private boolean sees(Player player, DeliveryService.Delivered delivered, int radius) {
        Location from = delivered.from();
        return player.getWorld().equals(from.getWorld())
                && player.getLocation().distanceSquared(from) <= (double) radius * radius
                && trust.canView(player, delivered.owner());
    }

    // A source's deliveries on one tick are one turn, drawn together.
    private static boolean startsNewTurn(@Nullable Turn last, int tick, Location from) {
        return last == null || last.tick != tick || !last.from.equals(from);
    }

    private void show(Player player, DeliveryService.Delivered delivered) {
        Location to = delivered.to();
        player.sendActionBar(messages.get().get("trace.action-bar",
                Placeholder.component("item", delivered.item().effectiveName()),
                Placeholder.unparsed("amount", String.valueOf(delivered.amount())),
                Placeholder.component("kind", messages.get().get("trace.kind."
                        + delivered.destination().name().toLowerCase(Locale.ROOT))),
                Placeholder.unparsed("x", String.valueOf(to.getBlockX())),
                Placeholder.unparsed("y", String.valueOf(to.getBlockY())),
                Placeholder.unparsed("z", String.valueOf(to.getBlockZ()))));
        drawTrail(player, delivered.from(), to, delivered.destination().color(settings.get().inspect().colors()));
    }

    // Straight from the source to the frame, so it stays readable in tunnels.
    private void drawTrail(Player player, Location from, Location to, Color color) {
        Vector path = to.toVector().subtract(from.toVector());
        double length = path.length();
        if (length < MIN_TRAIL_LENGTH) {
            return;
        }
        Vector axis = path.multiply(1 / length);
        // Any vector not parallel to the path gives the plane of the ring.
        Vector side = Math.abs(axis.getY()) < NEARLY_VERTICAL ? new Vector(0, 1, 0) : new Vector(1, 0, 0);
        Vector u = axis.getCrossProduct(side).normalize();
        Vector v = axis.getCrossProduct(u);
        Location start = from.clone().add(axis.clone().multiply(Math.min(START_BEYOND_CENTRE, length / 2)));
        Color light = lighter(color);
        int ticks = (int) Math.clamp(Math.round(length / BLOCKS_PER_TICK), MIN_TICKS, MAX_TICKS);
        for (int i = 0; i < RING_TRAILS; i++) {
            double angle = 2 * Math.PI * i / RING_TRAILS;
            Location origin = start.clone()
                    .add(u.clone().multiply(RING_RADIUS * Math.cos(angle)))
                    .add(v.clone().multiply(RING_RADIUS * Math.sin(angle)));
            spawnTrail(player, origin, to, i == 0 ? light : color, ticks + i * STAGGER_TICKS);
        }
        player.getScheduler().runDelayed(plugin, task -> splash(player, to, light), null, ticks);
    }

    private static void splash(Player player, Location at, Color color) {
        if (!player.getWorld().equals(at.getWorld())) {
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < SPLASH; i++) {
            Vector out = new Vector(random.nextGaussian(), random.nextGaussian(), random.nextGaussian());
            if (out.lengthSquared() > 0) {
                spawnTrail(player, at, at.clone().add(out.normalize().multiply(SPLASH_RADIUS)), color, SPLASH_TICKS);
            }
        }
    }

    private static void spawnTrail(Player player, Location from, Location to, Color color, int ticks) {
        player.spawnParticle(Particle.TRAIL, from, 1, 0, 0, 0, 0, new Particle.Trail(to, color, ticks));
    }

    // Halfway to white; Color#mixColors is documented as dyeing, not as a mix.
    private static Color lighter(Color color) {
        return Color.fromRGB((color.getRed() + 255) / 2, (color.getGreen() + 255) / 2, (color.getBlue() + 255) / 2);
    }
}
