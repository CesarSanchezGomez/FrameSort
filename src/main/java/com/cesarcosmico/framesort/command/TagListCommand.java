package com.cesarcosmico.framesort.command;

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

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

// list and search are one listing, search only narrows it, so both live here.
final class TagListCommand {

    private final TagCatalog tags;
    private final Supplier<Messages> messages;
    private final Supplier<FrameSortSettings> settings;

    TagListCommand(TagCatalog tags, Supplier<Messages> messages, Supplier<FrameSortSettings> settings) {
        this.tags = tags;
        this.messages = messages;
        this.settings = settings;
    }

    LiteralArgumentBuilder<CommandSourceStack> list(String path) {
        return Commands.literal("list")
                .executes(context -> send(context, path, "", 1))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .executes(context -> send(context, path, "", IntegerArgumentType.getInteger(context, "page"))));
    }

    LiteralArgumentBuilder<CommandSourceStack> search(String path) {
        return Commands.literal("search")
                .then(Commands.argument("text", StringArgumentType.word())
                        .executes(context -> send(context, path, StringArgumentType.getString(context, "text"), 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(context -> send(context, path,
                                        StringArgumentType.getString(context, "text"),
                                        IntegerArgumentType.getInteger(context, "page")))));
    }

    private int send(CommandContext<CommandSourceStack> context, String path, String search, int page) {
        Messages text = messages.get();
        String needle = search.toLowerCase(Locale.ROOT);
        List<Component> lines = tags.names().stream()
                .map(TagArgument::shortName)
                .filter(name -> name.contains(needle))
                .map(name -> entry(text, name, path))
                .toList();
        Component title = needle.isEmpty()
                ? text.get("tag.list.title")
                : text.get("tag.list.title-search", Placeholder.unparsed("text", search));
        String pageCommand = needle.isEmpty() ? path + " list %d" : path + " search " + needle + " %d";
        ChatPager.send(context.getSource().getSender(), text, title, lines, page,
                settings.get().inspect().pageSize(), pageCommand);
        return Command.SINGLE_SUCCESS;
    }

    private static Component entry(Messages text, String name, String path) {
        return text.get("tag.list.entry", Placeholder.unparsed("tag", name))
                .clickEvent(ClickEvent.runCommand(path + " show " + name))
                .hoverEvent(HoverEvent.showText(text.get("tag.list.hover")));
    }
}
