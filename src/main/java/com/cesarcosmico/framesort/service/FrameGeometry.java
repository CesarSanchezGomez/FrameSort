package com.cesarcosmico.framesort.service;

import com.cesarcosmico.framesort.model.BlockKey;
import com.cesarcosmico.framesort.model.ChunkKey;
import com.cesarcosmico.framesort.model.FramePosition;
import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Hopper;
import org.bukkit.entity.ItemFrame;

/** Where frames hang and how blocks map to keys. */
public final class FrameGeometry {

    private FrameGeometry() {
    }

    /** The block the frame hangs on. */
    public static Block attachedBlock(ItemFrame frame) {
        return frame.getLocation().getBlock().getRelative(frame.getAttachedFace());
    }

    public static FramePosition position(ItemFrame frame) {
        BlockFace attached = frame.getAttachedFace();
        BlockData data = frame.getLocation().getBlock().getRelative(attached).getBlockData();
        // A hopper's facing is where it outputs, not a front.
        BlockFace facing = data instanceof Directional directional && !(data instanceof Hopper)
                ? directional.getFacing() : null;
        return FramePosition.of(attached.getOppositeFace(), facing);
    }

    public static BlockKey key(Block block) {
        return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    public static ChunkKey key(Chunk chunk) {
        return new ChunkKey(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
    }
}
