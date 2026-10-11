package dev.vexsoft.core.common.service.identity;

import dev.vexsoft.core.api.player.identity.PlayerIdentity;
import dev.vexsoft.core.api.service.cache.CacheService;
import dev.vexsoft.core.api.service.player.PlayerIdentityService;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.cache.VexAsyncCache;
import dev.vexsoft.core.cache.VexCacheOptions;
import dev.vexsoft.core.common.data.identity.PlayerIdentityStore;
import dev.vexsoft.core.common.service.data.PlayerDataStoreService;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Default cached network-wide player identity index. */
@Dependencies({PlayerDataStoreService.class, CacheService.class})
public final class VexPlayerIdentityService implements PlayerIdentityService, AutoCloseable {

    private final PlayerIdentityStore store;
    private final CompletableFuture<Void> ready;
    private final VexAsyncCache<UUID, Optional<PlayerIdentity>> identitiesById;
    private final VexAsyncCache<String, Optional<PlayerIdentity>> identitiesByName;

    public VexPlayerIdentityService(final VexServiceRegistry services) {
        VexServiceRegistry checkedServices = Objects.requireNonNull(services, "services");

        PlayerDataStoreService storage = checkedServices.require(PlayerDataStoreService.class);
        store = storage.getPlayerIdentityStore();
        ready = storage.getReadiness();
        CacheService caches = checkedServices.require(CacheService.class);
        VexCacheOptions options =
            VexCacheOptions.builder().maximumSize(50_000L).expireAfterAccess(Duration.ofMinutes(30)).build();

        identitiesById = caches.createAsync("player-identities-by-id", options,
            id -> ready.thenCompose(ignored -> store.findPlayerIdentity(id)));
        identitiesByName = caches.createAsync("player-identities-by-name", options,
            name -> ready.thenCompose(ignored -> store.findPlayerIdentity(name)));
    }

    @Override
    public CompletableFuture<PlayerIdentity> record(final UUID uniqueId, final String name) {
        UUID checkedUniqueId = Objects.requireNonNull(uniqueId, "uniqueId");
        String checkedName = validateName(name);

        return ready.thenCompose(ignored -> store.recordPlayerIdentity(checkedUniqueId, checkedName)).thenApply(identity -> {
            identitiesById.put(checkedUniqueId, Optional.of(identity));
            identitiesByName.put(normalizeName(checkedName), Optional.of(identity));

            return identity;
        });
    }

    @Override
    public CompletableFuture<Optional<PlayerIdentity>> find(final UUID uniqueId) {
        return identitiesById.get(Objects.requireNonNull(uniqueId, "uniqueId"));
    }

    @Override
    public CompletableFuture<Optional<PlayerIdentity>> find(final String name) {
        return identitiesByName.get(normalizeName(validateName(name)));
    }

    @Override
    public void close() {
        identitiesById.invalidateAll();
        identitiesByName.invalidateAll();
    }

    private String validateName(final String name) {
        String checkedName = Objects.requireNonNull(name, "name").trim();

        if (checkedName.isEmpty() || checkedName.length() > 16) {
            throw new IllegalArgumentException("Player name must contain between 1 and 16 characters");
        }

        return checkedName;
    }

    private String normalizeName(final String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
