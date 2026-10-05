package com.cesarcosmico.framesort.command;

import com.cesarcosmico.framesort.config.CommandSpec;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;

/** A command whose name, aliases, permissions and enabled state come from {@code commands.yml}. */
public interface PluginCommand {

    /** Stable id used as the {@code commands.yml} key; stays fixed when the command is renamed. */
    String id();

    String description();

    CommandSpec defaults();

    LiteralCommandNode<CommandSourceStack> build(CommandSpec spec);
}
