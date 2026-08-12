package dev.vexsoft.core.api.service.requirement;

import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.requirement.Requirement;

/** Registers owner-scoped handlers for keys inside a {@code requirements} section. */
public interface RequirementRegistry extends VexService {

  /** Registers a requirement under a globally unique configuration key. */
  void register(String key, Class<? extends Requirement> requirementType);

  /** Removes a key when it belongs to this service owner. */
  boolean unregister(String key);
}
