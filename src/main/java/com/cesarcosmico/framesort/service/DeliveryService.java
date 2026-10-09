package com.cesarcosmico.framesort.service;

import com.cesarcosmico.framesort.config.InspectSettings;
import com.cesarcosmico.framesort.model.Delivery;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.ItemFrame;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

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

    public enum Kind {
        CONTAINER,
        DROPPED,
        DESTROYED;

        public Color color(InspectSettings.Colors colors) {
            return switch (this) {
                case CONTAINER -> colors.container();
                case DROPPED -> colors.dropped();
                case DESTROYED -> colors.lava();
            };
        }
    }

    public record Delivered(Location from, ItemStack item, int amount, Location to, Kind kind) {
    }

    private final TargetResolver resolver;
    private final Consumer<Delivered> observer;

    public DeliveryService(TargetResolver resolver, Consumer<Delivered> observer) {
        this.resolver = resolver;
        this.observer = observer;
    }

    /** Returns how many items left the source, whether into containers, dropped or destroyed. */
    public int deliver(Block origin, Source source) {
        ItemStack stack = source.stack().clone();
        if (stack.isEmpty()) {
            return 0;
        }
        TargetResolver.Plan plan = resolver.plan(origin, stack);
        int amount = stack.getAmount();
        List<ItemFrame> order = new ArrayList<>(plan.containers());
        Collections.shuffle(order, ThreadLocalRandom.current());
        Location from = source.location();
        Delivery.Outcome<ItemFrame> outcome = Delivery.deliver(amount, order,
                (frame, offered) -> offer(frame, stack, offered, from), source::commit);
        if (plan.stale() || !outcome.gone().isEmpty()) {
            resolver.forget(origin, stack);
        }

        int remaining = outcome.remaining();
        if (remaining > 0) {
            List<ItemFrame> best = plan.drops();
            List<ItemFrame> open = best.stream().filter(frame -> !FrameGeometry.isLava(frame)).toList();
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

    private int offer(ItemFrame frame, ItemStack stack, int amount, Location from) {
        Inventory inventory = frame.isValid() && FrameGeometry.attachedLoaded(frame)
                ? FrameGeometry.inventory(FrameGeometry.attachedBlock(frame)) : null;
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
}
