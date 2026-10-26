package com.cesarcosmico.framesort.command;

import com.cesarcosmico.framesort.config.PadSettings;
import com.cesarcosmico.framesort.config.PadType;
import com.cesarcosmico.framesort.service.PadService;
import com.cesarcosmico.framesort.service.SorterService;
import com.cesarcosmico.framesort.text.Messages;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public final class GiveCommand implements CommandFeature {

    private interface ItemSource {
        // Null when CraftEngine can't build the item now.
        @Nullable ItemStack create(CommandContext<CommandSourceStack> context, int amount)
                throws CommandSyntaxException;
    }

    private final SorterService sorters;
    private final PadService pads;
    private final PadTypeArgument padType;
    private final Supplier<Messages> messages;

    public GiveCommand(SorterService sorters, PadService pads, Supplier<PadSettings> padSettings,
                       Supplier<Messages> messages) {
        this.sorters = sorters;
        this.pads = pads;
        this.padType = new PadTypeArgument(padSettings, messages);
        this.messages = messages;
    }

    @Override
    public String id() {
        return "give";
    }

    @Override
    public void attach(LiteralArgumentBuilder<CommandSourceStack> node, String path) {
        ItemSource sorter = (context, amount) -> sorters.createActivator(amount);
        ItemSource pad = (context, amount) -> pads.createItem(context.getArgument("type", PadType.class), amount);
        node.then(targets(Commands.literal("sorter"), sorter, path))
                .then(Commands.literal("pad")
                        .then(targets(Commands.argument("type", padType), pad, path)));
    }

    private <B extends ArgumentBuilder<CommandSourceStack, B>> B targets(B node, ItemSource item, String path) {
        RequiredArgumentBuilder<CommandSourceStack, Integer> amount =
                Commands.argument("amount", IntegerArgumentType.integer(1, 64));
        Function<CommandContext<CommandSourceStack>, Integer> amountOf =
                context -> IntegerArgumentType.getInteger(context, "amount");
        return node
                .executes(context -> giveSelf(context, item, path))
                .then(Commands.argument("player", ArgumentTypes.player())
                        .executes(context -> give(context, named(context), item, 1))
                        .then(amount.executes(
                                context -> give(context, named(context), item, amountOf.apply(context)))));
    }

    private int giveSelf(CommandContext<CommandSourceStack> context, ItemSource item, String path)
            throws CommandSyntaxException {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.get().get("give.players-only", Placeholder.unparsed("usage", path)));
            return Command.SINGLE_SUCCESS;
        }
        return give(context, player, item, 1);
    }

    private int give(CommandContext<CommandSourceStack> context, Player target, ItemSource item, int amount)
            throws CommandSyntaxException {
        CommandSender sender = context.getSource().getSender();
        ItemStack stack = item.create(context, amount);
        if (stack == null) {
            sender.sendMessage(messages.get().get("give.unavailable"));
            return Command.SINGLE_SUCCESS;
        }
        // Whatever does not fit is dropped at the player's feet instead of being lost.
        for (ItemStack left : target.getInventory().addItem(stack).values()) {
            target.getWorld().dropItem(target.getLocation(), left);
        }
        sender.sendMessage(messages.get().get("give.done",
                Placeholder.unparsed("amount", String.valueOf(amount)),
                Placeholder.component("item", stack.effectiveName()),
                Placeholder.unparsed("player", target.getName())));
        return Command.SINGLE_SUCCESS;
    }

    private static Player named(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        List<Player> players = context.getArgument("player", PlayerSelectorArgumentResolver.class)
                .resolve(context.getSource());
        return players.getFirst();
    }
}
