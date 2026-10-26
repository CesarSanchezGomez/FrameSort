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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class UntrustCommand implements CommandFeature {

    private final TrustService trust;
    private final Supplier<Messages> messages;

    public UntrustCommand(TrustService trust, Supplier<Messages> messages) {
        this.trust = trust;
        this.messages = messages;
    }

    @Override
    public String id() {
        return "untrust";
    }

    @Override
    public void attach(LiteralArgumentBuilder<CommandSourceStack> node, String path) {
        node.then(Commands.argument("player", StringArgumentType.word())
                .suggests(this::trustedPlayers)
                .executes(this::untrust));
    }

    private int untrust(CommandContext<CommandSourceStack> context) {
        Player player = Senders.player(context, messages.get());
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        trust.untrust(player, StringArgumentType.getString(context, "player"), (result, name) -> {
            String key = switch (result) {
                case REVOKED -> "trust.revoked";
                case NOT_TRUSTED -> "trust.not-trusted";
                case FAILED -> "trust.failed";
            };
            player.sendMessage(messages.get().get(key, Placeholder.unparsed("player", name)));
        });
        return Command.SINGLE_SUCCESS;
    }

    private CompletableFuture<Suggestions> trustedPlayers(CommandContext<CommandSourceStack> context,
                                                          SuggestionsBuilder builder) {
        if (context.getSource().getSender() instanceof Player player) {
            String typed = builder.getRemainingLowerCase();
            for (UUID trusted : trust.get().trustedBy(player.getUniqueId()).keySet()) {
                String name = trust.name(trusted);
                if (name.toLowerCase(Locale.ROOT).startsWith(typed)) {
                    builder.suggest(name);
                }
            }
        }
        return builder.buildFuture();
    }
}
