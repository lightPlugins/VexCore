package dev.vexsoft.core.paper.module;

import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.service.platform.PlatformService;
import dev.vexsoft.core.paper.service.platform.VexPlatformService;

public final class PlatformModule implements VexModule {

  private VexServiceRegistry services;

  @Override
  public void enable(final VexServiceRegistry registry) {
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
  public String getServiceOwnerName() {
    return "vexcore-platform";
  }
}
