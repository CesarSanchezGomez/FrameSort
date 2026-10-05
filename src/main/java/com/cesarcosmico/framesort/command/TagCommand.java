package com.cesarcosmico.framesort.command;

import com.cesarcosmico.framesort.config.FrameSortSettings;
import com.cesarcosmico.framesort.service.TagCatalog;
import com.cesarcosmico.framesort.text.ChatPager;
import com.cesarcosmico.framesort.text.Messages;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class TagCommand implements Subcommand {

    private final TagCatalog tags;
    private final Supplier<Messages> messages;
    private final Supplier<FrameSortSettings> settings;

    public TagCommand(TagCatalog tags, Supplier<Messages> messages, Supplier<FrameSortSettings> settings) {
        this.tags = tags;
        this.messages = messages;
        this.settings = settings;
    }

    @Override
    public String id() {
        return "tag";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> node(String root, @Nullable String permission) {
        return Commands.literal("tag")
                .requires(source -> FrameSortCommand.allowed(source.getSender(), permission))
                .then(Commands.argument("tag", ArgumentTypes.namespacedKey())
                        .suggests(this::suggestTags)
                        .executes(context -> showTag(context, root, 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(context -> showTag(context, root,
                                        IntegerArgumentType.getInteger(context, "page")))));
    }

    private int showTag(CommandContext<CommandSourceStack> context, String root, int page) {
        NamespacedKey key = context.getArgument("tag", NamespacedKey.class);
        Messages text = messages.get();
        TagCatalog.TagView view = tags.find(key);
        if (view == null) {
            context.getSource().getSender().sendMessage(text.get("tag.unknown",
                    Placeholder.unparsed("tag", key.asString())));
            return Command.SINGLE_SUCCESS;
        }
        List<Component> lines = view.materials().stream().map(material -> entry(text, material)).toList();
        String name = shortName(view.key());
        Component title = text.get("tag.title",
                Placeholder.unparsed("tag", name),
                Placeholder.component("kind", text.get("tag.kind." + view.kind().name().toLowerCase(Locale.ROOT))));
        ChatPager.send(context.getSource().getSender(), text, title, lines, page,
                settings.get().inspect().pageSize(), "/" + root + " tag " + name + " %d");
        return Command.SINGLE_SUCCESS;
    }

    private CompletableFuture<Suggestions> suggestTags(CommandContext<CommandSourceStack> context,
                                                       SuggestionsBuilder builder) {
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

    private static Component entry(Messages text, Material material) {
        return text.get("tag.entry", Placeholder.component("item", Component.translatable(material)))
                .hoverEvent(HoverEvent.showText(Component.text(material.getKey().asString())));
    }

    // Vanilla tags are shown without the "minecraft:" namespace, as players type them.
    static String shortName(NamespacedKey key) {
        return NamespacedKey.MINECRAFT.equals(key.getNamespace()) ? key.getKey() : key.asString();
    }
}
