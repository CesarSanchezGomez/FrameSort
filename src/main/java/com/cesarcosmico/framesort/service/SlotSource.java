package com.cesarcosmico.framesort.service;

import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.jspecify.annotations.Nullable;

/** Offers all of the slot but {@code kept} items, which stay whatever happens: a sorter's turn stops at 64. */
record SlotSource(Slot slot, int kept) implements DeliveryService.Source {
    @Override
    public ItemStack stack() {
        ItemStack item = slot.get();
        return item.asQuantity(item.getAmount() - kept);
    }

    @Override
    public void commit(int remaining) {
        leave(kept + remaining);
    }

    @Override
    public @Nullable Item moveTo(Location destination) {
        Item dropped = destination.getWorld().dropItem(destination, stack(),
                item -> item.setVelocity(new Vector()));
        // Another plugin may cancel the spawn; the item returned then isn't in the world.
        if (!dropped.isValid()) {
            return null;
        }
        leave(kept);
        return dropped;
    }

    @Override
    public void destroy() {
        leave(kept);
    }

    private void leave(int amount) {
        slot.set(amount <= 0 ? ItemStack.empty() : slot.get().asQuantity(amount));
    }
}
