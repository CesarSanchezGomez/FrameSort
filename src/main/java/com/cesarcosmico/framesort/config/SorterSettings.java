package com.cesarcosmico.framesort.config;

import com.cesarcosmico.framesort.item.ItemTemplate;
import com.cesarcosmico.framesort.item.Keys;
import com.cesarcosmico.framesort.item.MarkerCodec;
import com.cesarcosmico.framesort.model.ItemReference;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemFrame;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * {@code activatorTemplate} is how FrameSort builds a vanilla activator; it is {@code null} for a CraftEngine one,
 * which CraftEngine builds with its own look.
 */
public record SorterSettings(ItemReference activator, @Nullable ItemTemplate activatorTemplate,
                             boolean requireMarked, Set<EntityType> frameTypes, @Nullable String customName,
                             int tickRate, boolean disableWhenPowered, boolean hideFrame, boolean showActivity) {

    private static final Set<EntityType> ITEM_FRAMES = Set.of(EntityType.ITEM_FRAME, EntityType.GLOW_ITEM_FRAME);
    private static final ItemReference DEFAULT_ACTIVATOR = new ItemReference.Vanilla(Material.ENDER_EYE);

    public SorterSettings {
        frameTypes = Set.copyOf(frameTypes);
    }

    static SorterSettings parse(ConfigReader reader, Predicate<Material> isItem,
                                Function<String, @Nullable String> problem) {
        ConfigReader activator = reader.sectionOrEmpty("activator");
        ItemReference item = activatorItem(activator, isItem);
        ItemTemplate template = null;
        if (item instanceof ItemReference.Vanilla(Material material)) {
            template = ItemLookParser.template(activator, material, problem);
        } else {
            String look = ItemReferences.lookKeys(activator);
            if (look != null) {
                activator.warn("material", "a CraftEngine item keeps its own look; " + look + " ignored");
            }
        }
        String customName = reader.string("custom-name", "");
        return new SorterSettings(
                item,
                template,
                activator.bool("require-marked", false),
                frameTypes(reader),
                customName.isBlank() ? null : customName,
                reader.integer("tick-rate", 20, 1, 1200),
                reader.bool("disable-when-powered", false),
                reader.bool("hide-frame", true),
                reader.bool("show-activity", true));
    }

    /**
     * Whether {@code frame} holds the activator, which makes a sorter of the dispenser it hangs on. {@code customId}
     * gives a custom item's reference, or {@code null} for a vanilla item.
     */
    public boolean isActivatorFrame(ItemFrame frame, Function<ItemStack, @Nullable String> customId) {
        ItemStack item = frame.getItem();
        return frameTypes.contains(frame.getType()) && activator.matches(item.getType(), customId.apply(item))
                && (!requireMarked || MarkerCodec.read(item, Keys.ACTIVATOR) != null);
    }

    private static ItemReference activatorItem(ConfigReader activator, Predicate<Material> isItem) {
        ItemReference item = ItemReferences.parse(activator.string("material", "ENDER_EYE"),
                problem -> activator.warn("material", problem + ", using ENDER_EYE"));
        if (item instanceof ItemReference.Vanilla(Material material) && !isItem.test(material)) {
            activator.warn("material", material + " is not an item, using ENDER_EYE");
            return DEFAULT_ACTIVATOR;
        }
        return item == null ? DEFAULT_ACTIVATOR : item;
    }

    static Set<EntityType> frameTypes(ConfigReader reader) {
        Set<EntityType> types = reader.enumSet("frame-types", EntityType.class, ITEM_FRAMES);
        if (!ITEM_FRAMES.containsAll(types)) {
            reader.warn("frame-types", "only ITEM_FRAME and GLOW_ITEM_FRAME are item frames; other types are ignored");
            types = types.stream().filter(ITEM_FRAMES::contains).collect(Collectors.toSet());
        }
        return types.isEmpty() ? ITEM_FRAMES : types;
    }
}
