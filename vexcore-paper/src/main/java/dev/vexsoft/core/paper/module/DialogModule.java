package dev.vexsoft.core.paper.module;

import dev.vexsoft.core.api.service.VexServiceRegistry;
import dev.vexsoft.core.paper.dialog.DialogCoordinatorService;
import dev.vexsoft.core.paper.dialog.VexDialogCoordinatorService;
import dev.vexsoft.core.paper.dialog.VexDialogListener;
import dev.vexsoft.core.paper.listener.ListenerService;

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
    services.require(ListenerService.class).register(VexDialogListener.class);
  }

  @Override
  public String getServiceOwnerName() {
    return "vexcore-dialog";
  }
}
