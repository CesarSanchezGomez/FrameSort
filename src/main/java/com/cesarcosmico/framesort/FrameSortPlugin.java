package com.cesarcosmico.framesort;

import com.cesarcosmico.framesort.command.CommandRegistrar;
import com.cesarcosmico.framesort.command.FrameSortCommand;
import com.cesarcosmico.framesort.command.GiveCommand;
import com.cesarcosmico.framesort.command.InspectCommand;
import com.cesarcosmico.framesort.command.TagCommand;
import com.cesarcosmico.framesort.command.TagsCommand;
import com.cesarcosmico.framesort.command.TraceCommand;
import com.cesarcosmico.framesort.config.CommandsConfig;
import com.cesarcosmico.framesort.config.ConfigHolder;
import com.cesarcosmico.framesort.config.ConfigValidator;
import com.cesarcosmico.framesort.config.FrameSortSettings;
import com.cesarcosmico.framesort.config.PadSettings;
import com.cesarcosmico.framesort.listener.PadListener;
import com.cesarcosmico.framesort.listener.SorterListener;
import com.cesarcosmico.framesort.listener.TargetListener;
import com.cesarcosmico.framesort.listener.ToolListener;
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
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

public final class FrameSortPlugin extends JavaPlugin {

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
        try {
            saveDefaultConfig();
            settings = new ConfigHolder<>(loadSettings());
            pads = new ConfigHolder<>(loadPads());
            messages = new ConfigHolder<>(Messages.load(this, settings.get().language()));
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "FrameSort could not start: " + e.getMessage(), e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        CommandsConfig commands = CommandsConfig.load(this);
        String commandName = commands.effective("framesort", FrameSortCommand.DEFAULTS).name();

        tags = new TagCatalog();
        index = new TargetIndex(settings);
        resolver = new TargetResolver(settings, index, tags);
        TraceService trace = new TraceService(this, settings, messages);
        DeliveryService delivery = new DeliveryService(settings, resolver, trace::report);
        sorters = new SorterService(this, settings, delivery);
        padService = new PadService(getServer(), pads, delivery);
        inspect = new InspectService(new HighlightService(this), settings, messages, index, resolver, sorters,
                padService, commandName);

        register(new TargetListener(index), new SorterListener(sorters), new PadListener(padService, messages),
                new ToolListener(settings, inspect, sorters, padService, trace));
        new CommandRegistrar(this, commands, List.of(new FrameSortCommand(messages, List.of(
                new TagCommand(tags, messages, settings),
                new TagsCommand(tags, messages, settings),
                new TraceCommand(trace, messages, settings),
                new InspectCommand(inspect, messages),
                new GiveCommand(sorters, padService, pads, messages)),
                getPluginMeta().getVersion(), this::reload, getLogger()))).register();

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

    /** Loads everything first and swaps only when all of it parsed, so a broken file keeps the old state. */
    private void reload() throws Exception {
        reloadConfig();
        FrameSortSettings newSettings = loadSettings();
        PadSettings newPads = loadPads();
        Messages newMessages = Messages.load(this, newSettings.language());
        settings.set(newSettings);
        pads.set(newPads);
        messages.set(newMessages);
        tags.clear();
        resolver.clear();
        index.clear();
        scanFrames();
    }

    /** Indexes target frames and (re)checks sorters for every loaded frame. */
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

    private FrameSortSettings loadSettings() {
        ConfigValidator.check(this, getConfig(), "config.yml");
        return FrameSortSettings.parse(getConfig(), getLogger()::warning);
    }

    private PadSettings loadPads() {
        File file = new File(getDataFolder(), "pads.yml");
        if (!file.exists()) {
            saveResource("pads.yml", false);
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigValidator.check(this, yaml, "pads.yml", Set.of("types"));
        return PadSettings.parse(yaml, getLogger()::warning, Material::isBlock);
    }

    private void register(Listener... listeners) {
        for (Listener listener : listeners) {
            getServer().getPluginManager().registerEvents(listener, this);
        }
    }
}
