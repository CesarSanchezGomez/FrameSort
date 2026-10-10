package com.cesarcosmico.framesort.service;

import com.cesarcosmico.framesort.model.Composting;
import com.cesarcosmico.framesort.model.Delivery;
import com.cesarcosmico.framesort.service.TargetResolver.Destination;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.ItemFrame;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

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

    public record Delivered(Location from, ItemStack item, int amount, Location to, Destination destination) {
    }

    // The delay vanilla gives a full composter before it turns ready.
    private static final long READY_DELAY_TICKS = 20;

    private final Plugin plugin;
    private final TargetResolver resolver;
    private final Consumer<Delivered> observer;

    public DeliveryService(Plugin plugin, TargetResolver resolver, Consumer<Delivered> observer) {
        this.plugin = plugin;
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
        List<Delivery.Level<ItemFrame, End>> levels = plan.levels().stream().map(DeliveryService::level).toList();
        Location from = source.location();
        Delivery.Outcome<ItemFrame, End> outcome = Delivery.deliver(amount, levels,
                (frame, offered) -> offer(frame, stack, offered, from), source::commit);
        if (plan.stale() || !outcome.gone().isEmpty()) {
            resolver.forget(origin, stack);
        }

        int remaining = outcome.remaining();
        End end = outcome.end();
        if (end == null) {
            return amount - remaining;
        }
        if (end.destination() == Destination.DESTROYED) {
            source.destroy();
            observer.accept(new Delivered(from, stack, remaining, end.frame().getLocation(), Destination.DESTROYED));
        } else {
            Location to = FrameGeometry.dropPoint(end.frame());
            source.moveTo(to);
            observer.accept(new Delivered(from, stack, remaining, to, Destination.DROPPED));
        }
        return amount;
    }

    /** Containers in random order; then one random drop spot takes the rest, or else the lava destroys it. */
    private static Delivery.Level<ItemFrame, End> level(TargetResolver.Level level) {
        List<ItemFrame> containers = new ArrayList<>(level.containers());
        Collections.shuffle(containers, ThreadLocalRandom.current());
        List<ItemFrame> drops = level.drops();
        End end = !drops.isEmpty()
                ? new End(drops.get(ThreadLocalRandom.current().nextInt(drops.size())), Destination.DROPPED)
                : level.lava().isEmpty() ? null : new End(level.lava().getFirst(), Destination.DESTROYED);
        return new Delivery.Level<>(containers, end);
    }

    private record End(ItemFrame frame, Destination destination) {
    }

    private int offer(ItemFrame frame, ItemStack stack, int amount, Location from) {
        if (!frame.isValid() || !FrameGeometry.attachedLoaded(frame)) {
            return Delivery.GONE;
        }
        Block block = FrameGeometry.attachedBlock(frame);
        int notAccepted;
        if (block.getType() == Material.COMPOSTER) {
            notAccepted = compost(block, stack, amount);
        } else {
            Inventory inventory = FrameGeometry.inventory(block);
            if (inventory == null) {
                return Delivery.GONE;
            }
            Map<Integer, ItemStack> left = inventory.addItem(stack.asQuantity(amount));
            notAccepted = left.values().stream().mapToInt(ItemStack::getAmount).sum();
        }
        if (notAccepted < amount) {
            int accepted = amount - notAccepted;
            observer.accept(new Delivered(from, stack, accepted, frame.getLocation(), Destination.CONTAINER));
        }
        return notAccepted;
    }

    /**
     * Composts like a hopper feeding the composter; returns how many items it did not take. The plan only sends
     * compostable items to a composter.
     */
    private int compost(Block composter, ItemStack stack, int amount) {
        Levelled data = (Levelled) composter.getBlockData();
        int before = data.getLevel();
        Composting.Fill fill = Composting.fill(before, stack.getType().getCompostChance(), amount,
                ThreadLocalRandom.current()::nextDouble);
        if (fill.consumed() > 0) {
            if (fill.level() != before) {
                data.setLevel(fill.level());
                composter.setBlockData(data);
            }
            composter.getWorld().playEffect(composter.getLocation(), Effect.COMPOSTER_FILL_ATTEMPT,
                    fill.level() != before);
        }
        if (fill.level() == Composting.FULL) {
            // Vanilla turns a full composter ready on a tick it schedules itself; a level set through the API
            // schedules none, so FrameSort asks for that tick. Extra ticks on a ready composter do nothing.
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (composter.getWorld().isChunkLoaded(composter.getX() >> 4, composter.getZ() >> 4)
                        && composter.getType() == Material.COMPOSTER) {
                    composter.tick();
                }
            }, READY_DELAY_TICKS);
        }
        return amount - fill.consumed();
    }
}
