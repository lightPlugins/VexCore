package dev.vexsoft.core.common.service.globaldata;

import dev.vexsoft.core.common.data.global.StoredGlobalData;
import java.util.Optional;

/** Cache representation that can distinguish a missing row from an unloaded value. */
record GlobalCacheEntry(Optional<StoredGlobalData> stored) {

  GlobalCacheEntry {
    stored = Optional.ofNullable(stored).orElseGet(Optional::empty);
  }
}
