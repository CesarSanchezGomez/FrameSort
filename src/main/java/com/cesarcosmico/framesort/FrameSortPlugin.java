package com.cesarcosmico.framesort;

import com.cesarcosmico.framesort.command.CommandFeature;
import com.cesarcosmico.framesort.command.CommandTree;
import com.cesarcosmico.framesort.command.GiveCommand;
import com.cesarcosmico.framesort.command.HelpCommand;
import com.cesarcosmico.framesort.command.InspectCommand;
import com.cesarcosmico.framesort.command.ReloadCommand;
import com.cesarcosmico.framesort.command.TagCommand;
import com.cesarcosmico.framesort.command.TagsCommand;
import com.cesarcosmico.framesort.command.TraceCommand;
import com.cesarcosmico.framesort.command.WhereCommand;
import com.cesarcosmico.framesort.config.CommandsConfig;
import com.cesarcosmico.framesort.config.ConfigFiles;
import com.cesarcosmico.framesort.config.ConfigHolder;
import com.cesarcosmico.framesort.config.ConfigReader;
import com.cesarcosmico.framesort.config.ConfigValidator;
import com.cesarcosmico.framesort.config.FrameSortSettings;
import com.cesarcosmico.framesort.config.PadSettings;
import com.cesarcosmico.framesort.listener.InspectListener;
import com.cesarcosmico.framesort.listener.PadListener;
import com.cesarcosmico.framesort.listener.SorterListener;
import com.cesarcosmico.framesort.listener.TargetListener;
import com.cesarcosmico.framesort.service.DeliveryService;
import com.cesarcosmico.framesort.service.HighlightService;
import com.cesarcosmico.framesort.service.InspectService;
import com.cesarcosmico.framesort.service.PadService;
import com.cesarcosmico.framesort.service.SorterService;
import com.cesarcosmico.framesort.service.TagCatalog;
import com.cesarcosmico.framesort.service.TargetIndex;
import com.cesarcosmico.framesort.service.TargetResolver;
import com.cesarcosmico.framesort.service.TraceService;
import com.cesarcosmico.framesort.text.Messages;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;

public final class FrameSortPlugin extends JavaPlugin {

    private static final String COMMAND_DESCRIPTION = "Sort items into item frames";

    private ConfigHolder<FrameSortSettings> settings;
    private ConfigHolder<PadSettings> pads;
    private ConfigHolder<Messages> messages;
    private TagCatalog tags;
    private TargetIndex index;
    private TargetResolver resolver;
    private SorterService sorters;
    private PadService padService;
    private InspectService inspect;
    private BukkitTask ticker;

    @Override
    public void onEnable() {
        Consumer<String> warn = getLogger()::warning;
        CommandsConfig commands;
        try {
            settings = new ConfigHolder<>(loadSettings(warn));
            pads = new ConfigHolder<>(loadPads(warn));
            messages = new ConfigHolder<>(Messages.load(this, settings.get().language(), warn));
            commands = CommandsConfig.load(this, warn);
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "FrameSort could not start: " + e.getMessage(), e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        tags = new TagCatalog();
        index = new TargetIndex(settings);
        resolver = new TargetResolver(settings, index, tags);
        TraceService trace = new TraceService(this, settings, messages);
        DeliveryService delivery = new DeliveryService(settings, resolver, trace::report);
        sorters = new SorterService(this, settings, delivery);
        padService = new PadService(getServer(), pads, delivery);
        inspect = new InspectService(new HighlightService(this), settings, messages, index, resolver, sorters,
                padService, commands);

        register(new TargetListener(index), new SorterListener(sorters), new PadListener(padService, messages),
                new InspectListener(inspect, sorters, padService, trace));
        List<CommandFeature> features = List.of(
                new HelpCommand(messages, commands, List.of("tag", "tags", "where", "trace", "give", "reload")),
                new TagCommand(tags, messages, settings),
                new TagsCommand(tags, messages, settings, commands),
                new TraceCommand(trace, messages, settings),
                new InspectCommand(inspect, messages),
                new WhereCommand(inspect, messages, settings),
                new GiveCommand(sorters, padService, pads, messages),
                new ReloadCommand(messages, this::reload, getLogger(), getName()));
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            for (LiteralCommandNode<CommandSourceStack> root : CommandTree.build(features, commands)) {
                event.registrar().register(root, COMMAND_DESCRIPTION);
            }
        });

        // Chunks and frames that were loaded before the plugin enabled fire no events for us.
        for (World world : getServer().getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                padService.load(chunk);
            }
        }
        scanFrames();
        ticker = getServer().getScheduler().runTaskTimer(this, () -> {
            sorters.tick();
            padService.tick();
        }, 1L, 1L);
    }

    @Override
    public void onDisable() {
        if (ticker != null) {
            ticker.cancel();
        }
        if (inspect != null) {
            inspect.stopAll();
        }
    }

    /**
     * Loads everything first and swaps only when all of it parsed, so a broken file keeps the old state.
     *
     * @return how many settings were invalid and fell back to their defaults
     */
    private int reload() throws IOException, InvalidConfigurationException {
        List<String> warnings = new ArrayList<>();
        Consumer<String> warn = warning -> {
            warnings.add(warning);
            getLogger().warning(warning);
        };
        FrameSortSettings newSettings = loadSettings(warn);
        PadSettings newPads = loadPads(warn);
        Messages newMessages = Messages.load(this, newSettings.language(), warn);
        settings.set(newSettings);
        pads.set(newPads);
        messages.set(newMessages);
        tags.clear();
        resolver.clear();
        index.clear();
        scanFrames();
        return warnings.size();
    }

    private void scanFrames() {
        for (World world : getServer().getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                for (Entity entity : chunk.getEntities()) {
                    if (entity instanceof ItemFrame frame) {
                        index.add(frame);
                        sorters.consider(frame);
                    }
                }
            }
        }
    }

    private FrameSortSettings loadSettings(Consumer<String> warn) throws IOException, InvalidConfigurationException {
        YamlConfiguration yaml = ConfigFiles.load(this, "config.yml");
        ConfigValidator.check(this, yaml, "config.yml", warn);
        return FrameSortSettings.parse(new ConfigReader(yaml, "config.yml", warn));
    }

    private PadSettings loadPads(Consumer<String> warn) throws IOException, InvalidConfigurationException {
        YamlConfiguration yaml = ConfigFiles.load(this, "pads.yml");
        ConfigValidator.check(this, yaml, "pads.yml", Set.of("types"), warn);
        return PadSettings.parse(new ConfigReader(yaml, "pads.yml", warn), Material::isBlock);
    }

    private void register(Listener... listeners) {
        for (Listener listener : listeners) {
            getServer().getPluginManager().registerEvents(listener, this);
        }
    }
}
