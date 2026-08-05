package dev.vexsoft.core.cache;

import java.time.Duration;
import lombok.Builder;
import lombok.Value;

/**
 * Defines memory limits, expiration and statistics for a local cache
 */
@Value
@Builder(toBuilder = true)
public class VexCacheOptions {

  private static final long DEFAULT_MAXIMUM_SIZE = 10_000L;

  @Builder.Default
  long maximumSize = DEFAULT_MAXIMUM_SIZE;
  Duration expireAfterAccess;
  Duration expireAfterWrite;
  @Builder.Default
  boolean recordStats = true;

  /** Creates options using safe defaults for a bounded cache */
  public static VexCacheOptions defaults() {
    return VexCacheOptions.builder().build();
  }

  /** Validates the options before a cache is created */
  public void validate() {
    if (maximumSize < 1L) {
      throw new IllegalArgumentException("maximumSize must be greater than zero");
    }
    requirePositive(expireAfterAccess, "expireAfterAccess");
    requirePositive(expireAfterWrite, "expireAfterWrite");
  }

  private void requirePositive(final Duration duration, final String name) {
    if (duration != null && (duration.isZero() || duration.isNegative())) {
      throw new IllegalArgumentException(name + " must be greater than zero");
    }
  }
}
