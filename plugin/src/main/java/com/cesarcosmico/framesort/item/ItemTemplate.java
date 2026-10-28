package com.cesarcosmico.framesort.item;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

/**
 * An item the plugin creates: a material, vanilla components (name to value in /give syntax) and a look. The
 * components were checked when the config loaded, so building never fails. Main thread only.
 */
public record ItemTemplate(Material material, Map<String, String> components, ItemLook look) {

    public ItemTemplate {
        components = Collections.unmodifiableMap(new LinkedHashMap<>(components));
    }

    /** The item in /give syntax, such as {@code minecraft:paper[item_model="minecraft:stick"]}. */
    public static String input(Material material, Map<String, String> components) {
        String id = material.getKey().asString();
        if (components.isEmpty()) {
            return id;
        }
        StringJoiner joined = new StringJoiner(",", id + "[", "]");
        components.forEach((name, value) -> joined.add(name + "=" + value));
        return joined.toString();
    }

    /** Why Minecraft rejects {@code input}, or {@code null} if it accepts it. Needs a running server. */
    public static @Nullable String problem(String input) {
        try {
            Bukkit.getItemFactory().createItemStack(input);
            return null;
        } catch (IllegalArgumentException e) {
            // The cause carries the reason without the "Could not parse ItemStack: <input>" wrapper.
            return e.getCause() instanceof CommandSyntaxException syntax
                    ? syntax.getRawMessage().getString() : e.getMessage();
        }
    }

    /** The name goes in as {@code item_name}, so an anvil rename still shows over it. */
    public ItemStack create(int amount, TagResolver... resolvers) {
        ItemStack item = Bukkit.getItemFactory().createItemStack(input(material, components));
        item.setAmount(amount);
        Component name = look.renderName(resolvers);
        if (name != null) {
            item.setData(DataComponentTypes.ITEM_NAME, name);
        }
        if (!look.lore().isEmpty()) {
            item.setData(DataComponentTypes.LORE, ItemLore.lore(look.renderLore(resolvers)));
        }
        if (look.glint()) {
            item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        return item;
    }
}
