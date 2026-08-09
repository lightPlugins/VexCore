package dev.vexsoft.core.common.cache;

import com.github.benmanes.caffeine.cache.stats.CacheStats;
import dev.vexsoft.core.cache.VexCacheStats;
import lombok.experimental.UtilityClass;

@UtilityClass
public class VexCacheStatistics {

  public static VexCacheStats snapshot(final CacheStats stats) {
    return new VexCacheStats(
        stats.hitCount(),
        stats.missCount(),
        stats.loadSuccessCount(),
        stats.loadFailureCount(),
        stats.evictionCount(),
        stats.hitRate(),
        stats.averageLoadPenalty()
    );
  }
}
