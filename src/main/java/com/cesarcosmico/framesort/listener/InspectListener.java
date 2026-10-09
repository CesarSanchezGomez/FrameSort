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
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
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

    // Cancelled for both hands, so the frame never rotates its item.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMark(PlayerInteractEntityEvent event) {
        ItemFrame frame = inspectedFrame(event.getPlayer(), event.getRightClicked());
        if (frame == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() == EquipmentSlot.HAND) {
            inspect.toggleMark(event.getPlayer(), frame);
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
        inspect.inspectFrame(player, frame);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        inspect.forget(event.getPlayer().getUniqueId());
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
