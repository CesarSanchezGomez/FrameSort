package com.cesarcosmico.framesort.api;

import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.EntityEvent;

/**
 * Fired when a pad is about to pull a loose item towards it. Cancel it to leave the item where it is, for example when
 * it lies inside a claim you protect.
 *
 * <p>It fires only for an item that FrameSort would pull: lying on the ground within its type's {@code pull-radius}
 * (pads.yml), outside the pad's own column, not dropped by FrameSort itself, not on another pad, and not thrown by
 * another pad on the same check; whose flight onto the pad its rays find clear. From further away, that is an arc
 * that rises a block over the pad and over the item, takes at most two seconds and lands softly enough to stay on the
 * pad, with rays through its top and where it comes over the pad's edge; from within half a block of the pad's side
 * along both axes, a hop straight up with free room above the item, then a drift onto the pad.
 * That is at most once per item and pad on every pad check ({@code sweep-interval} in pads.yml).</p>
 */
public final class PadPullEvent extends EntityEvent implements Cancellable {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final Block pad;
    private boolean cancelled;

    /** Only FrameSort creates and fires this event. */
    public PadPullEvent(Item item, Block pad) {
        super(item);
        this.pad = pad;
    }

    /** The pad's top block. */
    public Block getPad() {
        return pad;
    }

    /** The item the pad would pull. */
    public Item getItem() {
        return (Item) getEntity();
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }
}
