package dev.vexsoft.core.gameplay.reactor.registry;

import dev.vexsoft.core.api.service.ServiceOwner;
import dev.vexsoft.core.api.service.VexService;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import dev.vexsoft.core.gameplay.reactor.ReactionDefinition;
import dev.vexsoft.core.gameplay.reactor.context.ReactorContext;
import java.util.Collection;

/** Coordinates global component registrations and immutable runtime reaction snapshots. */
public interface ReactorRegistryCoordinatorService extends VexService {

  void register(
      ServiceOwner owner,
      VexServiceRegistry services,
      ReactorComponentKind kind,
      Class<?> componentType
  );

  void unregisterOwner(ServiceOwner owner, ReactorComponentKind kind);

  void reload(ServiceOwner owner, Collection<ReactionDefinition> definitions);

  void clear(ServiceOwner owner);

  void dispatch(String triggerId, ReactorContext context);
}
