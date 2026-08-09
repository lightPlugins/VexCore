package dev.vexsoft.core.common.service.placeholder;

import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.placeholder.PlaceholderContext;
import dev.vexsoft.core.placeholder.VexPlaceholder;

/** Coordinates global placeholder identities behind owner-scoped facades. */
public interface PlaceholderRegistryCoordinatorService extends VexService {

  /** Creates and registers one placeholder owned by the supplied scope. */
  <T extends VexPlaceholder> T register(
      ServiceOwner owner,
      VexServiceRegistry services,
      Class<T> placeholderType
  );

  /** Resolves a compiled placeholder template. */
  String resolve(PlaceholderContext context, String input);

  /** Removes every placeholder owned by the supplied scope. */
  void unregisterOwner(ServiceOwner owner);
}
