package com.cesarcosmico.framesort.command;

import com.cesarcosmico.framesort.config.FrameSortSettings;
import com.cesarcosmico.framesort.service.TagCatalog;
import com.cesarcosmico.framesort.text.ChatPager;
import com.cesarcosmico.framesort.text.Messages;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

final class TagShowCommand {

    private final TagArgument tag;
    private final Supplier<Messages> messages;
    private final Supplier<FrameSortSettings> settings;

    TagShowCommand(TagArgument tag, Supplier<Messages> messages, Supplier<FrameSortSettings> settings) {
        this.tag = tag;
        this.messages = messages;
        this.settings = settings;
    }

    LiteralArgumentBuilder<CommandSourceStack> show(String path) {
        return Commands.literal("show")
                .then(Commands.argument("tag", tag)
                        .executes(context -> send(context, path, 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(context -> send(context, path,
                                        IntegerArgumentType.getInteger(context, "page")))));
    }

    private int send(CommandContext<CommandSourceStack> context, String path, int page) {
        TagCatalog.TagView view = context.getArgument("tag", TagCatalog.TagView.class);
        CommandSender sender = context.getSource().getSender();
        Messages text = messages.get();
        List<Component> lines = view.materials().stream().map(material -> entry(text, material)).toList();
        String name = TagArgument.shortName(view.key());
        // Applying needs a hand, so only players get the button.
        Component apply = sender instanceof Player
                ? text.get("tag.filter-button").clickEvent(ClickEvent.runCommand(path + " apply " + name))
                : Component.empty();
        Component title = text.get("tag.title",
                Placeholder.unparsed("tag", name),
                Placeholder.component("kind", text.get("tag.kind." + view.kind().name().toLowerCase(Locale.ROOT))),
                Placeholder.component("filter", apply));
        ChatPager.send(sender, text, title, lines, page, settings.get().inspect().pageSize(),
                path + " show " + name + " %d");
        return Command.SINGLE_SUCCESS;
    }

    private static Component entry(Messages text, Material material) {
        return text.get("tag.entry", Placeholder.component("item", Component.translatable(material)))
                .hoverEvent(HoverEvent.showText(Component.text(material.getKey().asString())));
    }
}
