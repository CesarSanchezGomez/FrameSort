package com.cesarcosmico.framesort.command;

import com.cesarcosmico.framesort.text.Messages;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import java.util.function.Supplier;

public final class HelpCommand implements CommandFeature {

    private final Supplier<Messages> messages;

    public HelpCommand(Supplier<Messages> messages) {
        this.messages = messages;
    }

    @Override
    public String id() {
        return "help";
    }

    @Override
    public void attach(LiteralArgumentBuilder<CommandSourceStack> node, String path) {
        node.executes(context -> {
            context.getSource().getSender().sendMessage(messages.get().get("command.help"));
            return Command.SINGLE_SUCCESS;
        });
    }
}
