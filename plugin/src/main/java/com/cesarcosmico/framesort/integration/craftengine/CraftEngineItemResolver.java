package com.cesarcosmico.framesort.integration.craftengine;

import com.cesarcosmico.framesort.model.Delivery;
import com.cesarcosmico.framesort.model.ItemReference;
import com.cesarcosmico.framesort.service.CustomItemResolver;
import net.kyori.adventure.text.Component;
import net.momirealms.craftengine.bukkit.api.BukkitAdaptor;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.bukkit.block.behavior.SimpleStorageBlockBehavior;
import net.momirealms.craftengine.bukkit.block.entity.DrawerBlockEntityController;
import net.momirealms.craftengine.bukkit.block.entity.SimpleStorageBlockEntityController;
import net.momirealms.craftengine.bukkit.item.BukkitItemDefinition;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.BlockPos;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

public final class CraftEngineItemResolver implements CustomItemResolver {

    private static final String PREFIX = ItemReference.CRAFT_ENGINE + ":";

    @Override
    public @Nullable String id(ItemStack item) {
        Key id = CraftEngineItems.getCustomItemId(item);
        return id == null ? null : PREFIX + id.asString();
    }

    @Override
    public @Nullable Component name(String reference) {
        BukkitItemDefinition item = CraftEngineItems.byId(reference.substring(PREFIX.length()));
        return item == null ? null : item.buildBukkitItem().effectiveName();
    }

    @Override
    public @Nullable ItemStack item(ItemReference.Custom reference, int amount) {
        BukkitItemDefinition item = CraftEngineItems.byId(reference.reference().substring(PREFIX.length()));
        if (item == null) {
            return null;
        }
        ItemStack built = item.buildBukkitItem();
        built.setAmount(amount);
        return built;
    }

    @Override
    public @Nullable String blockId(Block block) {
        ImmutableBlockState state = CraftEngineBlocks.getCustomBlockState(block);
        return state == null || state.isEmpty() ? null : PREFIX + state.owner().value().id().asString();
    }

    @Override
    public @Nullable ItemStack blockItem(ItemReference.Custom block, int amount) {
        BlockDefinition definition = CraftEngineBlocks.byId(Key.of(block.reference().substring(PREFIX.length())));
        // The block's own ID unless its settings name another item; there may be no item with that ID.
        Key item = definition == null ? null : definition.defaultState().settings().itemId();
        return item == null ? null : item(new ItemReference.Custom(PREFIX + item.asString()), amount);
    }

    @Override
    public boolean isCustomBlock(Block block) {
        return CraftEngineBlocks.isCustomBlock(block);
    }

    @Override
    public boolean isContainer(Block block) {
        BlockEntity entity = blockEntity(block);
        return entity != null && (storage(entity) != null || drawer(entity) != null);
    }

    @Override
    public int store(Block block, ItemStack items) {
        BlockEntity entity = blockEntity(block);
        if (entity == null) {
            return Delivery.GONE;
        }
        // addItem leaves in items what didn't fit, so the amount offered is read first.
        int offered = items.getAmount();
        SimpleStorageBlockEntityController storage = storage(entity);
        if (storage != null) {
            int left = storage.inventory().addItem(items).values().stream().mapToInt(ItemStack::getAmount).sum();
            if (left < offered) {
                // Bukkit's addItem doesn't tell CraftEngine, which then wouldn't save the items with the chunk.
                entity.world().blockEntityChanged(entity.pos());
            }
            return left;
        }
        DrawerBlockEntityController drawer = drawer(entity);
        if (drawer == null) {
            return Delivery.GONE;
        }
        // Not addItem, which would replace what the drawer holds.
        return offered - drawer.put(BukkitAdaptor.adapt(items), offered);
    }

    // CraftEngine creates the block entity with its block and keeps it through /ce reload, so it is only read here.
    private static @Nullable BlockEntity blockEntity(Block block) {
        if (!CraftEngineBlocks.isCustomBlock(block)) {
            return null;
        }
        return BukkitAdaptor.adapt(block.getWorld()).storageWorld()
                .getBlockEntityAtIfLoaded(new BlockPos(block.getX(), block.getY(), block.getZ()), false);
    }

    private static @Nullable SimpleStorageBlockEntityController storage(BlockEntity entity) {
        SimpleStorageBlockBehavior behavior = entity.blockState().behavior().getFirst(SimpleStorageBlockBehavior.class);
        return behavior == null || !behavior.canPlaceItem
                ? null : entity.controller.get(SimpleStorageBlockEntityController.class, 0);
    }

    private static @Nullable DrawerBlockEntityController drawer(BlockEntity entity) {
        DrawerBlockEntityController drawer = entity.controller.get(DrawerBlockEntityController.class, 0);
        return drawer == null || !drawer.behavior.canPlaceItem ? null : drawer;
    }
}
