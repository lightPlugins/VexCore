package dev.vexsoft.core.paper.reactor.registry;

import dev.vexsoft.core.paper.reactor.provider.ItemTypeProvider;

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
import org.bukkit.inventory.ItemStack;

@Dependencies
public final class VexItemTypeCoordinatorService implements ItemTypeCoordinatorService {
  private final Map<String, RegisteredProvider> providers = new LinkedHashMap<>();

  public VexItemTypeCoordinatorService(final VexServiceRegistry services) {
    Objects.requireNonNull(services, "services");
  }

  @Override
  public synchronized void register(final ServiceOwner owner, final VexServiceRegistry services,
      final Class<? extends ItemTypeProvider> providerType) {
    ItemTypeProvider provider = VexClassFactory.create(providerType, services, "Item type provider");
    String namespace = provider.getNamespace().trim().toLowerCase(Locale.ROOT);
    if (!namespace.matches("[a-z][a-z0-9.-]*")) {
      throw new IllegalArgumentException("Invalid item type namespace: " + namespace);
    }
    RegisteredProvider existing = providers.putIfAbsent(namespace,
        new RegisteredProvider(owner, provider));
    if (existing != null) {
      throw new IllegalStateException("Item type namespace '" + namespace
          + "' is already registered by " + existing.getOwner().getServiceOwnerName());
    }
  }

  @Override
  public synchronized Predicate<ItemStack> compile(final Key key) {
    Key checkedKey = Objects.requireNonNull(key, "key");
    RegisteredProvider provider = providers.get(checkedKey.namespace());
    if (provider == null) {
      throw new IllegalArgumentException("No item type provider is registered for namespace '"
          + checkedKey.namespace() + '\'');
    }
    return Objects.requireNonNull(provider.getProvider().compile(checkedKey),
        "compiled item predicate");
  }

  @Override
  public synchronized void unregisterOwner(final ServiceOwner owner) {
    providers.values().removeIf(provider -> provider.getOwner() == owner);
  }

  @Value
  private static class RegisteredProvider {
    ServiceOwner owner;
    ItemTypeProvider provider;
  }
}
