package com.cesarcosmico.framesort.integration.worldguard;

import com.cesarcosmico.framesort.api.PadPullEvent;
import com.cesarcosmico.framesort.api.TargetBindEvent;
import com.cesarcosmico.framesort.service.FrameGeometry;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.config.WorldConfiguration;
import com.sk89q.worldguard.protection.association.DelayedRegionOverlapAssociation;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.List;
import java.util.logging.Logger;

public final class WorldGuardListener implements Listener {

    private static final String DELIVER = "framesort-deliver";
    private static final String PULL = "framesort-pull";

    private final StateFlag[] deliverFlags = registered(DELIVER);
    private final StateFlag[] pullFlags = registered(PULL);

    public static void registerFlags(Logger logger) {
        FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
        for (String name : List.of(DELIVER, PULL)) {
            try {
                // false gives no default value, not deny, so an unset flag leaves it to build.
                registry.register(new StateFlag(name, false));
            } catch (FlagConflictException e) {
                logger.warning("Another plugin already registered the WorldGuard flag " + name
                        + "; FrameSort uses it if it is an allow/deny flag, or else only build.");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBind(TargetBindEvent event) {
        if (!allows(event.getSource(), FrameGeometry.attachedBlock(event.getTarget()).getLocation(), deliverFlags)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPull(PadPullEvent event) {
        if (!allows(event.getPad(), event.getItem().getLocation(), pullFlags)) {
            event.setCancelled(true);
        }
    }

    private static boolean allows(Block source, Location target, StateFlag[] flags) {
        WorldConfiguration worldConfig = WorldGuard.getInstance().getPlatform().getGlobalStateManager()
                .get(BukkitAdapter.adapt(source.getWorld()));
        // With regions off, WorldGuard answers build with deny and skips its own checks.
        if (!worldConfig.useRegions) {
            return true;
        }
        RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
        DelayedRegionOverlapAssociation sourceRegions = new DelayedRegionOverlapAssociation(query,
                BukkitAdapter.adapt(source.getLocation()), worldConfig.useMaxPriorityAssociation);
        return query.testBuild(BukkitAdapter.adapt(target), sourceRegions, flags);
    }

    private static StateFlag[] registered(String name) {
        return WorldGuard.getInstance().getFlagRegistry().get(name) instanceof StateFlag flag
                ? new StateFlag[] {flag}
                : new StateFlag[0];
    }
}
