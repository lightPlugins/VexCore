package dev.vexsoft.core.paper.module;

import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.service.dialogs.DialogCoordinatorService;
import dev.vexsoft.core.paper.service.dialogs.VexDialogCoordinatorService;
import dev.vexsoft.core.paper.service.dialogs.VexDialogListener;
import dev.vexsoft.core.paper.service.listeners.ListenerService;

public final class DialogModule implements VexModule {

  private VexServiceRegistry services;

  @Override
  public void enable(final VexServiceRegistry registry) {
    services = registry.scoped(this);
    services.register(DialogCoordinatorService.class, VexDialogCoordinatorService.class);
    services.registerQueuedServices();
  }

  @Override
  public void start() {
    if (services == null) {
      throw new IllegalStateException("DialogModule has not been loaded yet");
    }
    services.require(ListenerService.class).register(VexDialogListener.class, services);
  }

  @Override
  public String getServiceOwnerName() {
    return "vexcore-dialog";
  }
}
