package com.cesarcosmico.framesort.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import java.util.Locale;

public final class TagSearchCommand implements CommandFeature {

    private final TagListing listing;

    public TagSearchCommand(TagListing listing) {
        this.listing = listing;
    }

    @Override
    public String id() {
        return "tag-search";
    }

    @Override
    public void attach(LiteralArgumentBuilder<CommandSourceStack> node, String path) {
        node.then(Commands.argument("text", StringArgumentType.word())
                .executes(context -> send(context, path, 1))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .executes(context -> send(context, path, IntegerArgumentType.getInteger(context, "page")))));
    }

    private int send(CommandContext<CommandSourceStack> context, String path, int page) {
        String search = StringArgumentType.getString(context, "text");
        listing.send(context.getSource().getSender(), search, page,
                path + " " + search.toLowerCase(Locale.ROOT) + " %d");
        return Command.SINGLE_SUCCESS;
    }
}
