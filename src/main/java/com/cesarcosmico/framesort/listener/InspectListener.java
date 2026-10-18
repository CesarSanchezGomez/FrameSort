package com.cesarcosmico.framesort.listener;

import com.cesarcosmico.framesort.service.InspectService;
import com.cesarcosmico.framesort.service.PadService;
import com.cesarcosmico.framesort.service.SorterService;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.jspecify.annotations.Nullable;

// Inspecting needs sneaking with an empty main hand, so every other click stays vanilla.
public final class InspectListener implements Listener {

    private static final String PERMISSION = "framesort.inspect";

    private final InspectService inspect;
    private final SorterService sorters;
    private final PadService pads;

    public InspectListener(InspectService inspect, SorterService sorters, PadService pads) {
        this.inspect = inspect;
        this.sorters = sorters;
        this.pads = pads;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        Player player = event.getPlayer();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || block == null || !inspects(player)) {
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

    // Cancelled for both hands, so the frame never rotates its item. Empty frames are left alone: a mark lives with the
    // item, and the click can still put the off-hand item in.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMark(PlayerInteractAtEntityEvent event) {
        Player player = event.getPlayer();
        if (!(event.getRightClicked() instanceof ItemFrame frame) || frame.getItem().isEmpty() || !inspects(player)) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() == EquipmentSlot.HAND && !inspectSorterOf(player, frame)) {
            inspect.toggleMark(player, frame);
        }
    }

    // A hit drops the frame's item before PrePlayerAttackEntityEvent fires; this event comes first and, cancelled,
    // keeps the item in the frame.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInspectFrame(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        ItemFrame frame = inspectedFrame(player, event.getEntity());
        if (frame == null) {
            return;
        }
        event.setCancelled(true);
        if (!inspectSorterOf(player, frame)) {
            inspect.inspectFrame(player, frame);
        }
    }

    // A sorter's frame covers a face of its dispenser, so clicking it inspects the sorter: it is never a target.
    private boolean inspectSorterOf(Player player, ItemFrame frame) {
        Block sorter = sorters.blockOf(frame);
        if (sorter != null) {
            inspect.inspectSource(player, sorter, false, player.getInventory().getItemInOffHand());
        }
        return sorter != null;
    }

    // Empty frames are left alone, so they can still be filled or broken while sneaking.
    private @Nullable ItemFrame inspectedFrame(Player player, Entity entity) {
        return entity instanceof ItemFrame frame && !frame.getItem().isEmpty() && inspects(player) ? frame : null;
    }

    private static boolean inspects(Player player) {
        return player.isSneaking() && player.getInventory().getItemInMainHand().isEmpty()
                && player.hasPermission(PERMISSION);
    }
}
