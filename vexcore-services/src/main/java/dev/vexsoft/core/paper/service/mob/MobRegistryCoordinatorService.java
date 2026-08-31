package dev.vexsoft.core.paper.service.mob;

import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.paper.mob.MobDefinition;
import dev.vexsoft.core.paper.mob.MobKey;
import java.util.Collection;
import java.util.Optional;

/** Coordinates custom mob definitions across owner-scoped registry facades. */
public interface MobRegistryCoordinatorService extends VexService {

  MobDefinition register(ServiceOwner owner, MobDefinition definition);

  Collection<MobDefinition> synchronize(
      ServiceOwner owner,
      Collection<MobDefinition> definitions
  );

  Optional<MobDefinition> find(MobKey key);

  boolean unregister(ServiceOwner owner, MobKey key);

  void unregisterOwner(ServiceOwner owner);

  Collection<MobDefinition> getDefinitions();

  boolean owns(ServiceOwner owner, MobKey key);
}
