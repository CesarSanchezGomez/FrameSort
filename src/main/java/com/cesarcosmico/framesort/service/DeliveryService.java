package com.cesarcosmico.framesort.service;

import com.cesarcosmico.framesort.config.FrameSortSettings;
import com.cesarcosmico.framesort.model.Delivery;
import com.cesarcosmico.framesort.model.TargetSet;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.entity.ItemFrame;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Containers are looked up the moment items go in, so one that vanished is skipped instead of duplicating items. */
public final class DeliveryService {

    public interface Source {

        ItemStack stack();

        Location location();

        /** Leaves {@code remaining} items in the source; 0 empties it. */
        void commit(int remaining);

        void moveTo(Location destination);

        void destroy();
    }

    public enum Kind { CONTAINER, DROPPED, DESTROYED }

    public record Delivered(Location from, ItemStack item, int amount, Location to, Kind kind) {
    }

    private final Supplier<FrameSortSettings> settings;
    private final TargetResolver resolver;
    private final Consumer<Delivered> observer;

    public DeliveryService(Supplier<FrameSortSettings> settings, TargetResolver resolver, Consumer<Delivered> observer) {
        this.settings = settings;
        this.resolver = resolver;
        this.observer = observer;
    }

    /** Returns how many items left the source, whether into containers, dropped or destroyed. */
    public int deliver(Block origin, Source source) {
        ItemStack stack = source.stack().clone();
        if (stack.isEmpty()) {
            return 0;
        }
        List<TargetResolver.Match> matches = resolver.matches(origin, stack);
        if (matches.isEmpty()) {
            return 0;
        }

        boolean insert = settings.get().delivery().insertIntoContainers();
        TargetSet<ItemFrame> containers = new TargetSet<>();
        TargetSet<ItemFrame> drops = new TargetSet<>();
        boolean stale = false;
        for (TargetResolver.Match match : matches) {
            ItemFrame frame = match.frame();
            if (!frame.isValid() || !FrameGeometry.attachedLoaded(frame) || !frame.getItem().equals(match.frameItem())) {
                stale = true;
                continue;
            }
            if (insert && inventory(FrameGeometry.attachedBlock(frame)) != null) {
                containers.add(match.priority(), frame);
            } else {
                drops.add(match.priority(), frame);
            }
        }

        int amount = stack.getAmount();
        List<ItemFrame> order = new ArrayList<>(containers.targets());
        Collections.shuffle(order, ThreadLocalRandom.current());
        Location from = source.location();
        Delivery.Outcome<ItemFrame> outcome = Delivery.deliver(amount, order,
                (frame, offered) -> offer(frame, stack, offered, from), source::commit);
        if (stale || !outcome.gone().isEmpty()) {
            resolver.forget(origin, stack);
        }

        int remaining = outcome.remaining();
        if (remaining > 0) {
            List<ItemFrame> best = drops.targets();
            List<ItemFrame> open = best.stream().filter(frame -> !isLava(frame)).toList();
            if (!open.isEmpty()) {
                Location to = open.get(ThreadLocalRandom.current().nextInt(open.size())).getLocation();
                source.moveTo(to);
                observer.accept(new Delivered(from, stack, remaining, to, Kind.DROPPED));
                return amount;
            }
            if (!best.isEmpty()) {
                source.destroy();
                observer.accept(new Delivered(from, stack, remaining, best.getFirst().getLocation(), Kind.DESTROYED));
                return amount;
            }
        }
        return amount - remaining;
    }

    public static @Nullable Inventory inventory(Block block) {
        // A composter has no inventory, so a frame on it feeds the hopper underneath, as in a composter farm.
        if (block.getType() == Material.COMPOSTER) {
            Block below = block.getRelative(BlockFace.DOWN);
            return below.getType() == Material.HOPPER ? containerInventory(below) : null;
        }
        return containerInventory(block);
    }

    public static boolean isLava(ItemFrame frame) {
        return FrameGeometry.attachedBlock(frame).getType() == Material.LAVA_CAULDRON;
    }

    private int offer(ItemFrame frame, ItemStack stack, int amount, Location from) {
        Inventory inventory = frame.isValid() && FrameGeometry.attachedLoaded(frame)
                ? inventory(FrameGeometry.attachedBlock(frame)) : null;
        if (inventory == null) {
            return Delivery.GONE;
        }
        Map<Integer, ItemStack> left = inventory.addItem(stack.asQuantity(amount));
        int notAccepted = left.values().stream().mapToInt(ItemStack::getAmount).sum();
        if (notAccepted < amount) {
            observer.accept(new Delivered(from, stack, amount - notAccepted, frame.getLocation(), Kind.CONTAINER));
        }
        return notAccepted;
    }

    // Not a snapshot: items must go into the live block entity, never into a copy written back later.
    private static @Nullable Inventory containerInventory(Block block) {
        return block.getState(false) instanceof Container container ? container.getInventory() : null;
    }
}
