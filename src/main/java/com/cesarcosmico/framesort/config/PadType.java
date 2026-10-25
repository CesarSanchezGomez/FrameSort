package com.cesarcosmico.framesort.config;

import com.cesarcosmico.framesort.item.ItemLook;
import com.cesarcosmico.framesort.model.ItemReference;

/**
 * Items rest on {@code top}, which sits on {@code base}; they go to targets within {@code range} blocks, and the pad
 * pulls loose items within {@code pullRadius} (0: none). With {@code takesFromInventory} it also sends the main
 * inventory of a player crouching on it. The pad item is the top block's item with {@code look}: built by FrameSort
 * for a vanilla block, or by CraftEngine with {@code look} laid over it.
 */
public record PadType(String id, ItemReference top, ItemReference base, int range, int pullRadius,
                      boolean takesFromInventory, ItemLook look) {
}
