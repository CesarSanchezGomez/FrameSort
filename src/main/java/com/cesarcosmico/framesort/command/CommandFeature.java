package com.cesarcosmico.framesort.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;

/** Something a command does, placed by {@code commands.yml} at any number of paths. */
public interface CommandFeature {

    /** The {@code commands.yml} key; it stays fixed when the paths change. */
    String id();

    /**
     * Adds this feature's command and arguments to the literal at the end of one of its paths. {@link CommandTree}
     * checks the permission, so features don't.
     *
     * @param path that path with its slash, such as {@code /fs tag}, for clickable links back into the command
     */
    void attach(LiteralArgumentBuilder<CommandSourceStack> node, String path);
}
