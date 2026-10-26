package com.cesarcosmico.framesort.command;

import com.cesarcosmico.framesort.config.CommandsConfig;
import com.cesarcosmico.framesort.config.FrameSortSettings;
import com.cesarcosmico.framesort.item.ItemTagCodec;
import com.cesarcosmico.framesort.service.TagCatalog;
import com.cesarcosmico.framesort.text.Messages;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

public final class TagApplyCommand implements CommandFeature {

    private final TagArgument tag;
    private final Supplier<Messages> messages;
    private final Supplier<FrameSortSettings> settings;

    public TagApplyCommand(TagCatalog tags, Supplier<Messages> messages, Supplier<FrameSortSettings> settings,
                           CommandsConfig commands) {
        this.tag = new TagArgument(tags, messages, commands);
        this.messages = messages;
        this.settings = settings;
    }

    @Override
    public String id() {
        return "tag-apply";
    }

    @Override
    public void attach(LiteralArgumentBuilder<CommandSourceStack> node, String path) {
        node.then(Commands.argument("tag", tag).executes(this::apply));
    }

    private int apply(CommandContext<CommandSourceStack> context) {
        Player player = playerHoldingItem(context, messages.get());
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        TagCatalog.TagView view = context.getArgument("tag", TagCatalog.TagView.class);
        String name = TagArgument.shortName(view.key());
        ItemStack held = player.getInventory().getItemInMainHand();
        ItemTagCodec.apply(held, view.key(), settings.get().targets().taggedItemLook(),
                Placeholder.unparsed("tag", name));
        player.getInventory().setItemInMainHand(held);
        player.sendMessage(messages.get().get("tag-item.applied", Placeholder.unparsed("tag", name)));
        return Command.SINGLE_SUCCESS;
    }

    // Tells the sender why when there is none.
    static @Nullable Player playerHoldingItem(CommandContext<CommandSourceStack> context, Messages messages) {
        if (!(context.getSource().getSender() instanceof Player player)) {
            context.getSource().getSender().sendMessage(messages.get("command.players-only"));
            return null;
        }
        if (player.getInventory().getItemInMainHand().isEmpty()) {
            player.sendMessage(messages.get("tag-item.empty-hand"));
            return null;
        }
        return player;
    }
}
