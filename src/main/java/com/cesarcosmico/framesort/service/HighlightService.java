package com.cesarcosmico.framesort.service;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class HighlightService {

    public record Highlight(ItemFrame frame, Color color) {
    }

    // Enough for any inspection a player can read; bounds the entities one click can spawn.
    private static final int MAX_SHOWN = 64;
    // Where Minecraft's item frame renderer draws the item, so the copy lies exactly over it.
    private static final float FACE_OFFSET = 0.46875f;
    private static final float DEPTH_IN_VISIBLE_FRAME = 0.4375f;
    private static final float DEPTH_IN_INVISIBLE_FRAME = 0.5f;
    private static final float DEGREES_PER_ROTATION = 45f;
    private static final float ITEM_SCALE = 0.5f;

    private final Plugin plugin;
    private final Map<UUID, List<ItemDisplay>> shown = new HashMap<>();

    public HighlightService(Plugin plugin) {
        this.plugin = plugin;
    }

    public void show(Player player, List<Highlight> highlights, int seconds) {
        UUID id = player.getUniqueId();
        clear(id);
        List<ItemDisplay> displays = new ArrayList<>();
        for (Highlight highlight : highlights.subList(0, Math.min(MAX_SHOWN, highlights.size()))) {
            if (highlight.frame().isValid() && !highlight.frame().getItem().isEmpty()) {
                ItemDisplay display = spawn(highlight);
                // Another plugin may cancel the spawn.
                if (display.isValid()) {
                    player.showEntity(plugin, display);
                    displays.add(display);
                }
            }
        }
        if (displays.isEmpty()) {
            return;
        }
        shown.put(id, displays);
        for (ItemDisplay display : displays) {
            // A display unloaded with its chunk is gone before the task runs; the entry must go with it.
            display.getScheduler().runDelayed(plugin, task -> {
                shown.remove(id, displays);
                display.remove();
            }, () -> shown.remove(id, displays), seconds * 20L);
        }
    }

    private void clear(UUID player) {
        List<ItemDisplay> displays = shown.remove(player);
        if (displays != null) {
            displays.forEach(Entity::remove);
        }
    }

    public void clearAll() {
        shown.values().forEach(displays -> displays.forEach(Entity::remove));
        shown.clear();
    }

    private ItemDisplay spawn(Highlight highlight) {
        ItemFrame frame = highlight.frame();
        Location at = frame.getLocation();
        at.setYaw(0);
        at.setPitch(0);
        ItemStack item = frame.getItem().clone();
        Matrix4f transform = itemTransform(frame.getFacing(), frame.getRotation().ordinal(), frame.isVisible());
        ItemDisplay display = at.getWorld().spawn(at, ItemDisplay.class, entity -> {
            // Set before the entity is sent to anyone, so no other player ever sees it.
            entity.setVisibleByDefault(false);
            entity.setPersistent(false);
            entity.setItemStack(item);
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            entity.setTransformationMatrix(transform);
            entity.setGlowColorOverride(highlight.color());
            entity.setGlowing(true);
        });
        return display;
    }

    static Matrix4f itemTransform(BlockFace facing, int rotation, boolean visibleFrame) {
        boolean horizontal = facing.getModY() == 0;
        float xRot = horizontal ? 0 : -90f * facing.getModY();
        float yRot = horizontal ? 180f - yRot(facing) : 180f;
        return new Matrix4f()
                .translate(facing.getModX() * FACE_OFFSET, facing.getModY() * FACE_OFFSET,
                        facing.getModZ() * FACE_OFFSET)
                .rotateX((float) Math.toRadians(xRot))
                .rotateY((float) Math.toRadians(yRot))
                .translate(0, 0, visibleFrame ? DEPTH_IN_VISIBLE_FRAME : DEPTH_IN_INVISIBLE_FRAME)
                .rotateZ((float) Math.toRadians(rotation * DEGREES_PER_ROTATION))
                .scale(ITEM_SCALE)
                .rotateY((float) -Math.PI);
    }

    // The angles of Minecraft's Direction#toYRot, so the copy turns like the real item.
    private static float yRot(BlockFace facing) {
        return switch (facing) {
            case WEST -> 90f;
            case NORTH -> 180f;
            case EAST -> 270f;
            default -> 0f;
        };
    }
}
