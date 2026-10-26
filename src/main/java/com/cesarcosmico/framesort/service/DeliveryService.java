package com.cesarcosmico.framesort.service;

import com.cesarcosmico.framesort.item.DropCodec;
import com.cesarcosmico.framesort.model.Composting;
import com.cesarcosmico.framesort.model.Delivery;
import com.cesarcosmico.framesort.service.TargetResolver.Destination;
import com.cesarcosmico.framesort.service.TargetResolver.Target;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

public final class DeliveryService {

    public interface Source {

        ItemStack stack();

        /** Leaves {@code remaining} items in the source; 0 empties it. */
        void commit(int remaining);

        /**
         * Puts the items left in the source at {@code destination}, as an item entity, and returns it; or returns
         * {@code null} and keeps them in the source when they couldn't be put there.
         */
        @Nullable Item moveTo(Location destination);

        void destroy();
    }

    /** {@code owner} is the source's owner. */
    public record Delivered(Location from, @Nullable UUID owner, ItemStack item, int amount, Location to,
                            Destination destination) {
    }

    // Vanilla's delay before a full composter turns ready.
    private static final long READY_DELAY_TICKS = 20;

    private final Plugin plugin;
    private final TargetResolver resolver;
    private final CustomItemResolver customItems;
    private final Consumer<Delivered> observer;

    public DeliveryService(Plugin plugin, TargetResolver resolver, CustomItemResolver customItems,
                           Consumer<Delivered> observer) {
        this.plugin = plugin;
        this.resolver = resolver;
        this.customItems = customItems;
        this.observer = observer;
    }

    /** Returns how many items left the source, whether into containers, dropped or destroyed. */
    public int deliver(Block origin, @Nullable UUID owner, int range, Source source) {
        ItemStack stack = source.stack().clone();
        if (stack.isEmpty()) {
            return 0;
        }
        TargetResolver.Plan plan = resolver.plan(origin, owner, range, stack);
        int amount = stack.getAmount();
        List<Delivery.Level<ItemFrame, Target>> levels = plan.levels().stream()
                .map(level -> new Delivery.Level<>(level.containers(), level.end())).toList();
        // From the source block, not the item: a pad's items rest anywhere on it, and one sweep is one turn.
        Location from = origin.getLocation().toCenterLocation();
        Delivery.Outcome<ItemFrame, Target> outcome = Delivery.deliver(amount, levels,
                (frame, offered) -> offer(frame, stack, offered, from, owner), source::commit);
        if (plan.stale() || !outcome.gone().isEmpty()) {
            resolver.forget(origin, owner, range, stack);
        }

        int remaining = outcome.remaining();
        Target end = outcome.end();
        if (end == null) {
            return amount - remaining;
        }
        if (end.destination() == Destination.DESTROYED) {
            source.destroy();
            observer.accept(new Delivered(from, owner, stack, remaining, end.frame().getLocation(),
                    Destination.DESTROYED));
        } else {
            Location to = FrameGeometry.dropPoint(end.frame());
            Item dropped = source.moveTo(to);
            if (dropped == null) {
                return amount - remaining;
            }
            DropCodec.mark(dropped);
            observer.accept(new Delivered(from, owner, stack, remaining, to, Destination.DROPPED));
        }
        return amount;
    }

    private int offer(ItemFrame frame, ItemStack stack, int amount, Location from, @Nullable UUID owner) {
        if (!frame.isValid() || !FrameGeometry.attachedLoaded(frame)) {
            return Delivery.GONE;
        }
        Block block = FrameGeometry.attachedBlock(frame);
        int notAccepted;
        if (block.getType() == Material.COMPOSTER) {
            notAccepted = compost(block, stack, amount);
        } else {
            Inventory inventory = FrameGeometry.storage(block);
            if (inventory != null) {
                Map<Integer, ItemStack> left = inventory.addItem(stack.asQuantity(amount));
                notAccepted = left.values().stream().mapToInt(ItemStack::getAmount).sum();
            } else {
                notAccepted = customItems.store(block, stack.asQuantity(amount));
                if (notAccepted == Delivery.GONE) {
                    return Delivery.GONE;
                }
            }
        }
        if (notAccepted < amount) {
            int accepted = amount - notAccepted;
            observer.accept(new Delivered(from, owner, stack, accepted, frame.getLocation(), Destination.CONTAINER));
        }
        return notAccepted;
    }

    // The plan only sends compostable items to a composter.
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
