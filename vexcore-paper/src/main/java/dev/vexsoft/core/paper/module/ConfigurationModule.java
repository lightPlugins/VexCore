package dev.vexsoft.core.paper.module;

import dev.vexsoft.core.api.configuration.ConfigurationService;
import dev.vexsoft.core.api.service.PluginServiceRegistry;
import dev.vexsoft.core.api.service.ServiceRegistry;
import dev.vexsoft.core.configuration.DefaultConfigurationService;

public final class ConfigurationModule implements VexModule {
  private PluginServiceRegistry services;

  @Override
  public void enable(ServiceRegistry registry) {
    services = registry.scoped(this);
    services.register(ConfigurationService.class, new DefaultConfigurationService());
  }

  @Override
  public void disable() {
    if (services != null) {
      services.unregisterOwnedServices();
    }
  }

  @Override public String serviceOwnerName() { return "vexcore-configuration"; }
}
