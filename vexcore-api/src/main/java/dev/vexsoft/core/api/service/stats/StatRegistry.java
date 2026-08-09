package dev.vexsoft.core.api.service.stats;

import dev.vexsoft.core.gameplay.stat.Stat;
import dev.vexsoft.core.gameplay.stat.StatDefinition;
import dev.vexsoft.core.gameplay.stat.StatKey;

import dev.vexsoft.core.api.service.registry.VexService;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Owner-scoped registry for dynamically available stats. */
public interface StatRegistry extends VexService {

  /** Registers a new stat or updates an active stat owned by this registry scope. */
  Stat register(StatDefinition definition);

  /** Atomically reconciles every stat owned by this registry scope. */
  List<Stat> synchronize(Collection<StatDefinition> definitions);

  /** Finds an active stat from any owner by its stable key. */
  Optional<Stat> find(StatKey key);

  /** Returns an active stat or fails if it is unavailable. */
  Stat require(StatKey key);

  /** Removes an owned stat from runtime without deleting persisted player values. */
  boolean unregister(StatKey key);

  /** Returns a snapshot of every currently active stat. */
  Collection<Stat> getRegisteredStats();
}
