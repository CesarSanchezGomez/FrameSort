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

import java.util.function.Supplier;

public final class TraceCommand implements CommandFeature {

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
    public void attach(LiteralArgumentBuilder<CommandSourceStack> node, String path) {
        node.executes(context -> start(context, settings.get().inspect().traceDefaultSeconds()))
                .then(Commands.literal("stop").executes(this::stop))
                .then(Commands.argument("seconds", IntegerArgumentType.integer(1))
                        .executes(context -> start(context, IntegerArgumentType.getInteger(context, "seconds"))));
    }

    private int start(CommandContext<CommandSourceStack> context, int seconds) {
        if (!(context.getSource().getSender() instanceof Player player)) {
            context.getSource().getSender().sendMessage(messages.get().get("command.players-only"));
            return Command.SINGLE_SUCCESS;
        }
        int granted = trace.start(player, seconds);
        player.sendMessage(messages.get().get("trace.started",
                Placeholder.unparsed("seconds", String.valueOf(granted)),
                Placeholder.unparsed("radius", String.valueOf(settings.get().inspect().traceRadius()))));
        return Command.SINGLE_SUCCESS;
    }

    private int stop(CommandContext<CommandSourceStack> context) {
        if (!(context.getSource().getSender() instanceof Player player)) {
            context.getSource().getSender().sendMessage(messages.get().get("command.players-only"));
            return Command.SINGLE_SUCCESS;
        }
        player.sendMessage(messages.get().get(trace.stop(player) ? "trace.stopped" : "trace.not-running"));
        return Command.SINGLE_SUCCESS;
    }
}
