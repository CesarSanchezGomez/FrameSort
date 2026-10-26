package com.cesarcosmico.framesort;

import com.cesarcosmico.framesort.command.AutomarkCommand;
import com.cesarcosmico.framesort.command.CommandFeature;
import com.cesarcosmico.framesort.command.CommandTree;
import com.cesarcosmico.framesort.command.GiveCommand;
import com.cesarcosmico.framesort.command.HelpCommand;
import com.cesarcosmico.framesort.command.ReloadCommand;
import com.cesarcosmico.framesort.command.TagApplyCommand;
import com.cesarcosmico.framesort.command.TagListCommand;
import com.cesarcosmico.framesort.command.TagListing;
import com.cesarcosmico.framesort.command.TagRemoveCommand;
import com.cesarcosmico.framesort.command.TagSearchCommand;
import com.cesarcosmico.framesort.command.TagShowCommand;
import com.cesarcosmico.framesort.command.TraceCommand;
import com.cesarcosmico.framesort.command.TrustCommand;
import com.cesarcosmico.framesort.command.TrustedCommand;
import com.cesarcosmico.framesort.command.UntrustCommand;
import com.cesarcosmico.framesort.command.WhereCommand;
import com.cesarcosmico.framesort.config.CommandsConfig;
import com.cesarcosmico.framesort.config.ConfigFiles;
import com.cesarcosmico.framesort.config.ConfigHolder;
import com.cesarcosmico.framesort.config.ConfigReader;
import com.cesarcosmico.framesort.config.ConfigValidator;
import com.cesarcosmico.framesort.config.DatabaseSettings;
import com.cesarcosmico.framesort.config.DatabaseType;
import com.cesarcosmico.framesort.config.FrameSortSettings;
import com.cesarcosmico.framesort.config.PadSettings;
import com.cesarcosmico.framesort.config.TagSettings;
import com.cesarcosmico.framesort.integration.craftengine.CraftEngineItemResolver;
import com.cesarcosmico.framesort.integration.worldguard.WorldGuardListener;
import com.cesarcosmico.framesort.item.ItemTemplate;
import com.cesarcosmico.framesort.listener.InspectListener;
import com.cesarcosmico.framesort.listener.PadListener;
import com.cesarcosmico.framesort.listener.SorterListener;
import com.cesarcosmico.framesort.listener.TargetListener;
import com.cesarcosmico.framesort.model.TrustList;
import com.cesarcosmico.framesort.service.AutomarkService;
import com.cesarcosmico.framesort.service.CustomItemResolver;
import com.cesarcosmico.framesort.service.DeliveryService;
import com.cesarcosmico.framesort.service.HighlightService;
import com.cesarcosmico.framesort.service.InspectService;
import com.cesarcosmico.framesort.service.PadService;
import com.cesarcosmico.framesort.service.SorterService;
import com.cesarcosmico.framesort.service.TagCatalog;
import com.cesarcosmico.framesort.service.TargetIndex;
import com.cesarcosmico.framesort.service.TargetResolver;
import com.cesarcosmico.framesort.service.TraceService;
import com.cesarcosmico.framesort.service.TrustService;
import com.cesarcosmico.framesort.storage.Database;
import com.cesarcosmico.framesort.storage.TrustRepository;
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
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.logging.Level;

public final class FrameSortPlugin extends JavaPlugin {

    private static final String COMMAND_DESCRIPTION = "Sort items into item frames";
    // The server does not take players before onEnable returns, so a slow database only delays the start.
    private static final long TRUST_LOAD_SECONDS = 10;
    private static final long TRUST_REFRESH_TICKS = 60 * 20L;

    private ConfigHolder<FrameSortSettings> settings;
    private ConfigHolder<PadSettings> pads;
    private ConfigHolder<TagSettings> tagSettings;
    private ConfigHolder<Messages> messages;
    private TagCatalog tags;
    private TargetIndex index;
    private TargetResolver resolver;
    private SorterService sorters;
    private PadService padService;
    private HighlightService highlights;
    // Null in onDisable when the plugin failed to start, or before it got that far.
    private @Nullable BukkitTask ticker;
    private @Nullable Database database;
    private @Nullable BukkitTask trustRefresh;
    // Read once on enable: CraftEngine loads before FrameSort, and a reload checks the config against it too.
    private boolean craftEngine;

