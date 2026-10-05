package com.cesarcosmico.framesort.listener;

import com.cesarcosmico.framesort.config.FrameSortSettings;
import com.cesarcosmico.framesort.service.InspectService;
import com.cesarcosmico.framesort.service.PadService;
import com.cesarcosmico.framesort.service.SorterService;
import com.cesarcosmico.framesort.service.TraceService;
import org.bukkit.block.Block;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.function.Supplier;

/** Empty frames are left alone, so the tool item can still be put into a frame. */
public final class ToolListener implements Listener {

    public static final String PERMISSION = "framesort.inspect";

    private final Supplier<FrameSortSettings> settings;
    private final InspectService inspect;
    private final SorterService sorters;
    private final PadService pads;
    private final TraceService trace;

    public ToolListener(Supplier<FrameSortSettings> settings, InspectService inspect, SorterService sorters,
                        PadService pads, TraceService trace) {
        this.settings = settings;
        this.inspect = inspect;
        this.sorters = sorters;
        this.pads = pads;
        this.trace = trace;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        Player player = event.getPlayer();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || block == null || !holdsTool(player)) {
            return;
        }
        boolean sorter = sorters.isSorter(block);
        boolean pad = !sorter && pads.padAt(block) != null;
        if (!sorter && !pad) {
            return;
        }
        // Cancel both hands, or the off-hand item would be placed or used after the inspection.
        event.setCancelled(true);
        if (event.getHand() == EquipmentSlot.HAND) {
            inspect.inspectSource(player, block, pad, player.getInventory().getItemInOffHand());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractFrame(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (!(event.getRightClicked() instanceof ItemFrame frame) || frame.getItem().isEmpty() || !holdsTool(player)) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (player.isSneaking()) {
            inspect.toggleMark(player, frame);
        } else {
            inspect.inspectFrame(player, frame);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        inspect.forget(event.getPlayer().getUniqueId());
        trace.forget(event.getPlayer().getUniqueId());
    }

    private boolean holdsTool(Player player) {
        return player.getInventory().getItemInMainHand().getType() == settings.get().inspect().tool()
                && player.hasPermission(PERMISSION);
    }
}
