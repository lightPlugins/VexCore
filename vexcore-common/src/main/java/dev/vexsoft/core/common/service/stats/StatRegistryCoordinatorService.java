package dev.vexsoft.core.common.service.stats;

import dev.vexsoft.core.gameplay.stat.Stat;
import dev.vexsoft.core.gameplay.stat.StatDefinition;
import dev.vexsoft.core.gameplay.stat.StatKey;

import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexService;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Coordinates global runtime slots behind owner-scoped stat registries. */
public interface StatRegistryCoordinatorService extends VexService {

  /** Registers or updates a definition owned by the supplied service owner. */
  Stat register(ServiceOwner owner, StatDefinition definition);

  /** Reconciles every definition belonging to one service owner. */
  List<Stat> synchronize(ServiceOwner owner, Collection<StatDefinition> definitions);

  /** Finds an active stat by key. */
  Optional<Stat> find(StatKey key);

  /** Removes one active stat if it belongs to the supplied owner. */
  boolean unregister(ServiceOwner owner, StatKey key);

  /** Removes all active stats belonging to the supplied owner. */
  void unregisterOwner(ServiceOwner owner);

  /** Returns every active stat. */
  Collection<Stat> getRegisteredStats();

  /** Attaches a loaded stat container for dynamic registration updates. */
  void attach(VexStatContainer container);

  /** Detaches a closed stat container. */
  void detach(VexStatContainer container);
}
