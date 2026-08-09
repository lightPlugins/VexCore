package dev.vexsoft.core.common.service.cache;

import dev.vexsoft.core.cache.VexAsyncCache;
import dev.vexsoft.core.cache.VexCache;

import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.common.service.registry.DefaultServiceRegistry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public final class VexCacheServiceTest {

  @Test
  public void cachesValuesAndRecordsStatistics() {
    VexCacheService service = service();
    VexCache<String, Integer> cache = service.create("profiles");

    assertFalse(cache.getIfPresent("vex").isPresent());
    cache.put("vex", 25);

    assertEquals(25, cache.getIfPresent("vex").orElseThrow());
    assertEquals(1L, cache.getStats().getHitCount());
    assertEquals(1L, cache.getStats().getMissCount());
  }

  @Test
  public void sharesOneAsynchronousLoadBetweenCallers() {
    VexCacheService service = service();
    AtomicInteger loads = new AtomicInteger();
    CompletableFuture<String> loaded = new CompletableFuture<>();
    VexAsyncCache<Integer, String> cache = service.createAsync("profiles", key -> {
      loads.incrementAndGet();
      return loaded;
    });

    CompletableFuture<String> first = cache.get(5);
    CompletableFuture<String> second = cache.get(5);

    assertSame(first, second);
    assertEquals(1, loads.get());
    loaded.complete("Vex");
    assertEquals("Vex", first.join());
  }

  @Test
  public void destroysOwnedCacheContents() {
    VexCacheService service = service();
    VexCache<String, String> cache = service.create("items");
    cache.put("sword", "cached");

    service.destroyAll();

    assertFalse(cache.getIfPresent("sword").isPresent());
  }

  @Test
  public void rejectsDuplicateNormalizedNames() {
    VexCacheService service = service();
    service.create("Player-Data");

    assertThrows(IllegalStateException.class, () -> service.create("player-data"));
  }

  private VexCacheService service() {
    DefaultServiceRegistry registry = new DefaultServiceRegistry();
    ServiceOwner owner = () -> "test-plugin";
    return new VexCacheService(registry.scoped(owner));
  }
}
