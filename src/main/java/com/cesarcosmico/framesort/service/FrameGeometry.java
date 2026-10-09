package com.cesarcosmico.framesort.service;

import com.cesarcosmico.framesort.model.BlockKey;
import com.cesarcosmico.framesort.model.ChunkKey;
import com.cesarcosmico.framesort.model.FramePosition;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Hopper;
import org.bukkit.entity.ItemFrame;
import org.bukkit.inventory.Inventory;
import org.jspecify.annotations.Nullable;

import java.util.Set;

public final class FrameGeometry {

    private FrameGeometry() {
    }

    public static Block attachedBlock(ItemFrame frame) {
        return frame.getLocation().getBlock().getRelative(frame.getAttachedFace());
    }

    /** A frame on a chunk border can hang on the next chunk; reading it unloaded would load it synchronously. */
    public static boolean attachedLoaded(ItemFrame frame) {
        Block block = attachedBlock(frame);
        return block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4);
    }

    public static Set<FramePosition> positions(ItemFrame frame) {
        BlockFace attached = frame.getAttachedFace();
        BlockData data = frame.getLocation().getBlock().getRelative(attached).getBlockData();
        // A hopper's facing is where it outputs, not a front.
        BlockFace facing = data instanceof Directional directional && !(data instanceof Hopper)
                ? directional.getFacing() : null;
        return FramePosition.of(attached.getOppositeFace(), facing);
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
        return attachedBlock(frame).getType() == Material.LAVA_CAULDRON;
    }

    public static BlockKey key(Block block) {
        return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    public static ChunkKey key(Chunk chunk) {
        return new ChunkKey(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
    }

    // Not a snapshot: items must go into the live block entity, never into a copy written back later.
    private static @Nullable Inventory containerInventory(Block block) {
        return block.getState(false) instanceof Container container ? container.getInventory() : null;
    }
}
