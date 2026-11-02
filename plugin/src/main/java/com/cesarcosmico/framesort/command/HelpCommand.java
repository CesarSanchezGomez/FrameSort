package com.cesarcosmico.framesort.command;

import com.cesarcosmico.framesort.config.CommandsConfig;
import com.cesarcosmico.framesort.text.ChatPager;
import com.cesarcosmico.framesort.text.Messages;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * A page of {@code command.help.layout}, whose {@code <entries>} is one line per feature
 * ({@code command.help.<id>}), only those available that the sender may run.
 */
public final class HelpCommand implements CommandFeature {

    private final Supplier<Messages> messages;
    private final CommandsConfig commands;
    private final List<String> listed;
    private final IntSupplier pageSize;

    /**
     * @param listed   the feature ids to list, in order; the help itself is usually left out
     * @param pageSize read on every send, so a reload applies
     */
    public HelpCommand(Supplier<Messages> messages, CommandsConfig commands, List<String> listed,
                       IntSupplier pageSize) {
        this.messages = messages;
        this.commands = commands;
        this.listed = List.copyOf(listed);
        this.pageSize = pageSize;
    }

    @Override
    public String id() {
        return "help";
    }

    @Override
    public void attach(LiteralArgumentBuilder<CommandSourceStack> node, String path) {
        PageArgument.optional(node, (context, page) -> send(context.getSource().getSender(), path, page));
    }

    private int send(CommandSender sender, String path, int page) {
        Messages text = messages.get();
        List<Component> lines = new ArrayList<>();
        for (String id : listed) {
            String usage = commands.usageFor(id, sender::hasPermission);
            if (usage != null) {
                lines.add(text.get("command.help." + id, Placeholder.unparsed("usage", usage)));
            }
        }
        ChatPager.send(sender, text, "command.help.layout", lines, page, pageSize.getAsInt(), path + " %d");
        return Command.SINGLE_SUCCESS;
    }
}
