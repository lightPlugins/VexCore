package dev.vexsoft.core.api.service;

public final class DuplicateServiceException extends IllegalStateException {
  public DuplicateServiceException(Class<? extends VexService> type, ServiceOwner owner) {
    super("Service " + type.getName() + " is already registered by " + owner.serviceOwnerName());
  }
}
