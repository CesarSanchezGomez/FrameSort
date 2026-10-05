package com.cesarcosmico.framesort.command;

import com.cesarcosmico.framesort.service.InspectService;
import com.cesarcosmico.framesort.text.Messages;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

public final class InspectCommand implements Subcommand {

    private final InspectService inspect;
    private final Supplier<Messages> messages;

    public InspectCommand(InspectService inspect, Supplier<Messages> messages) {
        this.inspect = inspect;
        this.messages = messages;
    }

    @Override
    public String id() {
        return "inspect";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> node(String root, @Nullable String permission) {
        return Commands.literal("inspect")
                .requires(source -> source.getSender() instanceof Player
                        && FrameSortCommand.allowed(source.getSender(), permission))
                .then(Commands.argument("page", IntegerArgumentType.integer(1)).executes(this::page));
    }

    private int page(CommandContext<CommandSourceStack> context) {
        Player player = (Player) context.getSource().getSender();
        if (!inspect.showPage(player, IntegerArgumentType.getInteger(context, "page"))) {
            player.sendMessage(messages.get().get("inspect.no-listing"));
        }
        return Command.SINGLE_SUCCESS;
    }
}
