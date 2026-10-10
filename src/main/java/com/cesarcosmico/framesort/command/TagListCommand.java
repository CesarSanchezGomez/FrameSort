package com.cesarcosmico.framesort.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

public final class TagListCommand implements CommandFeature {

    private final TagListing listing;

    public TagListCommand(TagListing listing) {
        this.listing = listing;
    }

    @Override
    public String id() {
        return "tag-list";
    }

    @Override
    public void attach(LiteralArgumentBuilder<CommandSourceStack> node, String path) {
        node.executes(context -> send(context, path, 1))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .executes(context -> send(context, path, IntegerArgumentType.getInteger(context, "page"))));
    }

    private int send(CommandContext<CommandSourceStack> context, String path, int page) {
        listing.send(context.getSource().getSender(), "", page, path + " %d");
        return Command.SINGLE_SUCCESS;
    }
}
