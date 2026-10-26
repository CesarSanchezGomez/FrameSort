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
 * <p>It fires for each frame in the source's range that could take items from it:</p>
 * <ul>
 *     <li>when a delivery finds no cached result for that source, its owner and that item. The cache is emptied when
 *     targets near the source change, when trust changes, on {@code /framesort reload}, and when it reaches 4096
 *     entries;</li>
 *     <li>every time a player inspects the source, or turns a page of that inspection;</li>
 *     <li>when a player inspects a frame: once for each source in range of it.</li>
 * </ul>
 *
 * <p>So it can fire many times a second on a busy server: keep a listener cheap.</p>
 */
public final class TargetBindEvent extends EntityEvent implements Cancellable {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final Block source;
    private boolean cancelled;

    /** Only FrameSort creates and fires this event. */
    public TargetBindEvent(Block source, ItemFrame target) {
        super(target);
        this.source = source;
    }

    /** The sorter's dispenser or the pad's top block. */
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
