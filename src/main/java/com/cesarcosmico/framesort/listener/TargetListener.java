package com.cesarcosmico.framesort.listener;

import com.cesarcosmico.framesort.service.AutoMarkService;
import com.cesarcosmico.framesort.service.TargetIndex;
import com.cesarcosmico.framesort.text.Messages;
import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import io.papermc.paper.event.player.PlayerItemFrameChangeEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.ItemFrame;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;

import java.util.function.Supplier;

public final class TargetListener implements Listener {

    private final TargetIndex index;
    private final AutoMarkService autoMark;
    private final Supplier<Messages> messages;

    public TargetListener(TargetIndex index, AutoMarkService autoMark, Supplier<Messages> messages) {
        this.index = index;
        this.autoMark = autoMark;
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

    // MONITOR: only an item that really went in ends the mark, or makes one; the event is left alone. Every item a
    // player puts in passes here, while an item can leave a frame with no event that confirms it (an arrow, another
    // plugin). The frame still holds the old item: the event's item is the one going in.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFrameChange(PlayerItemFrameChangeEvent event) {
        ItemFrame frame = event.getItemFrame();
        if (event.getAction() == PlayerItemFrameChangeEvent.ItemFrameChangeAction.PLACE) {
            ItemStack item = event.getItemStack();
            if (autoMark.itemPlaced(event.getPlayer(), frame, item)) {
                event.getPlayer().sendActionBar(messages.get().get("mark.auto",
                        Placeholder.component("item", item.effectiveName())));
            }
        } else {
            index.changed(frame.getWorld());
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
