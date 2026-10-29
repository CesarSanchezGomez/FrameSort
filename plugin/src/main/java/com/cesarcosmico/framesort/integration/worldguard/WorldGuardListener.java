package com.cesarcosmico.framesort.integration.worldguard;

import com.cesarcosmico.framesort.api.PadPullEvent;
import com.cesarcosmico.framesort.api.TargetBindEvent;
import com.cesarcosmico.framesort.service.FrameGeometry;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public final class WorldGuardListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBind(TargetBindEvent event) {
        if (!sameRegions(event.getSource(), FrameGeometry.attachedBlock(event.getTarget()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPull(PadPullEvent event) {
        if (!sameRegions(event.getPad(), event.getItem().getLocation().getBlock())) {
            event.setCancelled(true);
        }
    }

    private static boolean sameRegions(Block source, Block other) {
        RegionManager regions = WorldGuard.getInstance().getPlatform().getRegionContainer()
                .get(BukkitAdapter.adapt(source.getWorld()));
        return regions == null
                || regions.getApplicableRegions(BukkitAdapter.asBlockVector(source.getLocation())).getRegions()
                .equals(regions.getApplicableRegions(BukkitAdapter.asBlockVector(other.getLocation())).getRegions());
    }
}
