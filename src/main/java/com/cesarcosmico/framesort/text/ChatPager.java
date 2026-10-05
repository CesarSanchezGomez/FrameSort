package com.cesarcosmico.framesort.text;

import com.cesarcosmico.framesort.model.Page;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import java.util.List;

/** The whole page is sent as one message, so it stays together in the chat. */
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
        Component previous = button(messages, "pager.previous", page.hasPrevious(), pageCommand, page.number() - 1);
        Component next = button(messages, "pager.next", page.hasNext(), pageCommand, page.number() + 1);
        audience.sendMessage(messages.get("pager.layout",
                Placeholder.component("title", title),
                Placeholder.unparsed("total", String.valueOf(lines.size())),
                Placeholder.component("entries", entries),
                Placeholder.component("previous", previous),
                Placeholder.component("next", next),
                Placeholder.unparsed("page", "%02d".formatted(page.number())),
                Placeholder.unparsed("pages", "%02d".formatted(page.count()))));
    }

    private static Component button(Messages messages, String key, boolean enabled, String pageCommand, int target) {
        if (!enabled) {
            return messages.get(key + ".disabled");
        }
        return messages.get(key + ".enabled", Placeholder.unparsed("page", String.valueOf(target)))
                .clickEvent(ClickEvent.runCommand(pageCommand.formatted(target)));
    }
}
