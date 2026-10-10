package com.cesarcosmico.framesort.command;

import com.cesarcosmico.framesort.config.CommandsConfig;
import com.cesarcosmico.framesort.text.Messages;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.function.Supplier;

/** Lists one line per feature ({@code command.help.<id>}), only those enabled that the sender may run. */
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
            Component help = messages.get().get("command.help.header");
            for (String id : listed) {
                String usage = commands.usageFor(id, sender::hasPermission);
                if (usage != null) {
                    help = help.appendNewline().append(messages.get().get("command.help." + id,
                            Placeholder.unparsed("usage", usage)));
                }
            }
            sender.sendMessage(help);
            return Command.SINGLE_SUCCESS;
        });
    }
}
