package com.cesarcosmico.framesort.config;

import com.cesarcosmico.framesort.item.ItemTemplate;
import org.bukkit.Material;

import java.util.List;

/**
 * A teleport pad: a column of blocks listed top to bottom. Items resting on the top block are sent to targets.
 *
 * @param item the special top block used when pads are created with {@code creation: item}
 */
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
