package com.cesarcosmico.framesort.listener;

import com.cesarcosmico.framesort.service.TargetIndex;
import com.cesarcosmico.framesort.text.Messages;
import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import io.papermc.paper.event.player.PlayerItemFrameChangeEvent;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.function.Supplier;

public final class TargetListener implements Listener {

    private final TargetIndex index;
    private final Supplier<Messages> messages;

    public TargetListener(TargetIndex index, Supplier<Messages> messages) {
        this.index = index;
        this.messages = messages;
    }

    @EventHandler
    public void onAdd(EntityAddToWorldEvent event) {
        if (event.getEntity() instanceof ItemFrame frame) {
            index.add(frame);
        }
    }

    @EventHandler
    public void onRemove(EntityRemoveFromWorldEvent event) {
        if (event.getEntity() instanceof ItemFrame frame) {
            index.remove(frame);
        }
    }

    // MONITOR: only an item that really went in ends the mark; the event is left alone. Every item a player puts in
    // passes here, while an item can leave a frame with no event that confirms it (an arrow, another plugin).
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFrameChange(PlayerItemFrameChangeEvent event) {
        ItemFrame frame = event.getItemFrame();
        Player player = event.getPlayer();
        if (event.getAction() != PlayerItemFrameChangeEvent.ItemFrameChangeAction.PLACE) {
            index.changed(frame.getWorld());
        } else if (index.itemPlaced(frame, player.getUniqueId())) {
            player.sendActionBar(messages.get().get("mark.cleared"));
        }
    }

    // Hitting a frame (arrows, explosions) knocks its item out without a frame change event.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof ItemFrame frame) {
            index.changed(frame.getWorld());
        }
    }
}
