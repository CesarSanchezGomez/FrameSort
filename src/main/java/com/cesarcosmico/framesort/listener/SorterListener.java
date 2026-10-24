package com.cesarcosmico.framesort.listener;

import com.cesarcosmico.framesort.service.SorterService;
import com.cesarcosmico.framesort.text.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

import java.util.List;
import java.util.function.Supplier;

public final class SorterListener implements Listener {

    private final SorterService sorters;
    private final Supplier<Messages> messages;

    public SorterListener(SorterService sorters, Supplier<Messages> messages) {
        this.sorters = sorters;
        this.messages = messages;
    }

    @EventHandler
    public void onLoad(ChunkLoadEvent event) {
        sorters.load(event.getChunk());
    }

    @EventHandler
    public void onUnload(ChunkUnloadEvent event) {
        sorters.unload(event.getChunk());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        sorters.discover(event.getPlayer());
    }

    // MONITOR: the sorter is registered only once no plugin can still cancel the placement; the event is left alone.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (sorters.placed(event.getPlayer(), event.getBlockPlaced(), event.getItemInHand())) {
            event.getPlayer().sendMessage(messages.get().get("sorter.created"));
        }
    }

    // MONITOR: only a break no plugin cancelled ends the sorter.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (sorters.broken(event.getBlock())) {
            event.getPlayer().sendMessage(messages.get().get("sorter.removed"));
        }
    }

    // LOWEST: a plugin that picks up drops listens later and must get the sorter item; a later cancellation drops none.
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDrop(BlockDropItemEvent event) {
        sorters.markDrop(event.getBlockState(), event.getItems());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (movesSorter(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (movesSorter(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    // A wither breaks blocks without any break or drop event, which would lose the sorter.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (sorters.endsSorter(event.getBlock(), event.getTo())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(sorters::isSorter);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(sorters::isSorter);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        if (sorters.isSorter(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    // Only the title the player sees changes: the container keeps its own name, for when it stops sorting.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (event.getInventory().getHolder(false) instanceof Container container) {
            Component title = sorters.title(container.getBlock());
            if (title != null) {
                event.titleOverride(title);
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder(false) instanceof Container container) {
            sorters.wake(container.getBlock());
        }
    }

    private boolean movesSorter(List<Block> blocks) {
        for (Block block : blocks) {
            if (sorters.isSorter(block)) {
                return true;
            }
        }
        return false;
    }
}
