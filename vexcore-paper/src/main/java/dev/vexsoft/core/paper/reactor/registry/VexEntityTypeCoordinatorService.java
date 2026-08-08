package dev.vexsoft.core.paper.reactor.registry;

import dev.vexsoft.core.paper.reactor.provider.EntityTypeProvider;

import dev.vexsoft.core.api.service.Dependencies;
import dev.vexsoft.core.api.service.ServiceOwner;
import dev.vexsoft.core.api.service.VexClassFactory;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import lombok.Value;
import net.kyori.adventure.key.Key;
import org.bukkit.entity.Entity;

@Dependencies
public final class VexEntityTypeCoordinatorService implements EntityTypeCoordinatorService {
  private final Map<String, RegisteredProvider> providers = new LinkedHashMap<>();

  public VexEntityTypeCoordinatorService(final VexServiceRegistry services) {
    Objects.requireNonNull(services, "services");
  }

  @Override
  public synchronized void register(final ServiceOwner owner, final VexServiceRegistry services,
      final Class<? extends EntityTypeProvider> providerType) {
    EntityTypeProvider provider = VexClassFactory.create(providerType, services,
        "Entity type provider");
    String namespace = provider.getNamespace().trim().toLowerCase(Locale.ROOT);
    if (!namespace.matches("[a-z][a-z0-9.-]*")) {
      throw new IllegalArgumentException("Invalid entity type namespace: " + namespace);
    }
    RegisteredProvider existing = providers.putIfAbsent(namespace,
        new RegisteredProvider(owner, provider));
    if (existing != null) {
      throw new IllegalStateException("Entity type namespace '" + namespace
          + "' is already registered by " + existing.getOwner().getServiceOwnerName());
    }
  }

  @Override
  public synchronized Predicate<Entity> compile(final Key key) {
    Key checkedKey = Objects.requireNonNull(key, "key");
    RegisteredProvider provider = providers.get(checkedKey.namespace());
    if (provider == null) {
      throw new IllegalArgumentException("No entity type provider is registered for namespace '"
          + checkedKey.namespace() + '\'');
    }
    return Objects.requireNonNull(provider.getProvider().compile(checkedKey),
        "compiled entity predicate");
  }

  @Override
  public synchronized void unregisterOwner(final ServiceOwner owner) {
    providers.values().removeIf(provider -> provider.getOwner() == owner);
  }

  @Value
  private static class RegisteredProvider {
    ServiceOwner owner;
    EntityTypeProvider provider;
  }
}
