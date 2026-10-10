package com.cesarcosmico.framesort.command;

import com.cesarcosmico.framesort.config.CommandsConfig;
import com.cesarcosmico.framesort.config.FrameSortSettings;
import com.cesarcosmico.framesort.service.TagCatalog;
import com.cesarcosmico.framesort.text.ChatPager;
import com.cesarcosmico.framesort.text.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

// The page that tag-list and tag-search both send; search only narrows the same list.
public final class TagListing {

    private final TagCatalog tags;
    private final Supplier<Messages> messages;
    private final Supplier<FrameSortSettings> settings;
    private final CommandsConfig commands;

    public TagListing(TagCatalog tags, Supplier<Messages> messages, Supplier<FrameSortSettings> settings,
                      CommandsConfig commands) {
        this.tags = tags;
        this.messages = messages;
        this.settings = settings;
        this.commands = commands;
    }

    void send(CommandSender sender, String search, int page, String pageCommand) {
        Messages text = messages.get();
        String needle = search.toLowerCase(Locale.ROOT);
        String show = commands.usageFor("tag-show", sender::hasPermission);
        List<Component> lines = tags.names().stream()
                .map(TagArgument::shortName)
                .filter(name -> name.contains(needle))
                .map(name -> entry(text, name, show))
                .toList();
        Component title = needle.isEmpty()
                ? text.get("tag.list.title")
                : text.get("tag.list.title-search", Placeholder.unparsed("text", search));
        ChatPager.send(sender, text, title, lines, page, settings.get().inspect().pageSize(), pageCommand);
    }

    private static Component entry(Messages text, String name, @Nullable String show) {
        Component entry = text.get("tag.list.entry", Placeholder.unparsed("tag", name));
        return show == null ? entry
                : entry.clickEvent(ClickEvent.runCommand(show + " " + name))
                        .hoverEvent(HoverEvent.showText(text.get("tag.list.hover")));
    }
}
