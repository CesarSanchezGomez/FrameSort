package com.cesarcosmico.framesort.command;

import com.cesarcosmico.framesort.config.FrameSortSettings;
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
import org.bukkit.Material;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** A container that can be a sorter, by its lower-case material name. */
final class SorterBlockArgument implements CustomArgumentType.Converted<Material, String> {

    private final Supplier<FrameSortSettings> settings;
    private final Supplier<Messages> messages;

    SorterBlockArgument(Supplier<FrameSortSettings> settings, Supplier<Messages> messages) {
        this.settings = settings;
        this.messages = messages;
    }

    @Override
    public Material convert(String name) throws CommandSyntaxException {
        Material material = Material.matchMaterial(name);
        if (material == null || !settings.get().sorter().blocks().contains(material)) {
            throw new SimpleCommandExceptionType(MessageComponentSerializer.message().serialize(
                    messages.get().get("give.unknown-block", Placeholder.unparsed("block", name)))).create();
        }
        return material;
    }

    @Override
    public ArgumentType<String> getNativeType() {
        return StringArgumentType.word();
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        String typed = builder.getRemainingLowerCase();
        for (Material material : settings.get().sorter().blocks()) {
            String name = material.name().toLowerCase(Locale.ROOT);
            if (name.startsWith(typed)) {
                builder.suggest(name);
            }
        }
        return builder.buildFuture();
    }
}
