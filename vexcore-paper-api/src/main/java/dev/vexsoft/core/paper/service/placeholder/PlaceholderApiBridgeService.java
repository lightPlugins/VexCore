package dev.vexsoft.core.paper.service.placeholder;

import dev.vexsoft.core.api.service.registry.VexService;

/** Manages the optional PlaceholderAPI expansion for one plugin scope. */
public interface PlaceholderApiBridgeService extends VexService {

  /** Registers the expansion when PlaceholderAPI is available. */
  void enable();
}
