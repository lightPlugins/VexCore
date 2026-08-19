package dev.vexsoft.core.common.service.currency;

import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.currency.Currency;
import dev.vexsoft.core.currency.CurrencyDefinition;
import dev.vexsoft.core.currency.CurrencyKey;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Coordinates all active currency registrations behind owner-scoped facades. */
public interface CurrencyRegistryCoordinatorService extends VexService {

  Currency register(ServiceOwner owner, CurrencyDefinition definition);

  List<Currency> synchronize(ServiceOwner owner, Collection<CurrencyDefinition> definitions);

  Optional<Currency> find(CurrencyKey key);

  boolean unregister(ServiceOwner owner, CurrencyKey key);

  void unregisterOwner(ServiceOwner owner);

  Collection<Currency> getRegisteredCurrencies();
}
