package com.cesarcosmico.framesort.command;

import com.cesarcosmico.framesort.config.CommandsConfig;
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
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.NamespacedKey;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

final class TagArgument implements CustomArgumentType.Converted<TagCatalog.TagView, NamespacedKey> {

    private final TagCatalog tags;
    private final Supplier<Messages> messages;
    private final CommandsConfig commands;

    TagArgument(TagCatalog tags, Supplier<Messages> messages, CommandsConfig commands) {
        this.tags = tags;
        this.messages = messages;
        this.commands = commands;
    }

    @Override
    public TagCatalog.TagView convert(NamespacedKey key) throws CommandSyntaxException {
        TagCatalog.TagView view = tags.find(key);
        if (view == null) {
            Messages text = messages.get();
            // Parsing has no sender, so the hint names the list's path without checking who may run it.
            String list = commands.primaryUsage("tag-list");
            Component hint = list == null ? Component.empty()
                    : text.get("tag.unknown-hint", Placeholder.unparsed("usage", list));
            throw new SimpleCommandExceptionType(MessageComponentSerializer.message().serialize(text.get("tag.unknown",
                    Placeholder.unparsed("tag", key.asString()), Placeholder.component("hint", hint)))).create();
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
            String shortName = shortName(name);
            if (full.startsWith(typed) || shortName.startsWith(typed)) {
                builder.suggest(full.startsWith(typed) && !shortName.startsWith(typed) ? full : shortName);
            }
        }
        return builder.buildFuture();
    }

    static String shortName(NamespacedKey key) {
        return NamespacedKey.MINECRAFT.equals(key.getNamespace()) ? key.getKey() : key.asString();
    }
}
