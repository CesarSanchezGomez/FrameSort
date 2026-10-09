package com.cesarcosmico.framesort.listener;

import com.cesarcosmico.framesort.service.SorterService;
import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import io.papermc.paper.event.player.PlayerItemFrameChangeEvent;
import org.bukkit.block.Dispenser;
import org.bukkit.entity.ItemFrame;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

public final class SorterListener implements Listener {

    private final SorterService sorters;

    public SorterListener(SorterService sorters) {
        this.sorters = sorters;
    }

    @EventHandler
    public void onAdd(EntityAddToWorldEvent event) {
        if (event.getEntity() instanceof ItemFrame frame) {
            sorters.consider(frame);
        }
    }

    @EventHandler
    public void onRemove(EntityRemoveFromWorldEvent event) {
        if (event.getEntity() instanceof ItemFrame frame) {
            sorters.forget(frame);
        }
    }

    // MONITOR: the sorter is forgotten only once the break is final; the event is left alone.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(HangingBreakEvent event) {
        if (event.getEntity() instanceof ItemFrame frame) {
            sorters.broken(frame);
        }
    }

    // MONITOR: the frame is re-checked only once the change is final; the event is left alone.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFrameChange(PlayerItemFrameChangeEvent event) {
        sorters.considerLater(event.getItemFrame());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        if (sorters.isSorter(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder(false) instanceof Dispenser dispenser) {
            sorters.wake(dispenser.getBlock());
        }
    }
}
