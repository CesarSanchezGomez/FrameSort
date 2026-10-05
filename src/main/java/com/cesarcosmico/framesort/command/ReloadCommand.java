package com.cesarcosmico.framesort.command;

import com.cesarcosmico.framesort.text.Messages;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;

import java.util.concurrent.Callable;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ReloadCommand implements CommandFeature {

    private final Supplier<Messages> messages;
    private final Callable<Integer> reload;
    private final Logger logger;

    /** @param reload reloads everything and returns how many settings were invalid and fell back to defaults */
    public ReloadCommand(Supplier<Messages> messages, Callable<Integer> reload, Logger logger) {
        this.messages = messages;
        this.reload = reload;
        this.logger = logger;
    }

    @Override
    public String id() {
        return "reload";
    }

    @Override
    public void attach(LiteralArgumentBuilder<CommandSourceStack> node, String path) {
        node.executes(this::reload);
    }

    private int reload(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        int warnings;
        try {
            warnings = reload.call();
        } catch (Exception e) {
            logger.log(Level.WARNING, "FrameSort reload failed; the previous configuration is still active.", e);
            sender.sendMessage(messages.get().get("command.reload-failed",
                    Placeholder.unparsed("error", String.valueOf(e.getMessage()))));
            return Command.SINGLE_SUCCESS;
        }
        sender.sendMessage(warnings == 0
                ? messages.get().get("command.reloaded")
                : messages.get().get("command.reloaded-with-warnings",
                        Placeholder.unparsed("count", String.valueOf(warnings))));
        return Command.SINGLE_SUCCESS;
    }
}
