package com.cesarcosmico.framesort.text;

import com.cesarcosmico.framesort.model.Page;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import java.util.List;

/** Sends a long list one page at a time, with clickable buttons to move between pages. */
public final class ChatPager {

    private ChatPager() {
    }

    /**
     * @param pageCommand the command that shows another page, with {@code %d} where the page number goes
     */
    public static void send(Audience audience, Messages messages, Component title, List<Component> lines,
                            int requested, int pageSize, String pageCommand) {
        Page page = Page.of(requested, lines.size(), pageSize);
        audience.sendMessage(messages.get("pager.header",
                Placeholder.component("title", title),
                Placeholder.unparsed("page", String.valueOf(page.number())),
                Placeholder.unparsed("pages", String.valueOf(page.count())),
                Placeholder.unparsed("total", String.valueOf(lines.size()))));
        if (lines.isEmpty()) {
            audience.sendMessage(messages.get("pager.empty"));
        }
        for (Component line : lines.subList(page.from(), page.to())) {
            audience.sendMessage(line);
        }
        if (page.count() > 1) {
            Component previous = page.hasPrevious()
                    ? button(messages, "pager.previous", pageCommand, page.number() - 1)
                    : messages.get("pager.previous-disabled");
            Component next = page.hasNext()
                    ? button(messages, "pager.next", pageCommand, page.number() + 1)
                    : messages.get("pager.next-disabled");
            audience.sendMessage(messages.get("pager.footer",
                    Placeholder.component("previous", previous),
                    Placeholder.component("next", next)));
        }
    }

    private static Component button(Messages messages, String key, String pageCommand, int target) {
        return messages.get(key).clickEvent(ClickEvent.runCommand(pageCommand.formatted(target)));
    }
}
