package com.cesarcosmico.framesort.service;

import com.cesarcosmico.framesort.item.PlayerCodec;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.function.Predicate;

/** Whether putting an item in a frame marks it: each player chooses, and marking by hand works either way. */
public final class AutoMarkService {

    private final TargetIndex index;
    private final SorterService sorters;
    // Whether the player may still switch it off: their choice is kept, but does nothing while they can't.
    private final Predicate<Player> available;

    public AutoMarkService(TargetIndex index, SorterService sorters, Predicate<Player> available) {
        this.index = index;
        this.sorters = sorters;
        this.available = available;
    }

    /** Switches {@code player} between marking by hand and marking what they fill; returns whether it is now on. */
    public boolean toggle(Player player) {
        boolean on = !PlayerCodec.autoMark(player);
        PlayerCodec.setAutoMark(player, on);
        return on;
    }

    /**
     * {@code item} is going into {@code frame}, which still holds the old one. Ends the old mark, then marks the frame
     * for {@code player} if they chose to; returns whether it did. Only frames on containers or composters: a frame on
     * a wall or a lava cauldron is marked by hand, so a decorative one never becomes a drop spot or a trash can.
     */
    public boolean itemPlaced(Player player, ItemFrame frame, ItemStack item) {
        index.itemPlaced(frame);
        // Another plugin can empty the item before it goes in.
        if (item.isEmpty() || !PlayerCodec.autoMark(player) || !player.hasPermission(TargetIndex.MARK_PERMISSION)
                || !available.test(player) || !index.markable(frame)
                || !FrameGeometry.isContainer(FrameGeometry.attachedBlock(frame)) || sorters.makesSorter(frame, item)) {
            return false;
        }
        index.mark(frame, player.getUniqueId());
        return true;
    }
}
