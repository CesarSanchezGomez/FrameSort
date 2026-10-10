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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/** With nobody tracing, a delivery costs one empty-map check. */
public final class TraceService {

    // At most one action bar per player this often, so a busy sorter does not flood it.
    private static final long MIN_INTERVAL_MILLIS = 150;
    // As in the creaking heart, each trail particle gets its own start, end and travel time; the different speeds
    // spread them along the path instead of flying as one clump.
    private static final int TRAIL_PARTICLES = 12;
    private static final int MIN_TRAIL_TICKS = 10;
    private static final int MAX_TRAIL_TICKS = 49;

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
        drawTrail(player, delivered.from(), to, delivered.kind().color(settings.get().inspect().colors()));
    }

    private static void drawTrail(Player player, Location from, Location to, Color color) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < TRAIL_PARTICLES; i++) {
            Location start = jitter(from, 0.5, random);
            Particle.Trail trail = new Particle.Trail(jitter(to, 0.25, random), color,
                    random.nextInt(MIN_TRAIL_TICKS, MAX_TRAIL_TICKS + 1));
            player.spawnParticle(Particle.TRAIL, start, 1, 0, 0, 0, 0, trail);
        }
    }

    private static Location jitter(Location center, double spread, ThreadLocalRandom random) {
        return center.clone().add(random.nextDouble(-spread, spread), random.nextDouble(-spread, spread),
                random.nextDouble(-spread, spread));
    }
}
