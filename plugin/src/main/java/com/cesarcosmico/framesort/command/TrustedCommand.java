package com.cesarcosmico.framesort.command;

import com.cesarcosmico.framesort.config.FrameSortSettings;
import com.cesarcosmico.framesort.model.TrustList;
import com.cesarcosmico.framesort.service.TrustService;
import com.cesarcosmico.framesort.text.ChatPager;
import com.cesarcosmico.framesort.text.Messages;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;

// Its own path, not /fs trust with no player: a page number there would read as a player's name.
public final class TrustedCommand implements CommandFeature {

    private final TrustService trust;
    private final Supplier<Messages> messages;
    private final Supplier<FrameSortSettings> settings;

    public TrustedCommand(TrustService trust, Supplier<Messages> messages, Supplier<FrameSortSettings> settings) {
        this.trust = trust;
        this.messages = messages;
        this.settings = settings;
    }

    @Override
    public String id() {
        return "trusted";
    }

    @Override
    public void attach(LiteralArgumentBuilder<CommandSourceStack> node, String path) {
        PageArgument.optional(node, (context, page) -> send(context, path, page));
    }

    private int send(CommandContext<CommandSourceStack> context, String path, int page) {
        Player player = Senders.player(context, messages.get());
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        Messages text = messages.get();
        TrustList list = trust.get();
        UUID self = player.getUniqueId();
        List<Component> lines = Stream.concat(
                list.trustedBy(self).entrySet().stream()
                        .sorted(Map.Entry.comparingByValue())
                        .map(entry -> text.get("trust.list.trusted",
                                Placeholder.unparsed("player", trust.name(entry.getKey())),
                                Placeholder.unparsed("date", date(entry.getValue())))),
                list.trusting(self).stream()
                        .map(trust::name)
                        .sorted(Comparator.comparing(name -> name.toLowerCase(Locale.ROOT)))
                        .map(name -> text.get("trust.list.trusting", Placeholder.unparsed("player", name))))
                .toList();
        ChatPager.send(player, text, text.get("trust.list.title"), lines, page, settings.get().inspect().pageSize(),
                path + " %d");
        return Command.SINGLE_SUCCESS;
    }

    private static String date(long millis) {
        return DateTimeFormatter.ISO_LOCAL_DATE.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()));
    }
}
