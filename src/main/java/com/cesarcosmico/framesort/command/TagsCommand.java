package com.cesarcosmico.framesort.command;

import com.cesarcosmico.framesort.config.CommandsConfig;
import com.cesarcosmico.framesort.config.FrameSortSettings;
import com.cesarcosmico.framesort.service.TagCatalog;
import com.cesarcosmico.framesort.text.ChatPager;
import com.cesarcosmico.framesort.text.Messages;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

public final class TagsCommand implements CommandFeature {

    private final TagCatalog tags;
    private final Supplier<Messages> messages;
    private final Supplier<FrameSortSettings> settings;
    private final CommandsConfig commands;

    public TagsCommand(TagCatalog tags, Supplier<Messages> messages, Supplier<FrameSortSettings> settings,
                       CommandsConfig commands) {
        this.tags = tags;
        this.messages = messages;
        this.settings = settings;
        this.commands = commands;
    }

    @Override
    public String id() {
        return "tags";
    }

    @Override
    public void attach(LiteralArgumentBuilder<CommandSourceStack> node, String path) {
        node.executes(context -> list(context, path, "", 1))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .executes(context -> list(context, path, "", IntegerArgumentType.getInteger(context, "page"))))
                .then(Commands.literal("search")
                        .then(Commands.argument("text", StringArgumentType.word())
                                .executes(context -> list(context, path,
                                        StringArgumentType.getString(context, "text"), 1))
                                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                        .executes(context -> list(context, path,
                                                StringArgumentType.getString(context, "text"),
                                                IntegerArgumentType.getInteger(context, "page"))))));
    }

    private int list(CommandContext<CommandSourceStack> context, String path, String search, int page) {
        Messages text = messages.get();
        String needle = search.toLowerCase(Locale.ROOT);
        String tagCommand = commands.primaryUsage("tag");
        List<Component> lines = tags.names().stream()
                .map(TagCommand::shortName)
                .filter(name -> name.contains(needle))
                .map(name -> entry(text, name, tagCommand))
                .toList();
        Component title = needle.isEmpty()
                ? text.get("tags.title")
                : text.get("tags.title-search", Placeholder.unparsed("text", search));
        String pageCommand = needle.isEmpty()
                ? path + " %d"
                : path + " search " + needle + " %d";
        ChatPager.send(context.getSource().getSender(), text, title, lines, page,
                settings.get().inspect().pageSize(), pageCommand);
        return Command.SINGLE_SUCCESS;
    }

    // Entries open the tag feature wherever commands.yml put it; with that feature disabled they are plain text.
    private static Component entry(Messages text, String name, @Nullable String tagCommand) {
        Component entry = text.get("tags.entry", Placeholder.unparsed("tag", name));
        return tagCommand == null ? entry
                : entry.clickEvent(ClickEvent.runCommand(tagCommand + " " + name))
                        .hoverEvent(HoverEvent.showText(text.get("tags.hover")));
    }
}
