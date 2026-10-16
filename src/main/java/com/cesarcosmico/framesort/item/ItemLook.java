package com.cesarcosmico.framesort.item;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The look the owner gives an item in YAML: {@code name} and {@code lore} in MiniMessage, and {@code glint}. A
 * {@code null} name, an empty lore or {@code glint = false} means none of it.
 */
public record ItemLook(@Nullable String name, List<String> lore, boolean glint) {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    public ItemLook {
        lore = List.copyOf(lore);
    }

    public @Nullable Component renderName(TagResolver... resolvers) {
        return name == null ? null : render(name, resolvers);
    }

    public List<Component> renderLore(TagResolver... resolvers) {
        return lore.stream().map(line -> render(line, resolvers)).toList();
    }

    // A custom name and the lore are italic unless told otherwise; the owner asks for italics with <i>.
    private static Component render(String text, TagResolver... resolvers) {
        return MINI_MESSAGE.deserialize(text, resolvers)
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }
}
