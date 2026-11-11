package com.cesarcosmico.framesort.service;

import com.cesarcosmico.framesort.item.PlayerCodec;
import org.bukkit.World;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.function.Predicate;

public final class AutomarkService {

    /** What switching did; {@code CANNOT_MARK}: the player may not mark targets, so automark would do nothing. */
    public enum Toggle { ON, OFF, CANNOT_MARK }

    private final Plugin plugin;
    private final TargetIndex index;
    private final SorterService sorters;
    private final InspectService inspect;
    private final CustomItemResolver customItems;
    private final Predicate<Player> maySwitchOff;
    private final Predicate<World> worksIn;

    public AutomarkService(Plugin plugin, TargetIndex index, SorterService sorters, InspectService inspect,
                           CustomItemResolver customItems, Predicate<Player> maySwitchOff, Predicate<World> worksIn) {
        this.plugin = plugin;
        this.index = index;
        this.sorters = sorters;
        this.inspect = inspect;
        this.customItems = customItems;
        this.maySwitchOff = maySwitchOff;
        this.worksIn = worksIn;
    }

    public Toggle toggle(Player player) {
        if (!player.hasPermission(TargetIndex.MARK_PERMISSION)) {
            return Toggle.CANNOT_MARK;
        }
        boolean on = !PlayerCodec.automark(player);
        PlayerCodec.setAutomark(player, on);
        return on ? Toggle.ON : Toggle.OFF;
    }

    /**
     * {@code item} is going into {@code frame}, which still holds the old one. Only a frame on a container or a
     * composter is automarked, so a decorative one never becomes a drop spot or a trash can.
     */
    public void updateMark(Player player, ItemFrame frame, ItemStack item) {
        index.endMark(frame);
        if (!shouldAutomark(player, frame, item)) {
            return;
        }
        index.mark(frame, player.getUniqueId());
        // The glow copies the frame's item, which only goes in after this event.
        player.getScheduler().run(plugin, task -> inspect.showMark(player, frame, true), null);
    }

    public void endMarkOnTakeOut(Player player, ItemFrame frame) {
        if (index.endMark(frame) && worksIn.test(frame.getWorld()) && isHitByHand(frame)) {
            inspect.tellMark(player, false);
        }
    }

    // The hit taking the item out is already the frame's last damage cause; a player's arrow or lit TNT is indirect.
    private static boolean isHitByHand(ItemFrame frame) {
        EntityDamageEvent hit = frame.getLastDamageCause();
        return hit != null && !hit.getDamageSource().isIndirect();
    }

    // Another plugin can empty the item before it goes in.
    private boolean shouldAutomark(Player player, ItemFrame frame, ItemStack item) {
        return !item.isEmpty() && worksIn.test(frame.getWorld()) && usesAutomark(player)
                && makesTarget(player, frame, item);
    }

    private boolean usesAutomark(Player player) {
        return PlayerCodec.automark(player) && player.hasPermission(TargetIndex.MARK_PERMISSION)
                && maySwitchOff.test(player);
    }

    private boolean makesTarget(Player player, ItemFrame frame, ItemStack item) {
        return index.markable(frame) && TargetBlocks.isContainer(FrameGeometry.attachedBlock(frame), customItems)
                && !sorters.makesSorter(frame, item) && inspect.isInReachFor(player, frame);
    }
}
