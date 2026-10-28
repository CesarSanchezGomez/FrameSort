package com.cesarcosmico.framesort.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;

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
        PageArgument.optional(node, (context, page) -> send(context, path, page));
    }

    private int send(CommandContext<CommandSourceStack> context, String path, int page) {
        listing.send(context.getSource().getSender(), "", page, path + " %d");
        return Command.SINGLE_SUCCESS;
    }
}
