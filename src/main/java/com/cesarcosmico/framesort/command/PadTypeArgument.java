package com.cesarcosmico.framesort.command;

import com.cesarcosmico.framesort.config.PadSettings;
import com.cesarcosmico.framesort.config.PadType;
import com.cesarcosmico.framesort.text.Messages;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.MessageComponentSerializer;
import io.papermc.paper.command.brigadier.argument.CustomArgumentType;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** A pad type id from {@code pads.yml}: parsed, suggested and reported in one place. */
final class PadTypeArgument implements CustomArgumentType.Converted<PadType, String> {

    private final Supplier<PadSettings> pads;
    private final Supplier<Messages> messages;

    PadTypeArgument(Supplier<PadSettings> pads, Supplier<Messages> messages) {
        this.pads = pads;
        this.messages = messages;
    }

    @Override
    public PadType convert(String id) throws CommandSyntaxException {
        PadType type = pads.get().type(id);
        if (type == null) {
            throw new SimpleCommandExceptionType(MessageComponentSerializer.message().serialize(
                    messages.get().get("give.unknown-pad", Placeholder.unparsed("type", id)))).create();
        }
        return type;
    }

    @Override
    public ArgumentType<String> getNativeType() {
        return StringArgumentType.word();
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        String typed = builder.getRemainingLowerCase();
        for (String id : pads.get().types().keySet()) {
            if (id.startsWith(typed)) {
                builder.suggest(id);
            }
        }
        return builder.buildFuture();
    }
}
