package com.cesarcosmico.framesort.command;

import com.cesarcosmico.framesort.config.CommandsConfig;
import com.cesarcosmico.framesort.text.Messages;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * One message from {@code command.help.layout}, whose {@code <entries>} is one line per feature
 * ({@code command.help.<id>}), only those available that the sender may run.
 */
public final class HelpCommand implements CommandFeature {

    private final Supplier<Messages> messages;
    private final CommandsConfig commands;
    private final List<String> listed;

    /** @param listed the feature ids to list, in order; the help itself is usually left out */
    public HelpCommand(Supplier<Messages> messages, CommandsConfig commands, List<String> listed) {
        this.messages = messages;
        this.commands = commands;
        this.listed = List.copyOf(listed);
    }

    @Override
    public String id() {
        return "help";
    }

    @Override
    public void attach(LiteralArgumentBuilder<CommandSourceStack> node, String path) {
        node.executes(context -> {
            CommandSender sender = context.getSource().getSender();
            List<Component> lines = new ArrayList<>();
            for (String id : listed) {
                String usage = commands.usageFor(id, sender::hasPermission);
                if (usage != null) {
                    lines.add(messages.get().get("command.help." + id, Placeholder.unparsed("usage", usage)));
                }
            }
            sender.sendMessage(messages.get().get("command.help.layout",
                    Placeholder.component("entries", Component.join(JoinConfiguration.newlines(), lines))));
            return Command.SINGLE_SUCCESS;
        });
    }
}
