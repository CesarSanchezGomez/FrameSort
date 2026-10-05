package com.cesarcosmico.framesort.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.jspecify.annotations.Nullable;

public interface Subcommand {

    String id();

    /**
     * @param root       the configured root command name, for clickable links back into the command
     * @param permission the permission for this subcommand, or {@code null} when none is configured
     */
    LiteralArgumentBuilder<CommandSourceStack> node(String root, @Nullable String permission);
}
