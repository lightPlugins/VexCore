package dev.vexsoft.core.common.cache;

import dev.vexsoft.core.cache.VexCacheOptions;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

public final class VexCacheOptionsTest {

  @Test
  public void acceptsSafeDefaults() {
    assertDoesNotThrow(VexCacheOptions.defaults()::validate);
  }

  @Test
  public void rejectsUnboundedOrImmediateCaches() {
    assertThrows(
        IllegalArgumentException.class,
        () -> VexCacheOptions.builder().maximumSize(0).build().validate()
    );
    assertThrows(
        IllegalArgumentException.class,
        () -> VexCacheOptions.builder()
            .expireAfterWrite(Duration.ZERO)
            .build()
            .validate()
    );
  }
}
