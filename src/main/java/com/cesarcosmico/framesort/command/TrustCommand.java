package com.cesarcosmico.framesort.command;

import com.cesarcosmico.framesort.service.TrustService;
import com.cesarcosmico.framesort.text.Messages;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class TrustCommand implements CommandFeature {

    private final TrustService trust;
    private final Supplier<Messages> messages;

    public TrustCommand(TrustService trust, Supplier<Messages> messages) {
        this.trust = trust;
        this.messages = messages;
    }

    @Override
    public String id() {
        return "trust";
    }

    @Override
    public void attach(LiteralArgumentBuilder<CommandSourceStack> node, String path) {
        node.then(Commands.argument("player", StringArgumentType.word())
                .suggests(TrustCommand::onlinePlayers)
                .executes(this::trust));
    }

    private int trust(CommandContext<CommandSourceStack> context) {
        if (!(context.getSource().getSender() instanceof Player player)) {
            context.getSource().getSender().sendMessage(messages.get().get("command.players-only"));
            return Command.SINGLE_SUCCESS;
        }
        trust.trust(player, StringArgumentType.getString(context, "player"), (result, name) -> {
            String key = switch (result) {
                case GRANTED -> "trust.granted";
                case ALREADY -> "trust.already";
                case SELF -> "trust.self";
                case UNKNOWN_PLAYER -> "trust.unknown-player";
                case DISABLED -> "trust.disabled";
                case FAILED -> "trust.failed";
            };
            player.sendMessage(messages.get().get(key, Placeholder.unparsed("player", name)));
        });
        return Command.SINGLE_SUCCESS;
    }

    private static CompletableFuture<Suggestions> onlinePlayers(CommandContext<CommandSourceStack> context,
                                                                SuggestionsBuilder builder) {
        String typed = builder.getRemainingLowerCase();
        for (Player online : context.getSource().getSender().getServer().getOnlinePlayers()) {
            if (online != context.getSource().getSender()
                    && online.getName().toLowerCase(Locale.ROOT).startsWith(typed)) {
                builder.suggest(online.getName());
            }
        }
        return builder.buildFuture();
    }
}
