package dev.vexsoft.core.paper.service.packets;

import com.destroystokyo.paper.profile.PlayerProfile;
import dev.vexsoft.core.api.service.cache.CacheService;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.cache.VexAsyncCache;
import dev.vexsoft.core.cache.VexCache;
import dev.vexsoft.core.cache.VexCacheOptions;
import dev.vexsoft.core.paper.packets.dummy.SkinTexture;
import dev.vexsoft.core.paper.packets.service.SkinService;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Resolves player skins from profiles or cached requests and caches loaded textures. */
@Dependencies(CacheService.class)
public final class VexSkinService implements SkinService, AutoCloseable {

    private final VexCache<UUID, SkinTexture> skinCache;
    private final VexAsyncCache<UUID, Optional<SkinTexture>> skinRequests;

    private volatile boolean closed;

    public VexSkinService(final VexServiceRegistry services) {
        this(
            services.require(CacheService.class),
            playerId -> Bukkit.createProfile(playerId).update().thenApply(VexSkinService::readSkinTexture)
        );
    }

    /** Accepts a custom skin loader while retaining the same caching and timeout behavior. */
    public VexSkinService(
        final CacheService cacheService,
        final Function<UUID, CompletableFuture<Optional<SkinTexture>>> skinLoader
    ) {
        skinCache = cacheService.create(
            "player-skins",
            VexCacheOptions.builder().maximumSize(2048).expireAfterWrite(Duration.ofHours(1)).build()
        );

        skinRequests = cacheService.createAsync(
            "player-skin-requests",
            VexCacheOptions.builder().maximumSize(2048).expireAfterWrite(Duration.ofSeconds(30)).build(),
            playerId -> loadSkin(playerId, skinLoader)
        );
    }

    @Override
    public CompletableFuture<Optional<SkinTexture>> resolve(final Player player) {
        if (closed) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        UUID playerId = player.getUniqueId();
        Optional<SkinTexture> profileSkin = readSkinTexture(player.getPlayerProfile());

        if (profileSkin.isPresent()) {
            skinCache.put(playerId, profileSkin.get());

            return CompletableFuture.completedFuture(profileSkin);
        }

        Optional<SkinTexture> cachedSkin = skinCache.getIfPresent(playerId);

        if (cachedSkin.isPresent()) {
            return CompletableFuture.completedFuture(cachedSkin);
        }

        // Cancelling the returned future must not cancel the shared skin request.
        return skinRequests.get(playerId).thenApply(skin -> skin);
    }

    @Override
    public void close() {
        closed = true;
        skinRequests.invalidateAll();
        skinCache.invalidateAll();
    }

    private CompletableFuture<Optional<SkinTexture>> loadSkin(
        final UUID playerId,
        final Function<UUID, CompletableFuture<Optional<SkinTexture>>> skinLoader
    ) {
        CompletableFuture<Optional<SkinTexture>> skinRequest;

        try {
            skinRequest = skinLoader.apply(playerId);
        } catch (RuntimeException exception) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        return skinRequest.orTimeout(5, TimeUnit.SECONDS).handle((loadedSkin, failure) -> {
            Optional<SkinTexture> resolvedSkin = failure == null ? loadedSkin : Optional.empty();

            // A request may finish after this service has already cleared its caches.
            if (!closed) {
                resolvedSkin.ifPresent(skin -> skinCache.put(playerId, skin));
            }

            return resolvedSkin;
        });
    }

    private static Optional<SkinTexture> readSkinTexture(final PlayerProfile profile) {
        return profile.getProperties()
            .stream()
            .filter(property -> property.getName().equals("textures") && !property.getValue().isBlank())
            .findFirst()
            .map(property -> new SkinTexture(property.getValue(), property.getSignature()));
    }
}
