package com.cesarcosmico.framesort.integration.worldguard;

import com.cesarcosmico.framesort.api.TargetBindEvent;
import com.cesarcosmico.framesort.service.FrameGeometry;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

// A source only sends to containers in exactly its own set of regions, so nobody can fill a chest in a region from
// outside it, or from another region.
public final class WorldGuardTargetListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBind(TargetBindEvent event) {
        Block source = event.getSource();
        RegionManager regions = WorldGuard.getInstance().getPlatform().getRegionContainer()
                .get(BukkitAdapter.adapt(source.getWorld()));
        if (regions == null) {
            return;
        }
        Block target = FrameGeometry.attachedBlock(event.getTarget());
        if (!regions.getApplicableRegions(BukkitAdapter.asBlockVector(source.getLocation())).getRegions()
                .equals(regions.getApplicableRegions(BukkitAdapter.asBlockVector(target.getLocation())).getRegions())) {
            event.setCancelled(true);
        }
    }
}
