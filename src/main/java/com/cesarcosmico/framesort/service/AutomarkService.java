package com.cesarcosmico.framesort.service;

import com.cesarcosmico.framesort.item.PlayerCodec;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
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
    // Whether the player may still switch it off: their choice is kept, but does nothing while they can't.
    private final Predicate<Player> available;

    public AutomarkService(Plugin plugin, TargetIndex index, SorterService sorters, InspectService inspect,
                           CustomItemResolver customItems, Predicate<Player> available) {
        this.plugin = plugin;
        this.index = index;
        this.sorters = sorters;
        this.inspect = inspect;
        this.customItems = customItems;
        this.available = available;
    }

    /** Switches {@code player} between marking by hand and marking what they fill. */
    public Toggle toggle(Player player) {
        if (!player.hasPermission(TargetIndex.MARK_PERMISSION)) {
            return Toggle.CANNOT_MARK;
        }
        boolean on = !PlayerCodec.automark(player);
        PlayerCodec.setAutomark(player, on);
        return on ? Toggle.ON : Toggle.OFF;
    }

    /**
     * {@code item} is going into {@code frame}, which still holds the old one. Ends the old mark, then marks the frame
     * for {@code player} if they chose to, answering as marking by hand does. Only frames on containers or composters:
     * a frame on a wall or a lava cauldron is marked by hand, so a decorative one never becomes a drop spot or a trash
     * can.
     */
    public void itemPlaced(Player player, ItemFrame frame, ItemStack item) {
        index.itemPlaced(frame);
        // Another plugin can empty the item before it goes in.
        if (item.isEmpty() || !PlayerCodec.automark(player) || !player.hasPermission(TargetIndex.MARK_PERMISSION)
                || !available.test(player) || !index.markable(frame)
                || !FrameGeometry.isContainer(FrameGeometry.attachedBlock(frame), customItems)
                || sorters.makesSorter(frame, item)) {
            return;
        }
        index.mark(frame, player.getUniqueId());
        // The glow copies the frame's item, which only goes in after this event.
        player.getScheduler().run(plugin, task -> inspect.showMark(player, frame, true), null);
    }
}
