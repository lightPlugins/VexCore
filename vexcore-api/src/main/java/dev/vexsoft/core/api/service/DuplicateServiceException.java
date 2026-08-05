package dev.vexsoft.core.api.service;

/** Indicates that an owner attempted to register a service type more than once. */
public final class DuplicateServiceException extends IllegalStateException {
  /** Creates an exception identifying the duplicate service type and its owner. */
  public DuplicateServiceException(Class<? extends VexService> type, ServiceOwner owner) {
    super("Service " + type.getName() + " is already registered by " + owner.getServiceOwnerName());
  }
}
