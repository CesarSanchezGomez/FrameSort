package com.cesarcosmico.framesort.service;

import com.cesarcosmico.framesort.model.BlockKey;
import com.cesarcosmico.framesort.model.ChunkKey;
import org.bukkit.Chunk;
import org.bukkit.Location;
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

public final class FrameGeometry {

    private FrameGeometry() {
    }

    public static Block attachedBlock(ItemFrame frame) {
        return frame.getLocation().getBlock().getRelative(frame.getAttachedFace());
    }

    public static boolean attachedLoaded(ItemFrame frame) {
        Block block = attachedBlock(frame);
        return block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4);
    }

    // Not a snapshot: items must go into the live block entity, never into a copy written back later.
    public static @Nullable Inventory storage(Block block) {
        return block.getState(false) instanceof Container container && isStorage(container)
                ? container.getInventory() : null;
    }

    // Inventory#addItem ignores a block's slot rules, so only blocks whose every slot takes any item are filled.
    private static boolean isStorage(Container container) {
        return container instanceof Chest || container instanceof Barrel || container instanceof ShulkerBox
                || container instanceof Hopper || container instanceof Dispenser || container instanceof Dropper;
    }

    /**
     * Whether items sent to a frame on this block go into it: plain storage, a custom plugin's block that stores
     * items, or a composter that composts them.
     */
    public static boolean isContainer(Block block, CustomItemResolver customItems) {
        return block.getType() == Material.COMPOSTER || storage(block) != null || customItems.isContainer(block);
    }

    // The frame sits flush against its block; an item spawned there overlaps the block and gets pushed out of it.
    public static Location dropPoint(ItemFrame frame) {
        return frame.getLocation().getBlock().getLocation().toCenterLocation();
    }

    public static boolean isLava(ItemFrame frame) {
        return attachedBlock(frame).getType() == Material.LAVA_CAULDRON;
    }

    public static long distanceSquared(ItemFrame frame, BlockKey to) {
        return key(frame.getLocation().getBlock()).distanceSquared(to);
    }

    public static BlockKey key(Block block) {
        return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    public static ChunkKey key(Chunk chunk) {
        return new ChunkKey(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
    }
}
