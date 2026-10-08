package com.cesarcosmico.framesort.command;

import com.cesarcosmico.framesort.config.CommandsConfig;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

// Structure only. Who may see and run each node is checked by hand on a server (§13.2).
class CommandTreeTest {

    private record Feature(String id) implements CommandFeature {
        @Override
        public void attach(LiteralArgumentBuilder<CommandSourceStack> node, String path) {
            node.executes(context -> Command.SINGLE_SUCCESS);
        }
    }

    private static CommandsConfig config(String text) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        return CommandsConfig.parse(yaml, yaml, warning -> { });
    }

    @Test
    void pathsSharingWordsShareLiterals() throws Exception {
        CommandsConfig config = config("""
                help: {permission: 'h', usage: ['/plugin', '/p']}
                tag: {permission: 't', usage: ['/plugin tag', '/p tag']}
                trace: {permission: 'r', usage: ['/plugin trace', '/ptrace']}
                """);
        List<LiteralCommandNode<CommandSourceStack>> roots = CommandTree.build(
                List.of(new Feature("help"), new Feature("tag"), new Feature("trace")), config);

        assertEquals(List.of("plugin", "p", "ptrace"), roots.stream().map(CommandNode::getName).toList());
        LiteralCommandNode<CommandSourceStack> plugin = roots.getFirst();
        assertNotNull(plugin.getCommand());
        assertNotNull(plugin.getChild("tag"));
        assertNotNull(plugin.getChild("trace"));
        assertNull(roots.get(1).getChild("trace"));
        assertNotNull(roots.get(2).getCommand());
    }

    @Test
    void disabledFeaturesAreLeftOut() throws Exception {
        CommandsConfig config = config("""
                help: {permission: 'h', usage: ['/p']}
                trace: {enabled: false, permission: 'r', usage: ['/p trace']}
                """);
        List<LiteralCommandNode<CommandSourceStack>> roots = CommandTree.build(
                List.of(new Feature("help"), new Feature("trace")), config);

        assertEquals(1, roots.size());
        assertNull(roots.getFirst().getChild("trace"));
    }
}
