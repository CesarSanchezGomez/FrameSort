package com.cesarcosmico.framesort.service;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.GlowItemFrame;
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
 * A per-player display of the frame's own model glows over each frame: making the frame itself glow would show it to
 * everyone, in a colour that depends on scoreboard teams, and a second frame cannot hang in the same spot.
 */
public final class HighlightService {

    public record Highlight(ItemFrame frame, Color color) {
    }

    // Enough for any inspection a player can read; bounds the entities one click can spawn.
    private static final int MAX_SHOWN = 64;
    // The frame entity sits in the middle of its 1/16-thick border, and the flat frame sprite is 1/16 thick too:
    // centring the sprite this far out puts it just in front of the real frame, so the two never flicker.
    static final float FRAME_OFFSET = 1 / 16f + 0.002f;

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
        ItemStack model = ItemStack.of(frame instanceof GlowItemFrame ? Material.GLOW_ITEM_FRAME : Material.ITEM_FRAME);
        Matrix4f transform = frameTransform(frame.getFacing());
        ItemDisplay display = at.getWorld().spawn(at, ItemDisplay.class, entity -> {
            // Set before the entity is sent to anyone, so no other player ever sees it.
            entity.setVisibleByDefault(false);
            entity.setPersistent(false);
            entity.setItemStack(model);
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            entity.setTransformationMatrix(transform);
            entity.setGlowColorOverride(highlight.color());
            entity.setGlowing(true);
        });
        player.showEntity(plugin, display);
        return display;
    }

    /**
     * The flat frame sprite, relative to the frame entity: turned like {@code ItemFrameRenderer#submit} in Minecraft 26.2
     * turns the frame, {@link #FRAME_OFFSET} out from the wall, and with a half turn that undoes the one
     * {@code ItemDisplayRenderer} adds, so the sprite faces the same way as the frame.
     */
    static Matrix4f frameTransform(BlockFace facing) {
        boolean horizontal = facing.getModY() == 0;
        float xRot = horizontal ? 0 : -90f * facing.getModY();
        float yRot = horizontal ? 180f - yRot(facing) : 180f;
        return new Matrix4f()
                .translate(facing.getModX() * FRAME_OFFSET, facing.getModY() * FRAME_OFFSET,
                        facing.getModZ() * FRAME_OFFSET)
                .rotateX((float) Math.toRadians(xRot))
                .rotateY((float) Math.toRadians(yRot))
                .rotateY((float) -Math.PI);
    }

    // The angles of Minecraft's Direction#toYRot, so the sprite turns like the real frame.
    private static float yRot(BlockFace facing) {
        return switch (facing) {
            case WEST -> 90f;
            case NORTH -> 180f;
            case EAST -> 270f;
            default -> 0f;
        };
    }
}
