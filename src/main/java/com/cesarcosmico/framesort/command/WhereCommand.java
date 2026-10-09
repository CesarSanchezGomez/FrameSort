package com.cesarcosmico.framesort.command;

import com.cesarcosmico.framesort.config.FrameSortSettings;
import com.cesarcosmico.framesort.service.InspectService;
import com.cesarcosmico.framesort.text.Messages;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.function.Supplier;

public final class WhereCommand implements CommandFeature {

    private final InspectService inspect;
    private final Supplier<Messages> messages;
    private final Supplier<FrameSortSettings> settings;

    public WhereCommand(InspectService inspect, Supplier<Messages> messages, Supplier<FrameSortSettings> settings) {
        this.inspect = inspect;
        this.messages = messages;
        this.settings = settings;
    }

    @Override
    public String id() {
        return "where";
    }

    @Override
    public void attach(LiteralArgumentBuilder<CommandSourceStack> node, String path) {
        node.then(Commands.argument("item", ArgumentTypes.itemStack()).executes(this::where));
    }

    private int where(CommandContext<CommandSourceStack> context) {
        if (!(context.getSource().getSender() instanceof Player player)) {
            context.getSource().getSender().sendMessage(messages.get().get("command.players-only"));
            return Command.SINGLE_SUCCESS;
        }
        if (!inspect.inspectNearest(player, context.getArgument("item", ItemStack.class))) {
            player.sendMessage(messages.get().get("where.no-source",
                    Placeholder.unparsed("radius", String.valueOf(settings.get().delivery().maxDistance()))));
        }
        return Command.SINGLE_SUCCESS;
    }
}
