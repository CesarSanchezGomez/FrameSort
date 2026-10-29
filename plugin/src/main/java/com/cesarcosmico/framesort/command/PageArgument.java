package com.cesarcosmico.framesort.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import java.util.function.ToIntBiFunction;

final class PageArgument {

    private static final String NAME = "page";

    private PageArgument() {
    }

    static <T extends ArgumentBuilder<CommandSourceStack, T>> T optional(
            T node, ToIntBiFunction<CommandContext<CommandSourceStack>, Integer> send) {
        return node.executes(context -> send.applyAsInt(context, 1))
                .then(Commands.argument(NAME, IntegerArgumentType.integer(1))
                        .executes(context -> send.applyAsInt(context, IntegerArgumentType.getInteger(context, NAME))));
    }
}
