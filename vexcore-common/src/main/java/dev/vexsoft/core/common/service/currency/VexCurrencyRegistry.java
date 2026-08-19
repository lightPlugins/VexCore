package dev.vexsoft.core.common.service.currency;

import dev.vexsoft.core.api.service.currency.CurrencyRegistry;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.currency.Currency;
import dev.vexsoft.core.currency.CurrencyDefinition;
import dev.vexsoft.core.currency.CurrencyKey;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Default owner-scoped virtual-currency registry facade. */
@Dependencies(CurrencyRegistryCoordinatorService.class)
public final class VexCurrencyRegistry implements CurrencyRegistry, AutoCloseable {

  private final ServiceOwner owner;
  private final CurrencyRegistryCoordinatorService coordinator;

  /** Captures the calling service owner and shared coordinator. */
  public VexCurrencyRegistry(final VexServiceRegistry services) {
    VexServiceRegistry checked = Objects.requireNonNull(services, "services");
    owner = checked.getOwner();
    coordinator = checked.require(CurrencyRegistryCoordinatorService.class);
  }

  @Override
  public Currency register(final CurrencyDefinition definition) {
    return coordinator.register(owner, definition);
  }

  @Override
  public List<Currency> synchronize(final Collection<CurrencyDefinition> definitions) {
    return coordinator.synchronize(owner, definitions);
  }

  @Override
  public Optional<Currency> find(final CurrencyKey key) {
    return coordinator.find(key);
  }

  @Override
  public Currency require(final CurrencyKey key) {
    return find(key).orElseThrow(() -> new IllegalStateException(
        "Currency is not registered: " + key
    ));
  }

  @Override
  public boolean unregister(final CurrencyKey key) {
    return coordinator.unregister(owner, key);
  }

  @Override
  public Collection<Currency> getRegisteredCurrencies() {
    return coordinator.getRegisteredCurrencies();
  }

  @Override
  public void close() {
    coordinator.unregisterOwner(owner);
  }
}
