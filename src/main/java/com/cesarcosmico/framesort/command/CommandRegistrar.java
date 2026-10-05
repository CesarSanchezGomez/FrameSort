package com.cesarcosmico.framesort.command;

import com.cesarcosmico.framesort.config.CommandSpec;
import com.cesarcosmico.framesort.config.CommandsConfig;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class CommandRegistrar {

    private final JavaPlugin plugin;
    private final CommandsConfig config;
    private final List<PluginCommand> commands;

    public CommandRegistrar(JavaPlugin plugin, CommandsConfig config, List<PluginCommand> commands) {
        this.plugin = plugin;
        this.config = config;
        this.commands = commands;
    }

    public void register() {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            for (PluginCommand command : commands) {
                CommandSpec spec = config.effective(command.id(), command.defaults());
                if (!spec.enabled()) {
                    continue;
                }
                event.registrar().register(command.build(spec), command.description(), spec.aliases());
            }
        });
    }
}
