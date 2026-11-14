package com.cesarcosmico.framesort.listener;

import com.cesarcosmico.framesort.config.PadType;
import com.cesarcosmico.framesort.service.HighlightService;
import com.cesarcosmico.framesort.service.InspectService;
import com.cesarcosmico.framesort.service.PadService;
import com.cesarcosmico.framesort.service.SorterService;
import io.papermc.paper.event.player.PlayerItemFrameChangeEvent;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.function.Predicate;

public final class InspectListener implements Listener {

    private final InspectService inspect;
    private final SorterService sorters;
    private final PadService pads;
    private final HighlightService highlights;
    private final Predicate<World> worksIn;

    public InspectListener(InspectService inspect, SorterService sorters, PadService pads,
                           HighlightService highlights, Predicate<World> worksIn) {
        this.inspect = inspect;
        this.sorters = sorters;
        this.pads = pads;
        this.highlights = highlights;
        this.worksIn = worksIn;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        highlights.clear(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        Player player = event.getPlayer();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || block == null || !inspects(player)) {
            return;
        }
        boolean sorter = sorters.isSorter(block);
        PadType pad = sorter ? null : pads.padAt(block);
        if (!sorter && pad == null) {
            return;
        }
        // Cancel both hands, or the off-hand item would be placed or used after the inspection.
        event.setCancelled(true);
        if (event.getHand() == EquipmentSlot.HAND) {
            inspect.inspectSource(player, block, pad, player.getInventory().getItemInOffHand());
        }
    }

    // The frame's own events: the server fires them only if nothing cancelled the click or the hit, even at MONITOR.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFrameGesture(PlayerItemFrameChangeEvent event) {
        Player player = event.getPlayer();
        ItemFrame frame = event.getItemFrame();
        boolean answered = switch (event.getAction()) {
            case ROTATE -> answers(player, frame) && inspect.markOrInspectSorter(player, frame);
            case REMOVE -> InspectService.isInspectHit(player, frame) && worksIn.test(player.getWorld())
                    && inspect.inspectFrameOrSorter(player, frame);
            case PLACE -> false;
        };
        if (answered) {
            event.setCancelled(true);
        }
    }

    private boolean answers(Player player, ItemFrame frame) {
        return !frame.getItem().isEmpty() && inspects(player);
    }

    private boolean inspects(Player player) {
        return InspectService.isInspectGesture(player) && worksIn.test(player.getWorld());
    }
}
