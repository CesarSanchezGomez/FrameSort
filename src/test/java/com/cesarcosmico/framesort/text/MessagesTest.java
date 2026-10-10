package com.cesarcosmico.framesort.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessagesTest {

    private static final String EN_US = """
            prefix: '[Jar]'
            greeting: 'Hello'
            only-in-english: 'English only'
            lines:
              - 'one'
              - 'two'
            """;
    private static final String ES_ES = """
            prefix: '[Jar ES]'
            greeting: 'Hola'
            """;

    private final List<String> missing = new ArrayList<>();

    private Messages messages(String live, String bundled, String fallback) throws Exception {
        return Messages.of(yaml(live), yaml(bundled), yaml(fallback), missing::add);
    }

    private static YamlConfiguration yaml(String text) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        return yaml;
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @Test
    void serverFileWins() throws Exception {
        assertEquals("Hi", plain(messages("greeting: 'Hi'\n", ES_ES, EN_US).get("greeting")));
    }

    @Test
    void keyMissingInServerFileComesFromTheJarCopyOfThatLanguage() throws Exception {
        assertEquals("Hola", plain(messages("other: 'x'\n", ES_ES, EN_US).get("greeting")));
    }

    @Test
    void keyMissingInTheTranslationComesFromEnglish() throws Exception {
        assertEquals("English only", plain(messages("other: 'x'\n", ES_ES, EN_US).get("only-in-english")));
    }

    @Test
    void prefixTagUsesTheFallbackChainToo() throws Exception {
        assertEquals("[Jar ES] go", plain(messages("other: 'x'\n", ES_ES, EN_US).get("<prefix> go")));
    }

    @Test
    void listsAreOneMessageJoinedByNewlines() throws Exception {
        assertEquals("one\ntwo", plain(messages("other: 'x'\n", ES_ES, EN_US).get("lines")));
    }

    @Test
    void resolversFillPlaceholders() throws Exception {
        Messages messages = messages("welcome: 'Hi <name>'\n", ES_ES, EN_US);
        assertEquals("Hi <b>Steve</b>", plain(messages.get("welcome", Placeholder.unparsed("name", "<b>Steve</b>"))));
    }

    @Test
    void keyMissingEverywhereIsShownAndReportedOnce() throws Exception {
        Messages messages = messages("other: 'x'\n", ES_ES, EN_US);
        assertEquals("no.such.key", plain(messages.get("no.such.key")));
        assertEquals("no.such.key", plain(messages.get("no.such.key")));
        assertEquals(1, missing.size());
    }
}
