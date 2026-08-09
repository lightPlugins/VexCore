package dev.vexsoft.core.common.service.stats;

import dev.vexsoft.core.stats.Stat;
import dev.vexsoft.core.stats.StatDefinition;
import dev.vexsoft.core.stats.StatKey;

import java.util.Objects;

final class RegisteredStat implements Stat {

  private final String owner;
  private final int runtimeId;
  private final long generation;
  private volatile StatDefinition definition;
  private volatile boolean registered = true;

  RegisteredStat(
      final String owner,
      final int runtimeId,
      final long generation,
      final StatDefinition definition
  ) {
    this.owner = Objects.requireNonNull(owner, "owner");
    this.runtimeId = runtimeId;
    this.generation = generation;
    this.definition = Objects.requireNonNull(definition, "definition");
  }

  @Override
  public StatKey getKey() {
    return definition.getKey();
  }

  @Override
  public StatDefinition getDefinition() {
    return definition;
  }

  @Override
  public int getRuntimeId() {
    return runtimeId;
  }

  @Override
  public boolean isRegistered() {
    return registered;
  }

  String getOwner() {
    return owner;
  }

  long getGeneration() {
    return generation;
  }

  void update(final StatDefinition updatedDefinition) {
    definition = Objects.requireNonNull(updatedDefinition, "updatedDefinition");
  }

  void unregister() {
    registered = false;
  }
}
