package com.cesarcosmico.framesort.listener;

import com.cesarcosmico.framesort.service.TargetIndex;
import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import io.papermc.paper.event.player.PlayerItemFrameChangeEvent;
import org.bukkit.entity.ItemFrame;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

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

    // MONITOR: only an item that really went in ends the mark; the event is left alone. Every item a player puts in
    // passes here, while an item can leave a frame with no event that confirms it (an arrow, another plugin).
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFrameChange(PlayerItemFrameChangeEvent event) {
        ItemFrame frame = event.getItemFrame();
        if (event.getAction() == PlayerItemFrameChangeEvent.ItemFrameChangeAction.PLACE) {
            index.itemPlaced(frame, event.getPlayer().getUniqueId());
        } else {
            index.changed(frame.getWorld());
        }
    }

    // Hitting a frame (arrows, explosions) knocks its item out without a frame change event.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof ItemFrame frame) {
            index.changed(frame.getWorld());
        }
    }
}
