package com.cesarcosmico.framesort.item;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ItemLookTest {

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @Test
    void textIsNotItalicUnlessAsked() {
        ItemLook look = new ItemLook("Name", List.of("plain", "<i>asked</i>"), false);
        assertEquals(TextDecoration.State.FALSE, look.renderName().decoration(TextDecoration.ITALIC));
        List<Component> lore = look.renderLore();
        assertEquals(TextDecoration.State.FALSE, lore.get(0).decoration(TextDecoration.ITALIC));
        assertEquals(TextDecoration.State.TRUE, lore.get(1).decoration(TextDecoration.ITALIC));
    }

    @Test
    void placeholdersResolve() {
        ItemLook look = new ItemLook("#<tag>", List.of("<tag> items"), false);
        assertEquals("#logs", plain(look.renderName(Placeholder.unparsed("tag", "logs"))));
        assertEquals("logs items", plain(look.renderLore(Placeholder.unparsed("tag", "logs")).getFirst()));
    }

    @Test
    void noNameRendersNothing() {
        assertNull(new ItemLook(null, List.of(), false).renderName());
    }

    @Test
    void inputJoinsTheComponentsInOrder() {
        assertEquals("minecraft:ender_eye", ItemTemplate.input(Material.ENDER_EYE, Map.of()));
        Map<String, String> components = new LinkedHashMap<>();
        components.put("rarity", "\"epic\"");
        components.put("item_model", "\"a:b\"");
        ItemTemplate template = new ItemTemplate(Material.PAPER, components, new ItemLook(null, List.of(), false));
        assertEquals("minecraft:paper[rarity=\"epic\",item_model=\"a:b\"]",
                ItemTemplate.input(template.material(), template.components()));
    }
}
