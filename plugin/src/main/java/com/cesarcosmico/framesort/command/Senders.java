package com.cesarcosmico.framesort.command;

import com.cesarcosmico.framesort.text.Messages;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

final class Senders {

    private Senders() {
    }

    // The console may run any path, so a command that needs a player says so instead of failing.
    static @Nullable Player player(CommandContext<CommandSourceStack> context, Messages messages) {
        if (context.getSource().getSender() instanceof Player player) {
            return player;
        }
        context.getSource().getSender().sendMessage(messages.get("command.players-only"));
        return null;
    }
}
