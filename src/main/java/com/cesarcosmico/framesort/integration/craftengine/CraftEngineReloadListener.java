package com.cesarcosmico.framesort.integration.craftengine;

import com.cesarcosmico.framesort.service.SorterService;
import net.momirealms.craftengine.bukkit.api.event.CraftEngineReloadEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/** CraftEngine has its items only once it loads them, so a sorter recipe that names one waits for this. */
public final class CraftEngineReloadListener implements Listener {

    private final SorterService sorters;

    public CraftEngineReloadListener(SorterService sorters) {
        this.sorters = sorters;
    }

    @EventHandler
    public void onReload(CraftEngineReloadEvent event) {
        sorters.registerRecipes();
    }
}
