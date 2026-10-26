package com.cesarcosmico.framesort.config;

import com.cesarcosmico.framesort.item.ItemLook;
import com.cesarcosmico.framesort.model.ItemReference;

/** {@code range} and {@code pullRadius} are in blocks; a {@code pullRadius} of 0 pulls nothing. */
public record PadType(String id, ItemReference top, ItemReference base, int range, int pullRadius,
                      boolean takesFromInventory, ItemLook look) {
}
