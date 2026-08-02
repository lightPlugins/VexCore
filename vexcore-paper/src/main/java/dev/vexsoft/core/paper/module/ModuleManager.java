package dev.vexsoft.core.paper.module;

import dev.vexsoft.core.api.service.ServiceRegistry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ModuleManager {
  private final ServiceRegistry services;
  private final List<VexModule> enabled = new ArrayList<>();

  public ModuleManager(ServiceRegistry services) {
    this.services = services;
  }

  public void enable(VexModule module) {
    try {
      module.enable(services);
      enabled.add(module);
    } catch (RuntimeException exception) {
      disableAll();
      throw exception;
    }
  }

  public void disableAll() {
    List<VexModule> reverse = new ArrayList<>(enabled);
    // Dependants are started later, so they need to stop before their dependencies
    Collections.reverse(reverse);
    for (VexModule module : reverse) {
      try {
        module.disable();
      } finally {
        services.unregisterOwnedBy(module);
      }
    }
    enabled.clear();
  }
}
