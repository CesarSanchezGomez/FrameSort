package com.cesarcosmico.framesort.service;

import com.cesarcosmico.framesort.config.InspectSettings;
import com.cesarcosmico.framesort.model.Composting;
import com.cesarcosmico.framesort.model.Delivery;
import org.bukkit.Color;
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
                Location to = FrameGeometry.dropPoint(open.get(ThreadLocalRandom.current().nextInt(open.size())));
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
            observer.accept(new Delivered(from, stack, amount - notAccepted, frame.getLocation(), Kind.CONTAINER));
        }
        return notAccepted;
    }

    /** Composts like a hopper feeding the composter; returns how many items it did not take. */
    private int compost(Block composter, ItemStack stack, int amount) {
        Material material = stack.getType();
        if (!(composter.getBlockData() instanceof Levelled data) || !material.isCompostable()) {
            return amount;
        }
        int before = data.getLevel();
        Composting.Fill fill = Composting.fill(before, material.getCompostChance(), amount,
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
