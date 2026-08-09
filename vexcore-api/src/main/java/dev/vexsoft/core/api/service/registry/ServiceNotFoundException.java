package dev.vexsoft.core.api.service.registry;

/** Indicates that no visible registration exists for a required service type. */
public final class ServiceNotFoundException extends IllegalStateException {
  /** Creates an exception identifying the unavailable service type. */
  public ServiceNotFoundException(Class<? extends VexService> type) {
    super("No service is registered for " + type.getName());
  }
}
