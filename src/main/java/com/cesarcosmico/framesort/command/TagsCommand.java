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
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/** {@code tags [page]} and {@code tags search <text> [page]}: every tag, each one clickable to see its contents. */
public final class TagsCommand implements Subcommand {

    private final TagCatalog tags;
    private final Supplier<Messages> messages;
    private final Supplier<FrameSortSettings> settings;

    public TagsCommand(TagCatalog tags, Supplier<Messages> messages, Supplier<FrameSortSettings> settings) {
        this.tags = tags;
        this.messages = messages;
        this.settings = settings;
    }

    @Override
    public String id() {
        return "tags";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> node(String root, @Nullable String permission) {
        return Commands.literal("tags")
                .requires(source -> FrameSortCommand.allowed(source.getSender(), permission))
                .executes(context -> list(context, root, "", 1))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .executes(context -> list(context, root, "", IntegerArgumentType.getInteger(context, "page"))))
                .then(Commands.literal("search")
                        .then(Commands.argument("text", StringArgumentType.word())
                                .executes(context -> list(context, root,
                                        StringArgumentType.getString(context, "text"), 1))
                                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                        .executes(context -> list(context, root,
                                                StringArgumentType.getString(context, "text"),
                                                IntegerArgumentType.getInteger(context, "page"))))));
    }

    private int list(CommandContext<CommandSourceStack> context, String root, String search, int page) {
        Messages text = messages.get();
        String needle = search.toLowerCase(Locale.ROOT);
        List<Component> lines = tags.names().stream()
                .map(TagCommand::shortName)
                .filter(name -> name.contains(needle))
                .map(name -> text.get("tags.entry", Placeholder.unparsed("tag", name))
                        .clickEvent(ClickEvent.runCommand("/" + root + " tag " + name))
                        .hoverEvent(HoverEvent.showText(text.get("tags.hover"))))
                .toList();
        Component title = needle.isEmpty()
                ? text.get("tags.title")
                : text.get("tags.title-search", Placeholder.unparsed("text", search));
        String pageCommand = needle.isEmpty()
                ? "/" + root + " tags %d"
                : "/" + root + " tags search " + needle + " %d";
        ChatPager.send(context.getSource().getSender(), text, title, lines, page,
                settings.get().inspect().pageSize(), pageCommand);
        return Command.SINGLE_SUCCESS;
    }
}
