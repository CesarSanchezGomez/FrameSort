package com.cesarcosmico.framesort.listener;

import com.cesarcosmico.framesort.service.PadService;
import com.cesarcosmico.framesort.text.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.function.Supplier;

public final class PadListener implements Listener {

    private final PadService pads;
    private final Supplier<Messages> messages;

    public PadListener(PadService pads, Supplier<Messages> messages) {
        this.pads = pads;
        this.messages = messages;
    }

    @EventHandler
    public void onLoad(ChunkLoadEvent event) {
        pads.load(event.getChunk());
    }

    @EventHandler
    public void onUnload(ChunkUnloadEvent event) {
        pads.unload(event.getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        PadService.PlaceResult result = pads.placed(event.getPlayer(), event.getBlockPlaced(), event.getItemInHand());
        switch (result.placement()) {
            case CREATED -> event.getPlayer().sendMessage(messages.get().get("pad.created",
                    Placeholder.unparsed("type", result.type() == null ? "" : result.type().id())));
            case NOT_ALLOWED -> event.getPlayer().sendMessage(messages.get().get("pad.not-allowed"));
            case NOT_A_PAD -> {
            }
        }
    }

    // HIGHEST, not MONITOR: it changes the drops. Protection plugins cancel earlier, and cancelled breaks are skipped.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!pads.isRegistered(block)) {
            return;
        }
        ItemStack special = pads.broken(block);
        if (special != null && event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            event.setDropItems(false);
            block.getWorld().dropItemNaturally(block.getLocation().toCenterLocation(), special);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (movesPad(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (movesPad(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(pads::isRegistered);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(pads::isRegistered);
    }

    private boolean movesPad(List<Block> blocks) {
        for (Block block : blocks) {
            if (pads.isRegistered(block)) {
                return true;
            }
        }
        return false;
    }
}
