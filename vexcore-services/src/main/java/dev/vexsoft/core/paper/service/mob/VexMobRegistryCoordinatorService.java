package dev.vexsoft.core.paper.service.mob;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.mob.MobDefinition;
import dev.vexsoft.core.paper.mob.MobKey;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Default global coordinator for custom mob definitions. */
@Dependencies(MobRuntimeCoordinatorService.class)
public final class VexMobRegistryCoordinatorService implements MobRegistryCoordinatorService {

  private final Map<MobKey, Registration> definitions = new LinkedHashMap<>();
  private final MobRuntimeCoordinatorService runtime;

  public VexMobRegistryCoordinatorService(final VexServiceRegistry services) {
    runtime = services.require(MobRuntimeCoordinatorService.class);
  }

  @Override
  public synchronized MobDefinition register(
      final ServiceOwner owner,
      final MobDefinition definition
  ) {
    String ownerName = ownerName(owner);
    MobDefinition checked = requireOwned(ownerName, definition);
    Registration current = definitions.get(checked.key());
    if (current != null && !current.ownerName.equals(ownerName)) {
      throw new IllegalStateException("Mob definition is owned by another plugin: " + checked.key());
    }
    definitions.put(checked.key(), new Registration(ownerName, checked));
    return checked;
  }

  @Override
  public synchronized Collection<MobDefinition> synchronize(
      final ServiceOwner owner,
      final Collection<MobDefinition> desiredDefinitions
  ) {
    String ownerName = ownerName(owner);
    Map<MobKey, MobDefinition> desired = new LinkedHashMap<>();
    for (MobDefinition definition : Objects.requireNonNull(desiredDefinitions, "definitions")) {
      MobDefinition checked = requireOwned(ownerName, definition);
      if (desired.putIfAbsent(checked.key(), checked) != null) {
        throw new IllegalArgumentException("Duplicate mob definition: " + checked.key());
      }
    }
    Set<MobKey> removed = new LinkedHashSet<>();
    definitions.forEach((key, registration) -> {
      if (registration.ownerName.equals(ownerName) && !desired.containsKey(key)) {
        removed.add(key);
      }
    });
    removed.forEach(key -> unregister(owner, key));
    desired.values().forEach(definition -> register(owner, definition));
    return ListCopy.copy(desired.values());
  }

  @Override
  public synchronized Optional<MobDefinition> find(final MobKey key) {
    Registration registration = definitions.get(Objects.requireNonNull(key, "key"));
    return registration == null ? Optional.empty() : Optional.of(registration.definition);
  }

  @Override
  public synchronized boolean unregister(final ServiceOwner owner, final MobKey key) {
    String ownerName = ownerName(owner);
    Registration registration = definitions.get(Objects.requireNonNull(key, "key"));
    if (registration == null) {
      return false;
    }
    if (!registration.ownerName.equals(ownerName)) {
      throw new IllegalArgumentException("Mob definition is owned by another plugin: " + key);
    }
    runtime.removeDefinition(owner, key);
    definitions.remove(key);
    return true;
  }

  @Override
  public synchronized void unregisterOwner(final ServiceOwner owner) {
    String ownerName = ownerName(owner);
    definitions.entrySet().stream()
        .filter(entry -> entry.getValue().ownerName.equals(ownerName))
        .map(Map.Entry::getKey)
        .toList()
        .forEach(key -> unregister(owner, key));
  }

  @Override
  public synchronized Collection<MobDefinition> getDefinitions() {
    return definitions.values().stream().map(registration -> registration.definition).toList();
  }

  @Override
  public synchronized boolean owns(final ServiceOwner owner, final MobKey key) {
    Registration registration = definitions.get(Objects.requireNonNull(key, "key"));
    return registration != null && registration.ownerName.equals(ownerName(owner));
  }

  static String ownerName(final ServiceOwner owner) {
    String normalized = Objects.requireNonNull(owner, "owner").getServiceOwnerName()
        .trim().toLowerCase(Locale.ROOT).replace('-', '_');
    if (!normalized.matches("[a-z][a-z0-9_]{0,62}")) {
      throw new IllegalArgumentException("Invalid mob owner name: " + normalized);
    }
    return normalized;
  }

  private static MobDefinition requireOwned(
      final String ownerName,
      final MobDefinition definition
  ) {
    MobDefinition checked = Objects.requireNonNull(definition, "definition");
    if (!checked.key().namespace().equals(ownerName)) {
      throw new IllegalArgumentException(
          "Mob namespace must match its owner '" + ownerName + "': " + checked.key()
      );
    }
    return checked;
  }

  private record Registration(String ownerName, MobDefinition definition) { }

  private static final class ListCopy {
    private ListCopy() { }

    private static <T> Collection<T> copy(final Collection<T> values) {
    return List.copyOf(values);
    }
  }
}
