package com.cesarcosmico.framesort.service;

import com.cesarcosmico.framesort.config.SorterSettings;
import com.cesarcosmico.framesort.item.Keys;
import com.cesarcosmico.framesort.item.MarkerCodec;
import org.bukkit.Material;
import org.bukkit.entity.ItemFrame;
import org.bukkit.inventory.ItemStack;

final class SorterFrames {

    private SorterFrames() {
    }

    // item is the frame's own, or the one going in.
    static boolean makesSorter(SorterSettings sorter, ItemFrame frame, ItemStack item, CustomItemResolver customItems) {
        return FrameGeometry.attachedBlock(frame).getType() == Material.DISPENSER
                && holdsActivator(sorter, frame, item, customItems);
    }

    static boolean holdsActivator(SorterSettings sorter, ItemFrame frame, ItemStack item,
                                  CustomItemResolver customItems) {
        return sorter.frameTypes().contains(frame.getType())
                && sorter.activator().matches(item.getType(), customItems.id(item))
                && (!sorter.requireMarked() || MarkerCodec.read(item, Keys.ACTIVATOR) != null);
    }
}
