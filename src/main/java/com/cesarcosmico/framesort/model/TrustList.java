package com.cesarcosmico.framesort.model;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Whom each owner lets receive from their sorters and pads, with when they did, in epoch milliseconds. One way and
 * not transitive: trusting someone shares your network with them, not theirs with you.
 */
public record TrustList(Map<UUID, Map<UUID, Long>> grants) {

    public static final TrustList EMPTY = new TrustList(Map.of());

    public TrustList {
        Map<UUID, Map<UUID, Long>> copy = new HashMap<>();
        grants.forEach((owner, trusted) -> {
            if (!trusted.isEmpty()) {
                copy.put(owner, Map.copyOf(trusted));
            }
        });
        grants = Map.copyOf(copy);
    }

    /** Whether a source owned by {@code source} sends to a frame owned by {@code target}; no owner is one too. */
    public boolean allows(@Nullable UUID source, @Nullable UUID target) {
        return Objects.equals(source, target) || source != null && target != null && trusts(source, target);
    }

    public boolean trusts(UUID owner, UUID trusted) {
        return trustedBy(owner).containsKey(trusted);
    }

    public Map<UUID, Long> trustedBy(UUID owner) {
        return grants.getOrDefault(owner, Map.of());
    }

    public List<UUID> trusting(UUID player) {
        List<UUID> owners = new ArrayList<>();
        grants.forEach((owner, trusted) -> {
            if (trusted.containsKey(player)) {
                owners.add(owner);
            }
        });
        return List.copyOf(owners);
    }

    public TrustList with(UUID owner, UUID trusted, long since) {
        if (trusts(owner, trusted)) {
            return this;
        }
        Map<UUID, Long> changed = new HashMap<>(trustedBy(owner));
        changed.put(trusted, since);
        Map<UUID, Map<UUID, Long>> all = new HashMap<>(grants);
        all.put(owner, changed);
        return new TrustList(all);
    }

    public TrustList without(UUID owner, UUID trusted) {
        if (!trusts(owner, trusted)) {
            return this;
        }
        Map<UUID, Long> changed = new HashMap<>(trustedBy(owner));
        changed.remove(trusted);
        Map<UUID, Map<UUID, Long>> all = new HashMap<>(grants);
        all.put(owner, changed);
        return new TrustList(all);
    }
}
