package com.cesarcosmico.framesort.integration.bstats;

import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.Plugin;

/**
 * Sends the plugin's usage metrics to bStats, every 30 minutes from bStats' own thread. Created at the end of
 * {@code onEnable}; {@link #stop()}, in {@code onDisable}, ends that thread.
 */
public final class UsageMetrics {

    private final Metrics metrics;

    public UsageMetrics(Plugin plugin, int serviceId) {
        metrics = new Metrics(plugin, serviceId);
    }

    public void stop() {
        metrics.shutdown();
    }
}
