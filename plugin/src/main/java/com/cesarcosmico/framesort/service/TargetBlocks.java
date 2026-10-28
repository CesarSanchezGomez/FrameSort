package com.cesarcosmico.framesort.service;

import org.bukkit.Material;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.block.Dispenser;
import org.bukkit.block.Dropper;
import org.bukkit.block.Hopper;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.ItemFrame;
import org.bukkit.inventory.Inventory;
import org.jspecify.annotations.Nullable;

import java.util.List;

final class TargetBlocks {

    private static final List<Class<? extends Container>> STORAGE =
            List.of(Chest.class, Barrel.class, ShulkerBox.class, Hopper.class, Dispenser.class, Dropper.class);

    private TargetBlocks() {
    }

    // Not a snapshot: items must go into the live block entity, never into a copy written back later.
    static @Nullable Inventory storage(Block block) {
        return block.getState(false) instanceof Container container && isStorage(container)
                ? container.getInventory() : null;
    }

    // Inventory#addItem ignores a block's slot rules, so only blocks whose every slot takes any item are filled.
    private static boolean isStorage(Container container) {
        return STORAGE.stream().anyMatch(type -> type.isInstance(container));
    }

    static boolean isContainer(Block block, CustomItemResolver customItems) {
        return block.getType() == Material.COMPOSTER || storage(block) != null || customItems.isContainer(block);
    }

    static boolean isLava(ItemFrame frame) {
        return FrameGeometry.attachedBlock(frame).getType() == Material.LAVA_CAULDRON;
    }
}
