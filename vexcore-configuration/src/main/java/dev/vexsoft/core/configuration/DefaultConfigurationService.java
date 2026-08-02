package dev.vexsoft.core.configuration;

import dev.vexsoft.core.api.configuration.ConfigurationOwner;
import dev.vexsoft.core.api.configuration.ConfigurationService;
import dev.vexsoft.core.api.configuration.PluginConfigurations;
import java.util.Objects;

public final class DefaultConfigurationService implements ConfigurationService {
  @Override
  public PluginConfigurations scoped(ConfigurationOwner owner) {
    return new DefaultPluginConfigurations(Objects.requireNonNull(owner, "owner"));
  }
}
