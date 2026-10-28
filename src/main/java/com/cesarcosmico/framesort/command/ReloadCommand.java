package com.cesarcosmico.framesort.command;

import com.cesarcosmico.framesort.text.Messages;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ReloadCommand implements CommandFeature {

    private final Supplier<Messages> messages;
    private final Supplier<CompletableFuture<Integer>> reload;
    private final Executor mainThread;
    private final Logger logger;
    private final String pluginName;

    /**
     * @param reload starts a reload, which completes with how many warnings it logged
     * @param mainThread where the result is told and the command lists are sent again
     */
    public ReloadCommand(Supplier<Messages> messages, Supplier<CompletableFuture<Integer>> reload, Executor mainThread,
                         Logger logger, String pluginName) {
        this.messages = messages;
        this.reload = reload;
        this.mainThread = mainThread;
        this.logger = logger;
        this.pluginName = pluginName;
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
        reload.get().whenCompleteAsync((warnings, error) -> {
            if (error != null) {
                Throwable cause = error instanceof CompletionException && error.getCause() != null
                        ? error.getCause() : error;
                logger.log(Level.WARNING, pluginName + " reload failed; the previous configuration is still active.",
                        cause);
                sender.sendMessage(messages.get().get("command.reload-failed",
                        Placeholder.unparsed("error", String.valueOf(cause.getMessage()))));
                return;
            }
            // A command may follow a setting the reload just changed, so each player's command list is sent again.
            for (Player player : sender.getServer().getOnlinePlayers()) {
                player.updateCommands();
            }
            sender.sendMessage(warnings == 0
                    ? messages.get().get("command.reloaded")
                    : messages.get().get("command.reloaded-with-warnings",
                            Placeholder.unparsed("count", String.valueOf(warnings))));
        }, mainThread);
        return Command.SINGLE_SUCCESS;
    }
}
