package com.cesarcosmico.framesort.listener;

import com.cesarcosmico.framesort.service.AutomarkService;
import com.cesarcosmico.framesort.service.TargetIndex;
import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import io.papermc.paper.event.player.PlayerItemFrameChangeEvent;
import org.bukkit.entity.ItemFrame;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public final class TargetListener implements Listener {

    private final TargetIndex index;
    private final AutomarkService automark;

    public TargetListener(TargetIndex index, AutomarkService automark) {
        this.index = index;
        this.automark = automark;
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFrameChange(PlayerItemFrameChangeEvent event) {
        if (event.getAction() == PlayerItemFrameChangeEvent.ItemFrameChangeAction.PLACE) {
            automark.itemPlaced(event.getPlayer(), event.getItemFrame(), event.getItemStack());
        }
    }
}
