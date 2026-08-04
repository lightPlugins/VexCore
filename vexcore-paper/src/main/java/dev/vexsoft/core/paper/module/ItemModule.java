package dev.vexsoft.core.paper.module;

import dev.vexsoft.core.api.service.ServiceRegistry;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import dev.vexsoft.core.item.internal.ItemComponentAdapterService;
import dev.vexsoft.core.item.version.ItemVersionDefinition;
import dev.vexsoft.core.paper.item.ItemVersions;

public final class ItemModule implements VexModule {

  private VexServiceRegistry services;

  @Override
  public void enable(final ServiceRegistry registry) {
    services = registry.scoped(this);
    ItemVersionDefinition definition = ItemVersions.select(services);
    services.register(ItemComponentAdapterService.class, definition.getComponentAdapter());
    services.registerQueuedServices();
  }

  @Override
  public String getServiceOwnerName() {
    return "vexcore-items";
  }
}
