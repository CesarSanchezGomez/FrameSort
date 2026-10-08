package com.cesarcosmico.framesort.text;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatPagerTest {

    private static final String LANG = """
            pager:
              layout:
                - '<title> (<total>)'
                - '<entries>'
                - '<previous> <page>/<pages> <next>'
              empty: 'Nothing'
              previous:
                enabled: '<<page>'
                disabled: '-'
              next:
                enabled: '><page>'
                disabled: '-'
            """;

    private static final class Recorder implements Audience {
        private final List<Component> received = new ArrayList<>();

        @Override
        public void sendMessage(Component message) {
            received.add(message);
        }
    }

    private static Messages messages() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(LANG);
        return Messages.of(yaml, null, null, key -> { });
    }

    private static List<Component> lines(int count) {
        return IntStream.rangeClosed(1, count).<Component>mapToObj(i -> Component.text("e" + i)).toList();
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static @Nullable ClickEvent firstClick(Component component) {
        if (component.clickEvent() != null) {
            return component.clickEvent();
        }
        for (Component child : component.children()) {
            ClickEvent click = firstClick(child);
            if (click != null) {
                return click;
            }
        }
        return null;
    }

    @Test
    void sendsOnePageAsOneMessage() throws Exception {
        Recorder audience = new Recorder();
        ChatPager.send(audience, messages(), Component.text("Tags"), lines(5), 2, 2, "/p tags %d");

        assertEquals(1, audience.received.size());
        assertEquals("Tags (5)\ne3\ne4\n<1 02/03 >3", plain(audience.received.getFirst()));
    }

    @Test
    void buttonsClickToTheConfiguredCommand() throws Exception {
        Recorder audience = new Recorder();
        ChatPager.send(audience, messages(), Component.text("Tags"), lines(5), 1, 2, "/p tags %d");

        ClickEvent click = firstClick(audience.received.getFirst());
        assertTrue(click != null && click.payload().toString().contains("/p tags 2"), String.valueOf(click));
    }

    @Test
    void withoutACommandTheButtonsAreNotClickable() throws Exception {
        Recorder audience = new Recorder();
        ChatPager.send(audience, messages(), Component.text("Tags"), lines(5), 1, 2, null);
        assertNull(firstClick(audience.received.getFirst()));
    }

    @Test
    void anEmptyListShowsTheEmptyText() throws Exception {
        Recorder audience = new Recorder();
        ChatPager.send(audience, messages(), Component.text("Tags"), List.of(), 1, 10, null);
        assertEquals("Tags (0)\nNothing\n- 01/01 -", plain(audience.received.getFirst()));
    }
}
