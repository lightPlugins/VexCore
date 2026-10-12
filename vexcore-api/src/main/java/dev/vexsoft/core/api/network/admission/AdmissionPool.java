package dev.vexsoft.core.api.network.admission;

import dev.vexsoft.core.api.player.profile.ProfileDomain;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Immutable routing policy; an empty profile domain explicitly selects account-only backends. */
public record AdmissionPool(
    String id, String profileDomain, List<AdmissionEndpoint> endpoints, int maximumQueued,
    long leaseMillis, long heartbeatMaxAgeMillis
) {

    /** Validates and detaches a complete server pool. */
    public AdmissionPool {
        if (id == null || !id.matches("[a-z][a-z0-9_-]{0,47}") || profileDomain == null) {
            throw new IllegalArgumentException("Invalid admission pool identity");
        }
        if (!profileDomain.isEmpty()) {
            new ProfileDomain(profileDomain);
        }
        endpoints = List.copyOf(Objects.requireNonNull(endpoints, "endpoints"));
        if (endpoints.size() > 64 || maximumQueued < 1 || maximumQueued > 4096
            || leaseMillis < 15000 || leaseMillis > 300000
            || heartbeatMaxAgeMillis < 3000 || heartbeatMaxAgeMillis > 60000) {
            throw new IllegalArgumentException("Invalid admission pool limits");
        }
        var servers = new HashSet<String>();
        for (var endpoint : endpoints) {
            if (!servers.add(endpoint.destination().server().value())) {
                throw new IllegalArgumentException("Duplicate admission backend");
            }
        }
    }
}
