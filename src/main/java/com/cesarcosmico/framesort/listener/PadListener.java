package com.cesarcosmico.framesort.listener;

import com.cesarcosmico.framesort.config.PadType;
import com.cesarcosmico.framesort.service.PadService;
import com.cesarcosmico.framesort.text.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlacing(BlockPlaceEvent event) {
        switch (pads.placing(event.getBlockPlaced(), event.getItemInHand())) {
            case PadService.Placing.NeedsBase(PadType type, Component base) -> {
                event.setCancelled(true);
                event.getPlayer().sendMessage(messages.get().get("pad.needs-base",
                        Placeholder.unparsed("type", type.id()), Placeholder.component("base", base)));
            }
            case PadService.Placing.NotReady() -> {
                event.setCancelled(true);
                event.getPlayer().sendMessage(messages.get().get("pad.unavailable"));
            }
            case PadService.Placing.Fine() -> {
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        switch (pads.placed(event.getPlayer(), event.getBlockPlaced(), event.getItemInHand())) {
            case PadService.Placement.Created(PadType type) -> event.getPlayer().sendMessage(
                    messages.get().get("pad.created", Placeholder.unparsed("type", type.id())));
            case PadService.Placement.NotAllowed() -> event.getPlayer().sendMessage(
                    messages.get().get("pad.not-allowed"));
            case PadService.Placement.NotAPad() -> {
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        switch (pads.breaking(event.getBlock(), player.getGameMode() != GameMode.CREATIVE)) {
            case PadService.Breaking.Allowed(boolean replacesDrops) -> {
                if (replacesDrops) {
                    event.setDropItems(false);
                }
            }
            case PadService.Breaking.Kept() -> {
                event.setCancelled(true);
                player.sendMessage(messages.get().get("pad.unavailable"));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBroken(BlockBreakEvent event) {
        if (pads.removeBrokenPad(event.getBlock(), !event.isDropItems())) {
            event.getPlayer().sendMessage(messages.get().get("pad.removed"));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (movesPad(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (movesPad(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    // A top block with gravity still falls; the sweep then forgets the pad.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof FallingBlock) && pads.isPadTop(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(pads::isPadTop);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(pads::isPadTop);
    }

    private boolean movesPad(List<Block> blocks) {
        for (Block block : blocks) {
            if (pads.isPadTop(block)) {
                return true;
            }
        }
        return false;
    }
}
