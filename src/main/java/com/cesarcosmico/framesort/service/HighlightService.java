package com.cesarcosmico.framesort.service;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Makes frames glow for one player. Each frame gets a temporary, non-persistent block display shaped like the frame,
 * glowing in an exact colour and hidden from everyone else. Making the frame itself glow would show it to every
 * player, in a colour that depends on scoreboard teams.
 */
public final class HighlightService {

    public record Highlight(ItemFrame frame, Color color) {
    }

    // Enough for any inspection a player can read; bounds the entities one click can spawn.
    private static final int MAX_SHOWN = 64;
    private static final float PIXEL = 1f / 16;
    private static final float MARGIN = 0.02f;

    private final Plugin plugin;
    private final Map<UUID, List<BlockDisplay>> shown = new HashMap<>();

    public HighlightService(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Replaces whatever the player had highlighted. */
    public void show(Player player, List<Highlight> highlights, int seconds) {
        UUID id = player.getUniqueId();
        clear(id);
        List<BlockDisplay> displays = new ArrayList<>();
        for (Highlight highlight : highlights.subList(0, Math.min(MAX_SHOWN, highlights.size()))) {
            if (highlight.frame().isValid()) {
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
        List<BlockDisplay> displays = shown.remove(player);
        if (displays != null) {
            displays.forEach(Entity::remove);
        }
    }

    public void clearAll() {
        shown.values().forEach(displays -> displays.forEach(Entity::remove));
        shown.clear();
    }

    private BlockDisplay spawn(Player player, Highlight highlight) {
        ItemFrame frame = highlight.frame();
        Location corner = frame.getLocation().getBlock().getLocation();
        Transformation box = frameBox(frame.getFacing());
        BlockDisplay display = corner.getWorld().spawn(corner, BlockDisplay.class, entity -> {
            // Set before the entity is sent to anyone, so no other player ever sees it.
            entity.setVisibleByDefault(false);
            entity.setPersistent(false);
            entity.setBlock(Material.GLASS.createBlockData());
            entity.setTransformation(box);
            entity.setBrightness(new Display.Brightness(15, 15));
            entity.setGlowColorOverride(highlight.color());
            entity.setGlowing(true);
        });
        player.showEntity(plugin, display);
        return display;
    }

    /**
     * The frame's box inside its own block: 12 x 12 pixels and 1 pixel thick, lying against the face it hangs on
     * (the opposite of where it faces), slightly enlarged so the outline wraps the frame.
     */
    private static Transformation frameBox(BlockFace facing) {
        float[] min = {2 * PIXEL - MARGIN, 2 * PIXEL - MARGIN, 2 * PIXEL - MARGIN};
        float[] max = {14 * PIXEL + MARGIN, 14 * PIXEL + MARGIN, 14 * PIXEL + MARGIN};
        int axis = facing.getModX() != 0 ? 0 : facing.getModY() != 0 ? 1 : 2;
        int direction = facing.getModX() + facing.getModY() + facing.getModZ();
        if (direction > 0) {
            min[axis] = -MARGIN;
            max[axis] = PIXEL + MARGIN;
        } else {
            min[axis] = 1 - PIXEL - MARGIN;
            max[axis] = 1 + MARGIN;
        }
        return new Transformation(
                new Vector3f(min[0], min[1], min[2]),
                new Quaternionf(),
                new Vector3f(max[0] - min[0], max[1] - min[1], max[2] - min[2]),
                new Quaternionf());
    }
}
