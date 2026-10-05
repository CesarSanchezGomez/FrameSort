package com.cesarcosmico.framesort.text;

import com.cesarcosmico.framesort.model.Page;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import java.util.List;

/**
 * Sends a long list one page at a time, framed by the {@code pager.layout} message, with clickable buttons to
 * move between pages. The whole page goes out as one message, so it stays together in the chat.
 */
public final class ChatPager {

    private ChatPager() {
    }

    /**
     * @param pageCommand the command that shows another page, with {@code %d} where the page number goes
     */
    public static void send(Audience audience, Messages messages, Component title, List<Component> lines,
                            int requested, int pageSize, String pageCommand) {
        Page page = Page.of(requested, lines.size(), pageSize);
        Component entries = lines.isEmpty()
                ? messages.get("pager.empty")
                : Component.join(JoinConfiguration.newlines(), lines.subList(page.from(), page.to()));
        Component previous = page.hasPrevious()
                ? button(messages, "pager.previous", pageCommand, page.number() - 1)
                : messages.get("pager.previous-disabled");
        Component next = page.hasNext()
                ? button(messages, "pager.next", pageCommand, page.number() + 1)
                : messages.get("pager.next-disabled");
        audience.sendMessage(messages.get("pager.layout",
                Placeholder.component("title", title),
                Placeholder.unparsed("total", String.valueOf(lines.size())),
                Placeholder.component("entries", entries),
                Placeholder.component("previous", previous),
                Placeholder.component("next", next),
                Placeholder.unparsed("page", "%02d".formatted(page.number())),
                Placeholder.unparsed("pages", "%02d".formatted(page.count()))));
    }

    private static Component button(Messages messages, String key, String pageCommand, int target) {
        return messages.get(key)
                .clickEvent(ClickEvent.runCommand(pageCommand.formatted(target)))
                .hoverEvent(HoverEvent.showText(messages.get(key + "-hover",
                        Placeholder.unparsed("page", String.valueOf(target)))));
    }
}
