package dev.vexsoft.core.paper.module;

import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class ModuleManager {
  @NonNull
  private final VexServiceRegistry services;
  private final List<VexModule> enabled = new ArrayList<>();
  private boolean started;

  public void enable(VexModule module) {
    try {
      module.enable(services);
      enabled.add(module);
    } catch (RuntimeException exception) {
      disableAll();
      throw exception;
    }
  }

  public void startAll() {
    if (started) {
      return;
    }
    try {
      for (VexModule module : enabled) {
        module.start();
      }
      started = true;
    } catch (RuntimeException exception) {
      disableAll();
      throw exception;
    }
  }

  public void disableAll() {
    List<VexModule> reverse = new ArrayList<>(enabled);
    // Dependants are started later, so they need to stop before their dependencies
    Collections.reverse(reverse);
    RuntimeException failure = null;

    for (VexModule module : reverse) {
      try {
        module.disable();
      } catch (RuntimeException exception) {
        failure = collectFailure(failure, exception);
      } finally {
        try {
          services.scoped(module).unregisterOwnedServices();
        } catch (RuntimeException exception) {
          failure = collectFailure(failure, exception);
        }
      }
    }
    enabled.clear();
    started = false;

    // One broken module must not prevent the remaining modules from cleaning up
    if (failure != null) {
      throw failure;
    }
  }

  private RuntimeException collectFailure(RuntimeException current, RuntimeException next) {
    if (current == null) {
      return next;
    }
    current.addSuppressed(next);
    return current;
  }
}
