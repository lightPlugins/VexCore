package dev.vexsoft.core.common.service.execution;

import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import java.util.Optional;

/** Coordinates owner-scoped reward, cost, and requirement extension keys. */
public interface ExecutionComponentCoordinatorService extends VexService {

  /** Creates and registers one extension. */
  void register(
      ServiceOwner owner,
      VexServiceRegistry services,
      ExecutionComponentKind kind,
      String key,
      Class<?> type
  );

  /** Finds one active extension by category and key. */
  Optional<Object> find(ExecutionComponentKind kind, String key);

  /** Removes one extension if it belongs to the supplied owner. */
  boolean unregister(ServiceOwner owner, ExecutionComponentKind kind, String key);

  /** Removes all extensions in one category belonging to an owner. */
  void unregisterOwner(ServiceOwner owner, ExecutionComponentKind kind);
}
