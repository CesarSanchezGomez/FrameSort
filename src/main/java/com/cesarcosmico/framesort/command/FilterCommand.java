package com.cesarcosmico.framesort.command;

import com.cesarcosmico.framesort.config.FrameSortSettings;
import com.cesarcosmico.framesort.item.TagFilterCodec;
import com.cesarcosmico.framesort.service.TagCatalog;
import com.cesarcosmico.framesort.text.Messages;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

public final class FilterCommand implements CommandFeature {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final TagArgument tag;
    private final Supplier<Messages> messages;
    private final Supplier<FrameSortSettings> settings;

    public FilterCommand(TagCatalog tags, Supplier<Messages> messages, Supplier<FrameSortSettings> settings) {
        this.tag = new TagArgument(tags, messages);
        this.messages = messages;
        this.settings = settings;
    }

    @Override
    public String id() {
        return "filter";
    }

    @Override
    public void attach(LiteralArgumentBuilder<CommandSourceStack> node, String path) {
        node.then(Commands.literal("clear").executes(this::clear))
                .then(Commands.argument("tag", tag).executes(this::apply));
    }

    private int apply(CommandContext<CommandSourceStack> context) {
        Player player = holder(context);
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        TagCatalog.TagView view = context.getArgument("tag", TagCatalog.TagView.class);
        String name = TagCommand.shortName(view.key());
        ItemStack held = player.getInventory().getItemInMainHand();
        TagFilterCodec.apply(held, view.key(), MINI_MESSAGE.deserialize(settings.get().targets().filterName(),
                Placeholder.unparsed("tag", name)));
        player.getInventory().setItemInMainHand(held);
        player.sendMessage(messages.get().get("filter.set", Placeholder.unparsed("tag", name)));
        return Command.SINGLE_SUCCESS;
    }

    private int clear(CommandContext<CommandSourceStack> context) {
        Player player = holder(context);
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (TagFilterCodec.read(held) == null) {
            player.sendMessage(messages.get().get("filter.not-a-filter"));
            return Command.SINGLE_SUCCESS;
        }
        TagFilterCodec.clear(held);
        player.getInventory().setItemInMainHand(held);
        player.sendMessage(messages.get().get("filter.cleared"));
        return Command.SINGLE_SUCCESS;
    }

    // The player holding an item to work on, after telling the sender why there is none.
    private @Nullable Player holder(CommandContext<CommandSourceStack> context) {
        if (!(context.getSource().getSender() instanceof Player player)) {
            context.getSource().getSender().sendMessage(messages.get().get("command.players-only"));
            return null;
        }
        if (player.getInventory().getItemInMainHand().isEmpty()) {
            player.sendMessage(messages.get().get("filter.empty-hand"));
            return null;
        }
        return player;
    }
}
