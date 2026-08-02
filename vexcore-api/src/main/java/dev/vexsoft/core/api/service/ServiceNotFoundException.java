package dev.vexsoft.core.api.service;

public final class ServiceNotFoundException extends IllegalStateException {
  public ServiceNotFoundException(Class<? extends VexService> type) {
    super("No service is registered for " + type.getName());
  }
}
