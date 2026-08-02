package dev.vexsoft.core.api.configuration;

import dev.vexsoft.core.api.service.VexService;

public interface ConfigurationService extends VexService {
  /** Creates a configuration facade bound to the specified owner */
  public PluginConfigurations scoped(ConfigurationOwner owner);
}
