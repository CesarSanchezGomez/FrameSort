package com.cesarcosmico.framesort.service;

import com.cesarcosmico.framesort.config.ActivitySettings;
import com.cesarcosmico.framesort.config.FrameSortSettings;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;

import java.util.function.Supplier;

/** The burst a sorter or pad gives off when it sends, so players near it see it working. */
public final class ActivityPulse {

    private static final int COUNT = 10;
    private static final double SPREAD = 0.25;
    private static final float SIZE = 1.0f;

    private final Supplier<FrameSortSettings> settings;

    public ActivityPulse(Supplier<FrameSortSettings> settings) {
        this.settings = settings;
    }

    // The world comes apart: a Location may have none, while both callers have the block's or the frame's.
    public void show(World world, Location center) {
        ActivitySettings colors = settings.get().activity();
        world.spawnParticle(Particle.DUST_COLOR_TRANSITION, center, COUNT, SPREAD, SPREAD, SPREAD, 0,
                new Particle.DustTransition(colors.from(), colors.to(), SIZE));
    }
}
