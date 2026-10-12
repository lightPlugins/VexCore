package dev.vexsoft.core.api.network.admission;

import dev.vexsoft.core.api.network.ServerId;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Timestamped backend readiness and occupancy, including players still restoring their profile. */
public record BackendPresence(
    ServerId server, UUID instance, Set<UUID> players, Set<String> worlds, String profileDomain,
    boolean available, long observedAt
) {

    /** Detaches and validates one backend heartbeat. */
    public BackendPresence {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(instance, "instance");
        players = Set.copyOf(players);
        worlds = Set.copyOf(worlds);
        Objects.requireNonNull(profileDomain, "profileDomain");
        if (observedAt < 0 || players.size() > 10000 || worlds.size() > 10000) {
            throw new IllegalArgumentException("Invalid backend presence");
        }
    }
}
