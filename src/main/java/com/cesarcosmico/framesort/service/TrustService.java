package com.cesarcosmico.framesort.service;

import com.cesarcosmico.framesort.config.FrameSortSettings;
import com.cesarcosmico.framesort.model.TrustList;
import com.cesarcosmico.framesort.storage.Database;
import com.cesarcosmico.framesort.storage.TrustRepository;
import com.destroystokyo.paper.profile.PlayerProfile;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Who lets whom receive from their sorters and pads. Sorters work while their owner is offline, so the whole list
 * lives in memory and a delivery never waits for the database.
 */
public final class TrustService implements Supplier<TrustList> {

    public enum Grant { GRANTED, ALREADY, SELF, UNKNOWN_PLAYER, DISABLED, FAILED }

    public enum Revoke { REVOKED, NOT_TRUSTED, FAILED }

    private static final String SEE_OTHERS = "framesort.inspect.others";

    private final Plugin plugin;
    private final Database database;
    private final TrustRepository repository;
    private final Supplier<FrameSortSettings> settings;
    private final TargetIndex index;
    // Changed only on the database thread, in the task that writes or reads the table, so it follows the database's
    // order; the main thread only reads it.
    private volatile TrustList list;
    private volatile boolean refreshFailing;

    public TrustService(Plugin plugin, Database database, TrustRepository repository, TrustList initial,
                        Supplier<FrameSortSettings> settings, TargetIndex index) {
        this.plugin = plugin;
        this.database = database;
        this.repository = repository;
        this.list = initial;
        this.settings = settings;
        this.index = index;
    }

    @Override
    public TrustList get() {
        return list;
    }

    /** Whether {@code viewer} may see where a source of {@code owner} sends: inspecting it shows those chests. */
    public boolean canView(Player viewer, @Nullable UUID owner) {
        return !settings.get().ownership() || viewer.hasPermission(SEE_OTHERS)
                || owner != null && (owner.equals(viewer.getUniqueId()) || list.trusts(owner, viewer.getUniqueId()));
    }

    /**
     * The name the server's profile cache has for {@code player}, or the UUID when it has none. Not
     * {@code OfflinePlayer#getName}: for an offline player it reads their file from disk on every call.
     */
    public String name(UUID player) {
        PlayerProfile profile = plugin.getServer().createProfile(player);
        profile.completeFromCache();
        String name = profile.getName();
        return name == null || name.isEmpty() ? player.toString() : name;
    }

    /**
     * Makes {@code owner} trust the player named {@code typed}. Saved first, then applied, so a failed write never
     * leaves a trust in memory that the database doesn't have. {@code then} gets the result and the name to show,
     * on the main thread; not at all if {@code owner} leaves first, but the trust is applied anyway.
     */
    public void trust(Player owner, String typed, BiConsumer<Grant, String> then) {
        if (!settings.get().ownership()) {
            then.accept(Grant.DISABLED, typed);
            return;
        }
        // Only players this server has seen: looking a name up on the web would block the server.
        OfflinePlayer target = plugin.getServer().getOfflinePlayerIfCached(typed);
        if (target == null) {
            then.accept(Grant.UNKNOWN_PLAYER, typed);
            return;
        }
        UUID trusted = target.getUniqueId();
        String cached = target.getName();
        String name = cached == null ? typed : cached;
        if (trusted.equals(owner.getUniqueId())) {
            then.accept(Grant.SELF, name);
            return;
        }
        if (list.trusts(owner.getUniqueId(), trusted)) {
            then.accept(Grant.ALREADY, name);
            return;
        }
        long since = System.currentTimeMillis();
        database.update(connection -> {
            repository.grant(connection, owner.getUniqueId(), trusted, since);
            list = list.with(owner.getUniqueId(), trusted, since);
        }).whenComplete((done, error) -> {
            applied(error, "save");
            reply(owner, () -> then.accept(error == null ? Grant.GRANTED : Grant.FAILED, name));
        });
    }

    /** Found by the name in {@code owner}'s own list, so it works for a player this server no longer knows. */
    public void untrust(Player owner, String typed, BiConsumer<Revoke, String> then) {
        UUID trusted = null;
        String name = typed;
        for (UUID candidate : list.trustedBy(owner.getUniqueId()).keySet()) {
            String candidateName = name(candidate);
            if (candidateName.equalsIgnoreCase(typed)) {
                trusted = candidate;
                name = candidateName;
            }
        }
        if (trusted == null) {
            then.accept(Revoke.NOT_TRUSTED, typed);
            return;
        }
        UUID revoked = trusted;
        String shown = name;
        database.update(connection -> {
            repository.revoke(connection, owner.getUniqueId(), revoked);
            list = list.without(owner.getUniqueId(), revoked);
        }).whenComplete((done, error) -> {
            applied(error, "remove");
            reply(owner, () -> then.accept(error == null ? Revoke.REVOKED : Revoke.FAILED, shown));
        });
    }

    /** Reads the whole table again, so trust changed on another server sharing the database applies here too. */
    public void refresh() {
        database.query(connection -> {
            TrustList read = repository.load(connection);
            boolean differs = !read.equals(list);
            if (differs) {
                list = read;
            }
            return differs;
        }).whenComplete((differs, error) -> {
            if (error != null) {
                // Once per outage: the next read is a minute away and would fail the same way.
                if (!refreshFailing) {
                    refreshFailing = true;
                    plugin.getLogger().log(Level.WARNING,
                            "Could not read the trust list again; keeping the one in memory until it works.", error);
                }
                return;
            }
            if (refreshFailing) {
                refreshFailing = false;
                plugin.getLogger().info("Reading the trust list works again.");
            }
            if (differs) {
                invalidate();
            }
        });
    }

    private void applied(@Nullable Throwable error, String action) {
        if (error != null) {
            plugin.getLogger().log(Level.SEVERE, "Could not " + action + " a trust.", error);
            return;
        }
        invalidate();
    }

    // The cached matches live on the main thread. The global scheduler runs this even when the player who changed
    // the trust has left; their own scheduler would drop it.
    private void invalidate() {
        if (plugin.isEnabled()) {
            plugin.getServer().getScheduler().runTask(plugin, index::changedEverywhere);
        }
    }

    // While the plugin disables, the database finishes queued writes but nothing may be scheduled any more.
    private void reply(Player player, Runnable message) {
        if (plugin.isEnabled()) {
            player.getScheduler().run(plugin, task -> message.run(), null);
        }
    }
}
