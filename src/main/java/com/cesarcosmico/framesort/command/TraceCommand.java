package com.cesarcosmico.framesort.command;

import com.cesarcosmico.framesort.config.FrameSortSettings;
import com.cesarcosmico.framesort.service.TraceService;
import com.cesarcosmico.framesort.text.Messages;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

public final class TraceCommand implements Subcommand {

    private final TraceService trace;
    private final Supplier<Messages> messages;
    private final Supplier<FrameSortSettings> settings;

    public TraceCommand(TraceService trace, Supplier<Messages> messages, Supplier<FrameSortSettings> settings) {
        this.trace = trace;
        this.messages = messages;
        this.settings = settings;
    }

    @Override
    public String id() {
        return "trace";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> node(String root, @Nullable String permission) {
        return Commands.literal("trace")
                .requires(source -> source.getSender() instanceof Player
                        && FrameSortCommand.allowed(source.getSender(), permission))
                .executes(context -> start(context, settings.get().inspect().traceDefaultSeconds()))
                .then(Commands.literal("stop").executes(this::stop))
                .then(Commands.argument("seconds", IntegerArgumentType.integer(1))
                        .executes(context -> start(context, IntegerArgumentType.getInteger(context, "seconds"))));
    }

    private int start(CommandContext<CommandSourceStack> context, int seconds) {
        Player player = (Player) context.getSource().getSender();
        int granted = trace.start(player, seconds);
        player.sendMessage(messages.get().get("trace.started",
                Placeholder.unparsed("seconds", String.valueOf(granted)),
                Placeholder.unparsed("radius", String.valueOf(settings.get().inspect().traceRadius()))));
        return Command.SINGLE_SUCCESS;
    }

    private int stop(CommandContext<CommandSourceStack> context) {
        Player player = (Player) context.getSource().getSender();
        player.sendMessage(messages.get().get(trace.stop(player) ? "trace.stopped" : "trace.not-running"));
        return Command.SINGLE_SUCCESS;
    }
}
