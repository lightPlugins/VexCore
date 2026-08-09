package dev.vexsoft.core.common.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import dev.vexsoft.core.cache.VexCacheOptions;
import java.util.Objects;
import lombok.experimental.UtilityClass;

@UtilityClass
public class VexCaffeineFactory {

  public static Caffeine<Object, Object> create(final VexCacheOptions options) {
    VexCacheOptions checkedOptions = Objects.requireNonNull(options, "options");
    checkedOptions.validate();
    Caffeine<Object, Object> builder = Caffeine.newBuilder()
        .maximumSize(checkedOptions.getMaximumSize());
    if (checkedOptions.getExpireAfterAccess() != null) {
      builder.expireAfterAccess(checkedOptions.getExpireAfterAccess());
    }
    if (checkedOptions.getExpireAfterWrite() != null) {
      builder.expireAfterWrite(checkedOptions.getExpireAfterWrite());
    }
    if (checkedOptions.isRecordStats()) {
      builder.recordStats();
    }
    return builder;
  }
}
