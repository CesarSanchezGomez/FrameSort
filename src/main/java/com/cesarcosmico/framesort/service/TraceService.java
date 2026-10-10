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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/** With nobody tracing, a delivery costs one empty-map check. */
public final class TraceService {

    private record Shown(long millis, int tick, Location from) {
    }

    // At most one delivery per player this often, so a busy sorter does not flood the screen. Every part of the one
    // that is shown (the containers, then the drop) still shows: a part is the same source in the same tick.
    private static final long MIN_INTERVAL_MILLIS = 150;
    // The trail runs straight from the source to the frame, so it stays readable in tunnels.
    private static final int STREAK = 6;
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
    // Keyed by the player, not the UUID: the retired callback of start() drops them when they leave.
    private final Map<Player, Long> until = new HashMap<>();
    private final Map<Player, Shown> lastShown = new HashMap<>();

    public TraceService(Plugin plugin, Supplier<FrameSortSettings> settings, Supplier<Messages> messages) {
        this.plugin = plugin;
        this.server = plugin.getServer();
        this.settings = settings;
        this.messages = messages;
    }

    public int start(Player player, int seconds) {
        int granted = Math.clamp(seconds, 1, settings.get().inspect().traceMaxSeconds());
        long deadline = System.currentTimeMillis() + granted * 1000L;
        until.put(player, deadline);
        // A restart or stop changes the deadline, so a stale run does nothing.
        player.getScheduler().runDelayed(plugin, task -> {
            if (until.remove(player, deadline)) {
                lastShown.remove(player);
                player.sendMessage(messages.get().get("trace.ended"));
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
                player.sendMessage(messages.get().get("trace.ended"));
                continue;
            }
            Location from = delivered.from();
            if (!player.getWorld().equals(from.getWorld())
                    || player.getLocation().distanceSquared(from) > (double) radius * radius) {
                continue;
            }
            Shown last = lastShown.get(player);
            int tick = server.getCurrentTick();
            boolean sameDelivery = last != null && last.tick() == tick && last.from().equals(from);
            if (!sameDelivery) {
                if (last != null && now - last.millis() < MIN_INTERVAL_MILLIS) {
                    continue;
                }
                lastShown.put(player, new Shown(now, tick, from));
            }
            show(player, delivered);
        }
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

    private void drawTrail(Player player, Location from, Location to, Color color) {
        Vector path = to.toVector().subtract(from.toVector());
        double length = path.length();
        if (length < 1e-3) {
            return;
        }
        Vector axis = path.multiply(1 / length);
        // Any vector not parallel to the path gives the plane of the ring.
        Vector side = Math.abs(axis.getY()) < 0.9 ? new Vector(0, 1, 0) : new Vector(1, 0, 0);
        Vector u = axis.getCrossProduct(side).normalize();
        Vector v = axis.getCrossProduct(u);
        // From the source's face towards the frame: inside the block nobody would see the start.
        Location start = from.clone().add(axis.clone().multiply(Math.min(0.55, length / 2)));
        Color light = lighter(color);
        int ticks = (int) Math.clamp(Math.round(length / BLOCKS_PER_TICK), MIN_TICKS, MAX_TICKS);
        for (int i = 0; i < STREAK; i++) {
            double angle = 2 * Math.PI * i / STREAK;
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
