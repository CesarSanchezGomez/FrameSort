package com.cesarcosmico.framesort.command;

import com.cesarcosmico.framesort.service.AutoMarkService;
import com.cesarcosmico.framesort.text.Messages;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.entity.Player;

import java.util.function.Supplier;

public final class AutomarkCommand implements CommandFeature {

    private final AutoMarkService autoMark;
    private final Supplier<Messages> messages;

    public AutomarkCommand(AutoMarkService autoMark, Supplier<Messages> messages) {
        this.autoMark = autoMark;
        this.messages = messages;
    }

    @Override
    public String id() {
        return "automark";
    }

    @Override
    public void attach(LiteralArgumentBuilder<CommandSourceStack> node, String path) {
        node.executes(this::toggle);
    }

    private int toggle(CommandContext<CommandSourceStack> context) {
        if (!(context.getSource().getSender() instanceof Player player)) {
            context.getSource().getSender().sendMessage(messages.get().get("command.players-only"));
            return Command.SINGLE_SUCCESS;
        }
        player.sendMessage(messages.get().get(autoMark.toggle(player) ? "automark.on" : "automark.off"));
        return Command.SINGLE_SUCCESS;
    }
}
