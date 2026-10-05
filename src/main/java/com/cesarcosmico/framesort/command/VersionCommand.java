package com.cesarcosmico.framesort.command;

import com.cesarcosmico.framesort.text.Messages;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import java.util.function.Supplier;

public final class VersionCommand implements CommandFeature {

    private final Supplier<Messages> messages;
    private final String version;

    public VersionCommand(Supplier<Messages> messages, String version) {
        this.messages = messages;
        this.version = version;
    }

    @Override
    public String id() {
        return "version";
    }

    @Override
    public void attach(LiteralArgumentBuilder<CommandSourceStack> node, String path) {
        node.executes(context -> {
            context.getSource().getSender().sendMessage(messages.get().get("command.version",
                    Placeholder.unparsed("version", version)));
            return Command.SINGLE_SUCCESS;
        });
    }
}
