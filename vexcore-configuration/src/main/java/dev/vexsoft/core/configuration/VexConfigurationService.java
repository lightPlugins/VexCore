package dev.vexsoft.core.configuration;

import dev.vexsoft.core.api.configuration.ConfigurationOwner;
import dev.vexsoft.core.api.configuration.ConfigurationService;
import dev.vexsoft.core.api.configuration.VexConfiguration;
import dev.vexsoft.core.api.service.Dependencies;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

@Dependencies
public final class VexConfigurationService implements ConfigurationService {

  private final DefaultPluginConfigurations configurations;

  public VexConfigurationService(final VexServiceRegistry services) {
    VexServiceRegistry checkedServices = Objects.requireNonNull(services, "services");
    if (!(checkedServices.owner() instanceof ConfigurationOwner configurationOwner)) {
      throw new IllegalArgumentException("ConfigurationService owner must support configurations");
    }
    this.configurations = new DefaultPluginConfigurations(configurationOwner);
  }

  @Override
  public ConfigurationOwner owner() {
    return configurations.owner();
  }

  @Override
  public VexConfiguration load(final String relativePath) {
    return configurations.load(relativePath);
  }

  @Override
  public VexConfiguration load(final Path relativePath) {
    return configurations.load(relativePath);
  }

  @Override
  public VexConfiguration load(final Path relativePath, final boolean loadDefaults) {
    return configurations.load(relativePath, loadDefaults);
  }

  @Override
  public VexConfiguration load(final Path relativePath, final String defaultsResource) {
    return configurations.load(relativePath, defaultsResource);
  }

  @Override
  public Map<Path, VexConfiguration> loadDirectory(final Path relativeDirectory) {
    return configurations.loadDirectory(relativeDirectory);
  }
}
