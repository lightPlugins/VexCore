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

    /** Registers or updates a currency definition for the supplied owner. */
    Currency register(ServiceOwner owner, CurrencyDefinition definition);

    /** Atomically replaces the owner's active currency definitions with the supplied collection. */
    List<Currency> synchronize(ServiceOwner owner, Collection<CurrencyDefinition> definitions);

    /** Finds an active currency by its stable key. */
    Optional<Currency> find(CurrencyKey key);

    /** Removes an owned currency definition without deleting persisted balances. */
    boolean unregister(ServiceOwner owner, CurrencyKey key);

    /** Removes every active currency definition belonging to the owner. */
    void unregisterOwner(ServiceOwner owner);

    /** Returns a snapshot of all active currencies. */
    Collection<Currency> getRegisteredCurrencies();
}
