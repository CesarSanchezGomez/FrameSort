package com.cesarcosmico.framesort.config;

import com.cesarcosmico.framesort.item.ItemTemplate;
import com.cesarcosmico.framesort.model.ItemReference;
import org.jspecify.annotations.Nullable;

/**
 * Items rest on {@code top}, which sits on {@code base}. {@code item} is how FrameSort builds a vanilla top block's
 * item; it is {@code null} for a CraftEngine one, whose item CraftEngine builds with its own look.
 */
public record PadType(String id, ItemReference top, ItemReference base, @Nullable ItemTemplate item) {
}
