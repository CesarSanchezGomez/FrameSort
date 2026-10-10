package com.cesarcosmico.framesort.command;

import com.cesarcosmico.framesort.config.FrameSortSettings;
import com.cesarcosmico.framesort.config.TargetSettings;
import com.cesarcosmico.framesort.item.ItemTagCodec;
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

public final class TagApplyCommand implements CommandFeature {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final TagArgument tag;
    private final Supplier<Messages> messages;
    private final Supplier<FrameSortSettings> settings;

    public TagApplyCommand(TagCatalog tags, Supplier<Messages> messages, Supplier<FrameSortSettings> settings) {
        this.tag = new TagArgument(tags, messages);
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
        Player player = holder(context, messages.get());
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        TagCatalog.TagView view = context.getArgument("tag", TagCatalog.TagView.class);
        String name = TagArgument.shortName(view.key());
        TargetSettings targets = settings.get().targets();
        ItemStack held = player.getInventory().getItemInMainHand();
        ItemTagCodec.apply(held, view.key(),
                MINI_MESSAGE.deserialize(targets.taggedName(), Placeholder.unparsed("tag", name)),
                targets.taggedGlint());
        player.getInventory().setItemInMainHand(held);
        player.sendMessage(messages.get().get("tag-item.applied", Placeholder.unparsed("tag", name)));
        return Command.SINGLE_SUCCESS;
    }

    // The player holding an item to work on, after telling the sender why there is none. tag-remove needs it too.
    static @Nullable Player holder(CommandContext<CommandSourceStack> context, Messages messages) {
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
