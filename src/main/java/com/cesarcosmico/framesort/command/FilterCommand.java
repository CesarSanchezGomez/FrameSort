package com.cesarcosmico.framesort.command;

import com.cesarcosmico.framesort.config.FrameSortSettings;
import com.cesarcosmico.framesort.config.TargetSettings;
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

import java.util.function.Supplier;

// One argument and no sibling options, so the client always suggests the tags; running it again undoes it.
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
        node.then(Commands.argument("tag", tag).executes(this::toggle));
    }

    private int toggle(CommandContext<CommandSourceStack> context) {
        if (!(context.getSource().getSender() instanceof Player player)) {
            context.getSource().getSender().sendMessage(messages.get().get("command.players-only"));
            return Command.SINGLE_SUCCESS;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.isEmpty()) {
            player.sendMessage(messages.get().get("filter.empty-hand"));
            return Command.SINGLE_SUCCESS;
        }
        TagCatalog.TagView view = context.getArgument("tag", TagCatalog.TagView.class);
        String name = TagCommand.shortName(view.key());
        if (view.key().equals(TagFilterCodec.read(held))) {
            TagFilterCodec.clear(held);
            player.sendMessage(messages.get().get("filter.cleared", Placeholder.unparsed("tag", name)));
        } else {
            TargetSettings targets = settings.get().targets();
            TagFilterCodec.apply(held, view.key(),
                    MINI_MESSAGE.deserialize(targets.filterName(), Placeholder.unparsed("tag", name)),
                    targets.filterGlint());
            player.sendMessage(messages.get().get("filter.set", Placeholder.unparsed("tag", name)));
        }
        player.getInventory().setItemInMainHand(held);
        return Command.SINGLE_SUCCESS;
    }
}
