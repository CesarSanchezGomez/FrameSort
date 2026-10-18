package com.cesarcosmico.framesort.listener;

import com.cesarcosmico.framesort.service.SorterService;
import com.cesarcosmico.framesort.text.Messages;
import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import io.papermc.paper.event.player.PlayerItemFrameChangeEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.block.Dispenser;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;

import java.util.UUID;
import java.util.function.Supplier;

public final class SorterListener implements Listener {

    private final SorterService sorters;
    private final Supplier<Messages> messages;

    public SorterListener(SorterService sorters, Supplier<Messages> messages) {
        this.sorters = sorters;
        this.messages = messages;
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
        if (event.getEntity() instanceof ItemFrame frame && sorters.broken(frame)
                && event instanceof HangingBreakByEntityEvent byEntity
                && byEntity.getRemover() instanceof Player player) {
            tell(player, SorterService.Change.REMOVED);
        }
    }

    // MONITOR: only a break no plugin cancelled ends the sorter; its frame then falls and is forgotten.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (sorters.isSorter(event.getBlock())) {
            tell(event.getPlayer(), SorterService.Change.REMOVED);
        }
    }

    // MONITOR: the frame is re-checked only once the change is final; the event is left alone.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFrameChange(PlayerItemFrameChangeEvent event) {
        Player player = event.getPlayer();
        UUID placer = event.getAction() == PlayerItemFrameChangeEvent.ItemFrameChangeAction.PLACE
                ? player.getUniqueId() : null;
        sorters.considerLater(event.getItemFrame(), placer, change -> tell(player, change));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        if (sorters.isSorter(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    // Only the title the player sees changes: the dispenser keeps its own name, for when it stops sorting.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (event.getInventory().getHolder(false) instanceof Dispenser dispenser) {
            Component title = sorters.title(dispenser.getBlock());
            if (title != null) {
                event.titleOverride(title);
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder(false) instanceof Dispenser dispenser) {
            sorters.wake(dispenser.getBlock());
        }
    }

    private void tell(Player player, SorterService.Change change) {
        switch (change) {
            case CREATED -> player.sendMessage(messages.get().get("sorter.created"));
            case REMOVED -> player.sendMessage(messages.get().get("sorter.removed"));
            case UNCHANGED -> {
            }
        }
    }
}
