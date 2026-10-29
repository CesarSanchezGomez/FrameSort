package com.cesarcosmico.framesort.command;

import com.cesarcosmico.framesort.config.CommandsConfig;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Builds the Brigadier roots from the features and their paths in {@code commands.yml}. Paths that share words
 * share literals, so {@code /plugin tag} and {@code /plugin trace} hang from one {@code plugin}.
 */
public final class CommandTree {

    private static final class Node {
        private final String name;
        private final Map<String, Node> children = new LinkedHashMap<>();
        private @Nullable CommandFeature feature;
        private Predicate<CommandSourceStack> usable = source -> false;

        private Node(String name) {
            this.name = name;
        }
    }

    private CommandTree() {
    }

    /**
     * Registers every enabled feature, also one whose condition is false now ({@link CommandsConfig#onlyWhen}): a
     * reload can make it true, and until then nobody sees or runs it.
     */
    public static List<LiteralCommandNode<CommandSourceStack>> build(List<CommandFeature> features,
                                                                     CommandsConfig config) {
        Map<String, Node> roots = new LinkedHashMap<>();
        for (CommandFeature feature : features) {
            CommandsConfig.Feature spec = config.feature(feature.id());
            if (spec == null || !spec.enabled()) {
                continue;
            }
            for (List<String> path : spec.paths()) {
                Node node = roots.computeIfAbsent(path.getFirst(), Node::new);
                for (String word : path.subList(1, path.size())) {
                    node = node.children.computeIfAbsent(word, Node::new);
                }
                node.feature = feature;
                String id = feature.id();
                String permission = spec.permission();
                node.usable = source -> config.available(id) && allowed(source, permission);
            }
        }
        List<LiteralCommandNode<CommandSourceStack>> built = new ArrayList<>();
        for (Node root : roots.values()) {
            built.add(literal(root, "/" + root.name).build());
        }
        return built;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> literal(Node node, String path) {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal(node.name);
        List<Predicate<CommandSourceStack>> below = new ArrayList<>();
        collectUsable(node, below);
        builder.requires(source -> below.stream().anyMatch(usable -> usable.test(source)));
        if (node.feature != null) {
            attach(builder, node.feature, node.usable, path);
        }
        for (Node child : node.children.values()) {
            builder.then(literal(child, path + " " + child.name));
        }
        return builder;
    }

    // A literal is visible to anyone allowed below it, so the feature's own command and arguments check it again.
    private static void attach(LiteralArgumentBuilder<CommandSourceStack> builder, CommandFeature feature,
                               Predicate<CommandSourceStack> allowed, String path) {
        LiteralArgumentBuilder<CommandSourceStack> own = Commands.literal(builder.getLiteral());
        feature.attach(own, path);
        Predicate<CommandSourceStack> gate = allowed.and(own.getRequirement());
        Command<CommandSourceStack> command = own.getCommand();
        if (command != null) {
            builder.executes(context -> {
                if (!gate.test(context.getSource())) {
                    throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().create();
                }
                return command.run(context);
            });
        }
        for (CommandNode<CommandSourceStack> argument : own.getArguments()) {
            builder.then(restrict(argument, gate));
        }
    }

    private static ArgumentBuilder<CommandSourceStack, ?> restrict(CommandNode<CommandSourceStack> node,
                                                                   Predicate<CommandSourceStack> allowed) {
        ArgumentBuilder<CommandSourceStack, ?> builder = node.createBuilder();
        builder.requires(allowed.and(node.getRequirement()));
        for (CommandNode<CommandSourceStack> child : node.getChildren()) {
            builder.then(child);
        }
        return builder;
    }

    private static void collectUsable(Node node, List<Predicate<CommandSourceStack>> into) {
        if (node.feature != null) {
            into.add(node.usable);
        }
        for (Node child : node.children.values()) {
            collectUsable(child, into);
        }
    }

    private static boolean allowed(CommandSourceStack source, String permission) {
        return permission.isBlank() || source.getSender().hasPermission(permission);
    }
}
