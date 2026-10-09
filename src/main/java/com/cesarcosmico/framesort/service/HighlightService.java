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

/**
 * A per-player copy of the frame item glows instead of the frame: frame glowing is global and its colour
 * team-based.
 */
public final class HighlightService {

    public record Highlight(ItemFrame frame, Color color) {
    }

    // Enough for any inspection a player can read; bounds the entities one click can spawn.
    private static final int MAX_SHOWN = 64;

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
                displays.add(spawn(player, highlight));
            }
        }
        if (displays.isEmpty()) {
            return;
        }
        shown.put(id, displays);
        player.getScheduler().runDelayed(plugin, task -> {
            if (shown.remove(id, displays)) {
                displays.forEach(Entity::remove);
            }
        }, () -> clear(id), seconds * 20L);
    }

    public void clear(UUID player) {
        List<ItemDisplay> displays = shown.remove(player);
        if (displays != null) {
            displays.forEach(Entity::remove);
        }
    }

    public void clearAll() {
        shown.values().forEach(displays -> displays.forEach(Entity::remove));
        shown.clear();
    }

    private ItemDisplay spawn(Player player, Highlight highlight) {
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
        player.showEntity(plugin, display);
        return display;
    }

    /**
     * Where the vanilla item frame renderer draws the item, relative to the frame entity: the steps of
     * {@code ItemFrameRenderer#submit} in Minecraft 26.2, ending with a half turn that undoes the one
     * {@code ItemDisplayRenderer} adds, so the copy lands on the real item.
     */
    static Matrix4f itemTransform(BlockFace facing, int rotation, boolean visibleFrame) {
        boolean horizontal = facing.getModY() == 0;
        float xRot = horizontal ? 0 : -90f * facing.getModY();
        float yRot = horizontal ? 180f - yRot(facing) : 180f;
        float offset = 0.46875f;
        return new Matrix4f()
                .translate(facing.getModX() * offset, facing.getModY() * offset, facing.getModZ() * offset)
                .rotateX((float) Math.toRadians(xRot))
                .rotateY((float) Math.toRadians(yRot))
                .translate(0, 0, visibleFrame ? 0.4375f : 0.5f)
                .rotateZ((float) Math.toRadians(rotation * 45f))
                .scale(0.5f)
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
