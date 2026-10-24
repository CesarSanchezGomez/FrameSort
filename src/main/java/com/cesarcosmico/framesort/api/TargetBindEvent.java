package com.cesarcosmico.framesort.api;

import org.bukkit.block.Block;
import org.bukkit.entity.ItemFrame;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.EntityEvent;

/**
 * Fired when FrameSort considers an item frame as a target for a sorter or sorting pad. Cancel it to keep that
 * source from sending items to the frame, for example when the frame is inside a claim you protect.
 *
 * <p>It only fires for a frame the source may send to by owner: with ownership on, a frame of the source's owner or
 * of a player that owner trusts.</p>
 *
 * <p>Results are cached, so the event fires again only after targets near the source change, or after a change of
 * trust.</p>
 */
public final class TargetBindEvent extends EntityEvent implements Cancellable {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final Block source;
    private boolean cancelled;

    public TargetBindEvent(Block source, ItemFrame target) {
        super(target);
        this.source = source;
    }

    /** The sorter's container or the pad's top block. */
    public Block getSource() {
        return source;
    }

    /** The candidate target frame. */
    public ItemFrame getTarget() {
        return (ItemFrame) getEntity();
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