    @Override
    public void onEnable() {
        craftEngine = getServer().getPluginManager().isPluginEnabled("CraftEngine");
        Consumer<String> warn = getLogger()::warning;
        CommandsConfig commands;
        Database opened;
        TrustRepository trustRepository;
        TrustList trustList;
        try {
            settings = new ConfigHolder<>(loadSettings(warn));
            pads = new ConfigHolder<>(loadPads(warn));
            tagSettings = new ConfigHolder<>(loadTags(warn));
            messages = new ConfigHolder<>(Messages.load(this, settings.get().language(), warn));
            // Trusting players means nothing without owners, so those commands follow the setting.
            BooleanSupplier ownership = () -> settings.get().ownership();
            commands = CommandsConfig.load(this, warn).onlyWhen("trust", ownership).onlyWhen("untrust", ownership)
                    .onlyWhen("trusted", ownership);
            opened = Database.open(this, DatabaseSettings.parse(
                    new ConfigReader(ConfigFiles.load(this, "database.yml"), "database.yml", warn)));
            database = opened;
            trustRepository = new TrustRepository(opened.type());
            trustList = opened.query(trustRepository::load).get(TRUST_LOAD_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "FrameSort could not start: " + e.getMessage(), e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        tags = new TagCatalog(tagSettings);
        index = new TargetIndex(settings);
        TrustService trust = new TrustService(this, opened, trustRepository, trustList, settings, index);
        CustomItemResolver customItems = craftEngine ? new CraftEngineItemResolver() : CustomItemResolver.NONE;
        resolver = new TargetResolver(settings, index, tags, customItems, trust);
        TraceService trace = new TraceService(this, settings, messages, trust);
        DeliveryService delivery = new DeliveryService(this, resolver, customItems, trace::report);
        sorters = new SorterService(this, settings, delivery, customItems);
        padService = new PadService(this, pads, delivery, customItems);
        highlights = new HighlightService(this);
        InspectService inspect = new InspectService(this, highlights, settings, messages, index, resolver, trust,
                sorters, padService, commands);

        // Without the command a player could not switch it off, so it only acts while they may run it.
        AutomarkService automark = new AutomarkService(this, index, sorters, inspect, customItems,
                player -> commands.usageFor("automark", player::hasPermission) != null);

        register(new TargetListener(index, automark), new SorterListener(sorters, messages),
                new PadListener(padService, messages), new InspectListener(inspect, sorters, padService));
        if (getServer().getPluginManager().isPluginEnabled("WorldGuard")) {
            register(new WorldGuardListener());
        }
        List<String> helpLines = List.of("automark", "tag-list", "tag-search", "tag-show", "tag-apply", "tag-remove",
                "where", "trace", "trust", "untrust", "trusted", "give", "reload");
        TagListing listing = new TagListing(tags, messages, settings, commands);
        List<CommandFeature> features = List.of(
                new HelpCommand(messages, commands, helpLines),
                new AutomarkCommand(automark, messages),
                new TagListCommand(listing),
                new TagSearchCommand(listing),
                new TagShowCommand(tags, messages, settings, commands, customItems),
                new TagApplyCommand(tags, messages, settings, commands),
                new TagRemoveCommand(messages),
                new TraceCommand(trace, messages, settings),
                new WhereCommand(inspect, messages),
                new TrustCommand(trust, messages),
                new UntrustCommand(trust, messages),
                new TrustedCommand(trust, messages, settings),
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
        if (opened.type() != DatabaseType.SQLITE) {
            trustRefresh = getServer().getScheduler().runTaskTimer(this, trust::refresh, TRUST_REFRESH_TICKS,
                    TRUST_REFRESH_TICKS);
        }
    }

    @Override
    public void onDisable() {
        if (trustRefresh != null) {
            trustRefresh.cancel();
        }
        if (ticker != null) {
            ticker.cancel();
            sorters.showAllFrames();
            highlights.clearAll();
        }
        // Also when the start failed after opening it.
        if (database != null) {
            database.close();
        }
    }

    // Every file loads before anything is swapped, so a broken one keeps the previous state.
    private int reload() throws IOException, InvalidConfigurationException {
        List<String> warnings = new ArrayList<>();
        Consumer<String> warn = warning -> {
            warnings.add(warning);
            getLogger().warning(warning);
        };
        FrameSortSettings newSettings = loadSettings(warn);
        PadSettings newPads = loadPads(warn);
        TagSettings newTags = loadTags(warn);
        Messages newMessages = Messages.load(this, newSettings.language(), warn);
        settings.set(newSettings);
        pads.set(newPads);
        tagSettings.set(newTags);
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
        ConfigValidator.check(this, yaml, "config.yml", FrameSortSettings.OPEN_KEYS, FrameSortSettings.OPTIONAL_KEYS,
                warn);
        return FrameSortSettings.parse(new ConfigReader(yaml, "config.yml", warn), FrameSortPlugin::isItem,
                ItemTemplate::problem, craftEngine);
    }

    private PadSettings loadPads(Consumer<String> warn) throws IOException, InvalidConfigurationException {
        YamlConfiguration yaml = ConfigFiles.load(this, "pads.yml");
        ConfigValidator.check(this, yaml, "pads.yml", Set.of("types"), warn);
        return PadSettings.parse(new ConfigReader(yaml, "pads.yml", warn), Material::isBlock, FrameSortPlugin::isItem,
                craftEngine);
    }

    private TagSettings loadTags(Consumer<String> warn) throws IOException, InvalidConfigurationException {
        YamlConfiguration yaml = ConfigFiles.load(this, "tags.yml");
        ConfigValidator.check(this, yaml, "tags.yml", Set.of("tags"), warn);
        return TagSettings.parse(new ConfigReader(yaml, "tags.yml", warn), FrameSortPlugin::isItem,
                TagCatalog::isGameTag, craftEngine);
    }

    // Air is an item type too (ItemType.AIR), but no stack can hold it.
    private static boolean isItem(Material material) {
        return material.isItem() && !material.isAir();
    }

    private void register(Listener... listeners) {
        for (Listener listener : listeners) {
            getServer().getPluginManager().registerEvents(listener, this);
        }
    }
}
