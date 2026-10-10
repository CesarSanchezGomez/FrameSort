package com.cesarcosmico.framesort.config;

import com.cesarcosmico.framesort.item.ItemTemplate;
import org.bukkit.Material;

/** Items rest on {@code top}, which sits on {@code base}. */
public record PadType(String id, Material top, Material base, ItemTemplate item) {
}
