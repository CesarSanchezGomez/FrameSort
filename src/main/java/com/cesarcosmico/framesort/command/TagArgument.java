package com.cesarcosmico.framesort.command;

import com.cesarcosmico.framesort.service.TagCatalog;
import com.cesarcosmico.framesort.text.Messages;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.MessageComponentSerializer;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.CustomArgumentType;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.NamespacedKey;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

final class TagArgument implements CustomArgumentType.Converted<TagCatalog.TagView, NamespacedKey> {

    private final TagCatalog tags;
    private final Supplier<Messages> messages;

    TagArgument(TagCatalog tags, Supplier<Messages> messages) {
        this.tags = tags;
        this.messages = messages;
    }

    @Override
    public TagCatalog.TagView convert(NamespacedKey key) throws CommandSyntaxException {
        TagCatalog.TagView view = tags.find(key);
        if (view == null) {
            throw new SimpleCommandExceptionType(MessageComponentSerializer.message().serialize(
                    messages.get().get("tag.unknown", Placeholder.unparsed("tag", key.asString())))).create();
        }
        return view;
    }

    @Override
    public ArgumentType<NamespacedKey> getNativeType() {
        return ArgumentTypes.namespacedKey();
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        String typed = builder.getRemainingLowerCase();
        for (NamespacedKey name : tags.names()) {
            String full = name.asString();
            String shortName = TagCommand.shortName(name);
            if (full.startsWith(typed) || shortName.startsWith(typed)) {
                builder.suggest(full.startsWith(typed) && !shortName.startsWith(typed) ? full : shortName);
            }
        }
        return builder.buildFuture();
    }
}
