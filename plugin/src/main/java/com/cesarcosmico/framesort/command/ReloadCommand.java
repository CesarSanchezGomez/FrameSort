package com.cesarcosmico.framesort.command;

import com.cesarcosmico.framesort.text.Messages;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ReloadCommand implements CommandFeature {

    private final Supplier<Messages> messages;
    private final Supplier<CompletableFuture<Integer>> reload;
    private final Function<CommandSender, Executor> replies;
    private final Executor mainThread;
    private final Logger logger;
    private final String pluginName;
    private final AtomicBoolean reloading = new AtomicBoolean();

    /**
     * @param reload starts a reload, which completes with how many warnings it logged
     * @param replies where a sender is told the result
     * @param mainThread where the command lists are sent again
     */
    public ReloadCommand(Supplier<Messages> messages, Supplier<CompletableFuture<Integer>> reload,
                         Function<CommandSender, Executor> replies, Executor mainThread, Logger logger,
                         String pluginName) {
        this.messages = messages;
        this.reload = reload;
        this.replies = replies;
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
        // Two reloads at once could swap in the older files last.
        if (!reloading.compareAndSet(false, true)) {
            sender.sendMessage(messages.get().get("command.reload-running"));
            return Command.SINGLE_SUCCESS;
        }
        long start = System.nanoTime();
        // Logged here, not in the reply: a player who left gets no reply, and the failure must still be logged.
        CompletableFuture<Integer> done = reload.get().whenComplete((warnings, error) -> {
            reloading.set(false);
            if (error != null) {
                logger.log(Level.WARNING, pluginName + " reload failed; the previous configuration is still active.",
                        cause(error));
            }
        });
        // A command may follow a setting the reload just changed, so each player's command list is sent again.
        Server server = sender.getServer();
        done.thenRunAsync(() -> server.getOnlinePlayers().forEach(Player::updateCommands), mainThread);
        done.whenCompleteAsync((warnings, error) -> sender.sendMessage(error != null
                ? messages.get().get("command.reload-failed",
                        Placeholder.unparsed("error", String.valueOf(cause(error).getMessage())))
                : warnings == 0
                        ? messages.get().get("command.reloaded", elapsed(start))
                        : messages.get().get("command.reloaded-with-warnings",
                                Placeholder.unparsed("count", String.valueOf(warnings)), elapsed(start))),
                replies.apply(sender));
        return Command.SINGLE_SUCCESS;
    }

    private static TagResolver elapsed(long start) {
        return Placeholder.unparsed("ms", String.valueOf(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)));
    }

    private static Throwable cause(Throwable error) {
        return error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    }
}
