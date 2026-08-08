package dev.vexsoft.core.gameplay.stat;

import dev.vexsoft.core.api.service.Dependencies;
import dev.vexsoft.core.api.service.ServiceOwner;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Default global coordinator for dynamic stat registrations. */
@Dependencies
public final class VexStatRegistryCoordinatorService implements StatRegistryCoordinatorService {

  private final Map<StatKey, Slot> slots = new LinkedHashMap<>();
  private final Set<VexStatContainer> containers = new LinkedHashSet<>();
  private int nextSlot;

  public VexStatRegistryCoordinatorService(final VexServiceRegistry services) {
    Objects.requireNonNull(services, "services");
  }

  @Override
  public synchronized Stat register(
      final ServiceOwner owner,
      final StatDefinition definition
  ) {
    String ownerName = ownerName(owner);
    StatDefinition checkedDefinition = requireOwned(ownerName, definition);
    Slot slot = slots.computeIfAbsent(
        checkedDefinition.getKey(),
        ignored -> new Slot(nextSlot++)
    );
    if (slot.active != null) {
      if (!slot.active.getOwner().equals(ownerName)) {
        throw new IllegalStateException("Stat is owned by another plugin: " + definition.getKey());
      }
      slot.active.update(checkedDefinition);
      notifyDefinitionChanged(slot.active);
      return slot.active;
    }
    RegisteredStat registered = new RegisteredStat(
        ownerName,
        slot.runtimeId,
        ++slot.generation,
        checkedDefinition
    );
    slot.active = registered;
    for (VexStatContainer container : containers) {
      container.activate(registered);
    }
    return registered;
  }

  @Override
  public synchronized List<Stat> synchronize(
      final ServiceOwner owner,
      final Collection<StatDefinition> definitions
  ) {
    String ownerName = ownerName(owner);
    Collection<StatDefinition> checkedDefinitions = Objects.requireNonNull(
        definitions,
        "definitions"
    );
    Map<StatKey, StatDefinition> desired = new LinkedHashMap<>();
    for (StatDefinition definition : checkedDefinitions) {
      StatDefinition checked = requireOwned(ownerName, definition);
      if (desired.putIfAbsent(checked.getKey(), checked) != null) {
        throw new IllegalArgumentException("Duplicate stat definition: " + checked.getKey());
      }
    }
    validateOwnership(ownerName, desired.keySet());

    Set<StatKey> removed = new HashSet<>();
    for (Map.Entry<StatKey, Slot> entry : slots.entrySet()) {
      RegisteredStat active = entry.getValue().active;
      if (active != null && active.getOwner().equals(ownerName)
          && !desired.containsKey(entry.getKey())) {
        removed.add(entry.getKey());
      }
    }
    for (StatKey key : removed) {
      unregister(owner, key);
    }
    List<Stat> result = new ArrayList<>(desired.size());
    for (StatDefinition definition : desired.values()) {
      result.add(register(owner, definition));
    }
    return List.copyOf(result);
  }

  @Override
  public synchronized Optional<Stat> find(final StatKey key) {
    Slot slot = slots.get(Objects.requireNonNull(key, "key"));
    return slot == null || slot.active == null
        ? Optional.empty()
        : Optional.of(slot.active);
  }

  @Override
  public synchronized boolean unregister(final ServiceOwner owner, final StatKey key) {
    String ownerName = ownerName(owner);
    Slot slot = slots.get(Objects.requireNonNull(key, "key"));
    if (slot == null || slot.active == null) {
      return false;
    }
    RegisteredStat active = slot.active;
    if (!active.getOwner().equals(ownerName)) {
      throw new IllegalArgumentException("Stat is owned by another plugin: " + key);
    }
    slot.active = null;
    active.unregister();
    for (VexStatContainer container : containers) {
      container.deactivate(active);
    }
    return true;
  }

  @Override
  public synchronized void unregisterOwner(final ServiceOwner owner) {
    String ownerName = ownerName(owner);
    List<StatKey> owned = slots.entrySet().stream()
        .filter(entry -> entry.getValue().active != null)
        .filter(entry -> entry.getValue().active.getOwner().equals(ownerName))
        .map(Map.Entry::getKey)
        .toList();
    for (StatKey key : owned) {
      unregister(owner, key);
    }
  }

  @Override
  public synchronized Collection<Stat> getRegisteredStats() {
    return slots.values().stream()
        .map(slot -> slot.active)
        .filter(Objects::nonNull)
        .map(Stat.class::cast)
        .toList();
  }

  @Override
  public synchronized void attach(final VexStatContainer container) {
    VexStatContainer checkedContainer = Objects.requireNonNull(container, "container");
    if (!containers.add(checkedContainer)) {
      return;
    }
    for (Slot slot : slots.values()) {
      if (slot.active != null) {
        checkedContainer.activate(slot.active);
      }
    }
  }

  @Override
  public synchronized void detach(final VexStatContainer container) {
    containers.remove(Objects.requireNonNull(container, "container"));
  }

  private void notifyDefinitionChanged(final RegisteredStat stat) {
    for (VexStatContainer container : containers) {
      container.definitionChanged(stat);
    }
  }

  private void validateOwnership(final String owner, final Collection<StatKey> keys) {
    for (StatKey key : keys) {
      Slot slot = slots.get(key);
      if (slot != null && slot.active != null && !slot.active.getOwner().equals(owner)) {
        throw new IllegalStateException("Stat is owned by another plugin: " + key);
      }
    }
  }

  private static StatDefinition requireOwned(
      final String owner,
      final StatDefinition definition
  ) {
    StatDefinition checked = Objects.requireNonNull(definition, "definition");
    if (!checked.getKey().namespace().equals(owner)) {
      throw new IllegalArgumentException(
          "Stat namespace must match its owner '" + owner + "': " + checked.getKey()
      );
    }
    return checked;
  }

  static String ownerName(final ServiceOwner owner) {
    String normalized = Objects.requireNonNull(owner, "owner")
        .getServiceOwnerName()
        .trim()
        .toLowerCase(Locale.ROOT)
        .replace('-', '_');
    if (!normalized.matches("[a-z][a-z0-9_]{0,62}")) {
      throw new IllegalArgumentException("Invalid stat owner name: " + normalized);
    }
    return normalized;
  }

  private static final class Slot {

    private final int runtimeId;
    private long generation;
    private RegisteredStat active;

    private Slot(final int runtimeId) {
      this.runtimeId = runtimeId;
    }
  }
}
