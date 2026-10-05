package com.cesarcosmico.framesort.command;

import com.cesarcosmico.framesort.config.CommandSpec;
import com.cesarcosmico.framesort.text.Messages;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/** {@code /framesort}: help, plus one subcommand class per feature and {@code reload}/{@code version} here. */
public final class FrameSortCommand implements PluginCommand {

    public static final CommandSpec DEFAULTS = new CommandSpec(true, "framesort", List.of("fs"),
            "framesort.command.help", Map.of(
                    "tag", "framesort.command.tag",
                    "tags", "framesort.command.tags",
                    "trace", "framesort.command.trace",
                    "inspect", "framesort.command.inspect",
                    "give", "framesort.command.give",
                    "reload", "framesort.command.reload",
                    "version", "framesort.command.version"));

    @FunctionalInterface
    public interface ReloadAction {
        void reload() throws Exception;
    }

    private final Supplier<Messages> messages;
    private final List<Subcommand> subcommands;
    private final String pluginVersion;
    private final ReloadAction reloadAction;
    private final Logger logger;

    public FrameSortCommand(Supplier<Messages> messages, List<Subcommand> subcommands, String pluginVersion,
                            ReloadAction reloadAction, Logger logger) {
        this.messages = messages;
        this.subcommands = List.copyOf(subcommands);
        this.pluginVersion = pluginVersion;
        this.reloadAction = reloadAction;
        this.logger = logger;
    }

    @Override
    public String id() {
        return "framesort";
    }

    @Override
    public String description() {
        return "Sort items into item frames";
    }

    @Override
    public CommandSpec defaults() {
        return DEFAULTS;
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build(CommandSpec spec) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(spec.name())
                .requires(source -> allowed(source.getSender(), spec.permission())
                        || spec.subPermissions().values().stream().anyMatch(p -> allowed(source.getSender(), p)))
                .executes(this::help);
        for (Subcommand subcommand : subcommands) {
            root.then(subcommand.node(spec.name(), spec.subPermission(subcommand.id())));
        }
        return root
                .then(Commands.literal("reload")
                        .requires(source -> allowed(source.getSender(), spec.subPermission("reload")))
                        .executes(this::reload))
                .then(Commands.literal("version")
                        .requires(source -> allowed(source.getSender(), spec.subPermission("version")))
                        .executes(this::version))
                .build();
    }

    static boolean allowed(CommandSender sender, @Nullable String permission) {
        return permission != null && sender.hasPermission(permission);
    }

    private int help(CommandContext<CommandSourceStack> context) {
        context.getSource().getSender().sendMessage(messages.get().get("command.help"));
        return Command.SINGLE_SUCCESS;
    }

    private int reload(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        try {
            reloadAction.reload();
            sender.sendMessage(messages.get().get("command.reloaded"));
        } catch (Exception e) {
            logger.log(Level.WARNING, "FrameSort reload failed; the previous configuration is still active.", e);
            sender.sendMessage(messages.get().get("command.reload-failed",
                    Placeholder.unparsed("error", String.valueOf(e.getMessage()))));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int version(CommandContext<CommandSourceStack> context) {
        context.getSource().getSender().sendMessage(messages.get().get("command.version",
                Placeholder.unparsed("version", pluginVersion)));
        return Command.SINGLE_SUCCESS;
    }
}
