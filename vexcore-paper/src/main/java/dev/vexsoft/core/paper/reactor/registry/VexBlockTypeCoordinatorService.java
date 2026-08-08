package dev.vexsoft.core.paper.reactor.registry;

import dev.vexsoft.core.paper.reactor.provider.BlockTypeProvider;

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
import org.bukkit.block.Block;

@Dependencies
public final class VexBlockTypeCoordinatorService implements BlockTypeCoordinatorService {

  private final Map<String, RegisteredProvider> providers = new LinkedHashMap<>();

  public VexBlockTypeCoordinatorService(final VexServiceRegistry services) {
    Objects.requireNonNull(services, "services");
  }

  @Override
  public synchronized void register(
      final ServiceOwner owner,
      final VexServiceRegistry services,
      final Class<? extends BlockTypeProvider> providerType
  ) {
    BlockTypeProvider provider = VexClassFactory.create(providerType, services, "Block type provider");
    String namespace = provider.getNamespace().trim().toLowerCase(Locale.ROOT);
    if (!namespace.matches("[a-z][a-z0-9.-]*")) {
      throw new IllegalArgumentException("Invalid block type namespace: " + namespace);
    }
    RegisteredProvider registered = new RegisteredProvider(owner, provider);
    RegisteredProvider existing = providers.putIfAbsent(namespace, registered);
    if (existing != null) {
      throw new IllegalStateException(
          "Block type namespace '" + namespace + "' is already registered by "
              + existing.getOwner().getServiceOwnerName()
      );
    }
  }

  @Override
  public synchronized Predicate<Block> compile(final Key key) {
    Key checkedKey = Objects.requireNonNull(key, "key");
    RegisteredProvider provider = providers.get(checkedKey.namespace());
    if (provider == null) {
      throw new IllegalArgumentException(
          "No block type provider is registered for namespace '" + checkedKey.namespace() + '\''
      );
    }
    return Objects.requireNonNull(
        provider.getProvider().compile(checkedKey),
        "compiled block predicate"
    );
  }

  @Override
  public synchronized void unregisterOwner(final ServiceOwner owner) {
    providers.values().removeIf(provider -> provider.getOwner() == owner);
  }

  @Value
  private static class RegisteredProvider {
    ServiceOwner owner;
    BlockTypeProvider provider;
  }
}
