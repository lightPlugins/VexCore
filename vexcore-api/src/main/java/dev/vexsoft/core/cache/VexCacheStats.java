package dev.vexsoft.core.cache;

import lombok.Value;

/**
 * Contains an immutable snapshot of cache performance counters
 */
@Value
public class VexCacheStats {
  long hitCount;
  long missCount;
  long loadSuccessCount;
  long loadFailureCount;
  long evictionCount;
  double hitRate;
  double averageLoadPenaltyNanos;
}
