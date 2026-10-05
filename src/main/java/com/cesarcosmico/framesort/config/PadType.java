package com.cesarcosmico.framesort.config;

import com.cesarcosmico.framesort.item.ItemTemplate;
import org.bukkit.Material;

import java.util.List;

/** {@code structure} goes top to bottom; items rest on the first block. */
public record PadType(String id, List<Material> structure, ItemTemplate item) {

    public PadType {
        structure = List.copyOf(structure);
        if (structure.isEmpty()) {
            throw new IllegalArgumentException("a pad needs at least one block");
        }
    }

    public Material top() {
        return structure.getFirst();
    }
}
