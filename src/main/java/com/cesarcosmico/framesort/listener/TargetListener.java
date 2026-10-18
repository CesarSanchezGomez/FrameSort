package com.cesarcosmico.framesort.listener;

import com.cesarcosmico.framesort.service.TargetIndex;
import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import io.papermc.paper.event.player.PlayerItemFrameChangeEvent;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;

public final class TargetListener implements Listener {

    private final TargetIndex index;

    public TargetListener(TargetIndex index) {
        this.index = index;
    }

    @EventHandler
    public void onAdd(EntityAddToWorldEvent event) {
        if (event.getEntity() instanceof ItemFrame frame) {
            index.add(frame);
        }
    }

    @EventHandler
    public void onRemove(EntityRemoveFromWorldEvent event) {
        if (event.getEntity() instanceof ItemFrame frame) {
            index.remove(frame);
        }
    }

    // MONITOR: only a frame that stays hung gets an owner; the event is left alone.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(HangingPlaceEvent event) {
        if (event.getEntity() instanceof ItemFrame frame && event.getPlayer() instanceof Player player) {
            index.hung(frame, player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFrameChange(PlayerItemFrameChangeEvent event) {
        index.changed(event.getItemFrame().getWorld());
    }

    // Hitting a frame (arrows, explosions) knocks its item out without a frame change event.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof ItemFrame frame) {
            index.changed(frame.getWorld());
        }
    }
}
