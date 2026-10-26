package com.cesarcosmico.framesort.listener;

import com.cesarcosmico.framesort.service.AutomarkService;
import com.cesarcosmico.framesort.service.TargetIndex;
import com.cesarcosmico.framesort.service.TargetResolver;
import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import io.papermc.paper.event.player.PlayerItemFrameChangeEvent;
import org.bukkit.entity.ItemFrame;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldUnloadEvent;

public final class TargetListener implements Listener {

    private final TargetIndex index;
    private final AutomarkService automark;
    private final TargetResolver resolver;

    public TargetListener(TargetIndex index, AutomarkService automark, TargetResolver resolver) {
        this.index = index;
        this.automark = automark;
        this.resolver = resolver;
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
            automark.updateMark(event.getPlayer(), event.getItemFrame(), event.getItemStack());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        resolver.forget(event.getWorld());
    }
}
