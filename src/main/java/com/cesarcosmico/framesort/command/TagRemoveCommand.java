package com.cesarcosmico.framesort.command;

import com.cesarcosmico.framesort.item.ItemTagCodec;
import com.cesarcosmico.framesort.text.Messages;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.function.Supplier;

public final class TagRemoveCommand implements CommandFeature {

    private final Supplier<Messages> messages;

    public TagRemoveCommand(Supplier<Messages> messages) {
        this.messages = messages;
    }

    @Override
    public String id() {
        return "tag-remove";
    }

    @Override
    public void attach(LiteralArgumentBuilder<CommandSourceStack> node, String path) {
        node.executes(this::remove);
    }

    private int remove(CommandContext<CommandSourceStack> context) {
        Messages text = messages.get();
        Player player = TagApplyCommand.holder(context, text);
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (ItemTagCodec.read(held) == null) {
            player.sendMessage(text.get("tag-item.not-tagged"));
            return Command.SINGLE_SUCCESS;
        }
        ItemTagCodec.clear(held);
        player.getInventory().setItemInMainHand(held);
        player.sendMessage(text.get("tag-item.removed"));
        return Command.SINGLE_SUCCESS;
    }
}
