package com.cesarcosmico.framesort.service;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.BundleContents;
import io.papermc.paper.datacomponent.item.ItemContainerContents;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

sealed interface Slot permits Slot.InventorySlot, Slot.ContainerSlot, Slot.BundleSlot {
    ItemStack get();

    void set(ItemStack stack);

    record InventorySlot(Inventory inventory, int index) implements Slot {
        @Override
        public ItemStack get() {
            ItemStack item = inventory.getItem(index);
            return item == null ? ItemStack.empty() : item;
        }

        @Override
        public void set(ItemStack stack) {
            inventory.setItem(index, stack.isEmpty() ? null : stack);
        }
    }

    record ContainerSlot(Slot parent, int index) implements Slot {
        @Override
        public ItemStack get() {
            ItemContainerContents contents = parent.get().getData(DataComponentTypes.CONTAINER);
            return contents == null || index >= contents.contents().size()
                    ? ItemStack.empty() : contents.contents().get(index);
        }

        @Override
        public void set(ItemStack stack) {
            ItemStack holder = parent.get();
            ItemContainerContents contents = holder.getData(DataComponentTypes.CONTAINER);
            if (contents == null || index >= contents.contents().size()) {
                return;
            }
            List<ItemStack> items = new ArrayList<>(contents.contents());
            items.set(index, stack);
            holder.setData(DataComponentTypes.CONTAINER, ItemContainerContents.containerContents(items));
            parent.set(holder);
        }
    }

    record BundleSlot(Slot parent, int index) implements Slot {
        @Override
        public ItemStack get() {
            BundleContents contents = parent.get().getData(DataComponentTypes.BUNDLE_CONTENTS);
            return contents == null || index >= contents.contents().size()
                    ? ItemStack.empty() : contents.contents().get(index);
        }

        @Override
        public void set(ItemStack stack) {
            ItemStack holder = parent.get();
            BundleContents contents = holder.getData(DataComponentTypes.BUNDLE_CONTENTS);
            if (contents == null || index >= contents.contents().size()) {
                return;
            }
            List<ItemStack> items = new ArrayList<>(contents.contents());
            if (stack.isEmpty()) {
                items.remove(index);
            } else {
                items.set(index, stack);
            }
            holder.setData(DataComponentTypes.BUNDLE_CONTENTS, BundleContents.bundleContents(items));
            parent.set(holder);
        }
    }
}
