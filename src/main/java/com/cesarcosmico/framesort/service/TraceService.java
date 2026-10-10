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
import java.util.UUID;
import java.util.function.Supplier;

/** With nobody tracing, a delivery costs one empty-map check. */
public final class TraceService {

    // At most one action bar per player this often, so a busy sorter does not flood it.
    private static final long MIN_INTERVAL_MILLIS = 150;
    // A straight dotted line drawn at once: it reads in tunnels, and long paths spread the points out instead of
    // adding more.
    private static final double POINT_SPACING = 0.35;
    private static final int MAX_POINTS = 60;
    private static final float DUST_SIZE = 0.6f;

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
        drawLine(player, delivered.from(), to, delivered.kind().color(settings.get().inspect().colors()));
    }

    private static void drawLine(Player player, Location from, Location to, Color color) {
        Particle.DustOptions[] tones = {new Particle.DustOptions(color, DUST_SIZE),
                new Particle.DustOptions(lighter(color), DUST_SIZE)};
        int points = Math.clamp((long) Math.ceil(from.distance(to) / POINT_SPACING) + 1, 2, MAX_POINTS);
        Vector step = to.toVector().subtract(from.toVector()).multiply(1.0 / (points - 1));
        Location point = from.clone();
        for (int i = 0; i < points; i++) {
            player.spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, tones[i % 2]);
            point.add(step);
        }
    }

    // Halfway to white; Color#mixColors is documented as dyeing, not as a mix.
    private static Color lighter(Color color) {
        return Color.fromRGB((color.getRed() + 255) / 2, (color.getGreen() + 255) / 2, (color.getBlue() + 255) / 2);
    }
}
