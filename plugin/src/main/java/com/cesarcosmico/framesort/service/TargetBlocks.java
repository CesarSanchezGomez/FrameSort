package com.cesarcosmico.framesort.service;

import com.cesarcosmico.framesort.model.Composting;
import org.bukkit.Material;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.block.Dispenser;
import org.bukkit.block.Dropper;
import org.bukkit.block.Hopper;
import org.bukkit.block.ShulkerBox;
import org.bukkit.block.data.Levelled;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.List;

public final class TargetBlocks {

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
        for (Class<? extends Container> type : STORAGE) {
            if (type.isInstance(container)) {
                return true;
            }
        }
        return false;
    }

    static boolean isContainer(Block block, CustomItemResolver customItems) {
        return block.getType() == Material.COMPOSTER || storage(block) != null || customItems.isContainer(block);
    }

    static boolean isLava(Block attached) {
        return attached.getType() == Material.LAVA_CAULDRON;
    }

    // From 0 to 1, only for a block isContainer accepts.
    static double fullness(Block block, CustomItemResolver customItems) {
        if (block.getType() == Material.COMPOSTER) {
            // A ready composter is one level past full.
            return (double) Math.min(((Levelled) block.getBlockData()).getLevel(), Composting.FULL) / Composting.FULL;
        }
        Inventory inventory = storage(block);
        return inventory != null ? fullness(inventory) : customItems.fullness(block);
    }

    /** From 0 to 1. */
    public static double fullness(Inventory inventory) {
        double filledSlots = 0;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item != null) {
                // Other plugins can overstack a slot, which still counts as one.
                filledSlots += Math.min(1, (double) item.getAmount()
                        / Math.min(item.getMaxStackSize(), inventory.getMaxStackSize()));
            }
        }
        return filledSlots / inventory.getSize();
    }
}
