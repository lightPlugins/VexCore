package dev.vexsoft.core.common.service.execution;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexClassFactory;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.cost.Cost;
import dev.vexsoft.core.requirement.Requirement;
import dev.vexsoft.core.reward.Reward;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Default synchronized extension registry shared by the three execution domains. */
@Dependencies
public final class VexExecutionComponentCoordinatorService
    implements ExecutionComponentCoordinatorService, AutoCloseable {

  private final Map<ExecutionComponentKind, Map<String, RegisteredComponent>> components =
      new EnumMap<>(ExecutionComponentKind.class);

  /** Creates empty registries for every extension category. */
  public VexExecutionComponentCoordinatorService(final VexServiceRegistry services) {
    Objects.requireNonNull(services, "services");
    for (ExecutionComponentKind kind : ExecutionComponentKind.values()) {
      components.put(kind, new LinkedHashMap<>());
    }
  }

  @Override
  public synchronized void register(
      final ServiceOwner owner,
      final VexServiceRegistry services,
      final ExecutionComponentKind kind,
      final String key,
      final Class<?> type
  ) {
    String checkedKey = validateKey(key);
    Class<?> expected = expectedType(kind);
    if (!expected.isAssignableFrom(Objects.requireNonNull(type, "type"))) {
      throw new IllegalArgumentException(type.getName() + " does not implement " + expected.getName());
    }
    Map<String, RegisteredComponent> registry = components.get(kind);
    RegisteredComponent existing = registry.get(checkedKey);
    if (existing != null) {
      throw new IllegalStateException(
          "Duplicate " + kind.name().toLowerCase() + " key '" + checkedKey
              + "' owned by " + existing.owner().getServiceOwnerName()
      );
    }
    Object component = VexClassFactory.create(type, services, kind.name());
    registry.put(checkedKey, new RegisteredComponent(owner, component));
  }

  @Override
  public synchronized Optional<Object> find(
      final ExecutionComponentKind kind,
      final String key
  ) {
    RegisteredComponent registered = components.get(kind).get(validateKey(key));
    return registered == null ? Optional.empty() : Optional.of(registered.component());
  }

  @Override
  public synchronized boolean unregister(
      final ServiceOwner owner,
      final ExecutionComponentKind kind,
      final String key
  ) {
    Map<String, RegisteredComponent> registry = components.get(kind);
    RegisteredComponent registered = registry.get(validateKey(key));
    if (registered == null || registered.owner() != owner) {
      return false;
    }
    registry.remove(key);
    return true;
  }

  @Override
  public synchronized void unregisterOwner(
      final ServiceOwner owner,
      final ExecutionComponentKind kind
  ) {
    components.get(kind).entrySet().removeIf(entry -> entry.getValue().owner() == owner);
  }

  @Override
  public synchronized void close() {
    components.values().forEach(Map::clear);
  }

  private static Class<?> expectedType(final ExecutionComponentKind kind) {
    return switch (kind) {
      case REWARD -> Reward.class;
      case COST -> Cost.class;
      case REQUIREMENT -> Requirement.class;
    };
  }

  private static String validateKey(final String key) {
    String checked = Objects.requireNonNull(key, "key");
    if (!checked.matches("[a-z][a-z0-9-]*")) {
      throw new IllegalArgumentException("Extension key must be lowercase kebab-case: " + checked);
    }
    return checked;
  }

  private record RegisteredComponent(ServiceOwner owner, Object component) {}
}
