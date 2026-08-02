package dev.vexsoft.core.paper.module;

import dev.vexsoft.core.api.service.ServiceRegistry;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import dev.vexsoft.core.paper.platform.PlatformService;
import dev.vexsoft.core.paper.platform.VexPlatformService;

public final class PlatformModule implements VexModule {

  private VexServiceRegistry services;

  @Override
  public void enable(final ServiceRegistry registry) {
    services = registry.scoped(this);
    services.register(PlatformService.class, VexPlatformService.class);
    services.registerQueuedServices();
  }

  @Override
  public void disable() {
    if (services != null) {
      services.unregisterOwnedServices();
    }
  }

  @Override
  public String serviceOwnerName() {
    return "vexcore-platform";
  }
}
