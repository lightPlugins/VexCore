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

@Dependencies(CacheService.class)
public final class VexSkinService implements SkinService, AutoCloseable {
  private final VexCache<UUID, SkinTexture> skins;
  private final VexAsyncCache<UUID, Optional<SkinTexture>> requests;
  private volatile boolean closed;

  public VexSkinService(VexServiceRegistry services) {
    this(
        services.require(CacheService.class),
        id -> Bukkit.createProfile(id).update().thenApply(VexSkinService::texture));
  }

  /** Injectable asynchronous resolver; caching and timeout behavior stay identical. */
  public VexSkinService(
      CacheService caches, Function<UUID, CompletableFuture<Optional<SkinTexture>>> loader) {
    skins =
        caches.create(
            "player-skins",
            VexCacheOptions.builder()
                .maximumSize(2048)
                .expireAfterWrite(Duration.ofHours(1))
                .build());
    requests =
        caches.createAsync(
            "player-skin-requests",
            VexCacheOptions.builder()
                .maximumSize(2048)
                .expireAfterWrite(Duration.ofSeconds(30))
                .build(),
            id -> {
              CompletableFuture<Optional<SkinTexture>> request;
              try {
                request = loader.apply(id);
              } catch (RuntimeException failure) {
                return CompletableFuture.completedFuture(Optional.empty());
              }
              return request
                  .orTimeout(5, TimeUnit.SECONDS)
                  .handle(
                      (result, failure) -> {
                        var value = failure == null ? result : Optional.<SkinTexture>empty();
                        if (!closed) {
                          value.ifPresent(skin -> skins.put(id, skin));
                        }
                        return value;
                      });
            });
  }

  public CompletableFuture<Optional<SkinTexture>> resolve(Player player) {
    if (closed) {
      return CompletableFuture.completedFuture(Optional.empty());
    }
    UUID id = player.getUniqueId();
    var existing = texture(player.getPlayerProfile());
    if (existing.isPresent()) {
      skins.put(id, existing.get());
      return CompletableFuture.completedFuture(existing);
    }
    var cached = skins.getIfPresent(id);
    return cached.isPresent()
        ? CompletableFuture.completedFuture(cached)
        : requests.get(id).thenApply(value -> value);
  }

  private static Optional<SkinTexture> texture(PlayerProfile profile) {
    return profile.getProperties().stream()
        .filter(p -> p.getName().equals("textures") && !p.getValue().isBlank())
        .findFirst()
        .map(p -> new SkinTexture(p.getValue(), p.getSignature()));
  }

  public void close() {
    closed = true;
    requests.invalidateAll();
    skins.invalidateAll();
  }
}
