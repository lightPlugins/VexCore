package dev.vexsoft.core.api.service.currency;

import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.currency.Currency;
import dev.vexsoft.core.currency.CurrencyDefinition;
import dev.vexsoft.core.currency.CurrencyKey;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Owner-scoped registry for dynamically configured virtual currencies. */
public interface CurrencyRegistry extends VexService {

  /** Registers a new definition or updates an active currency owned by this scope. */
  Currency register(CurrencyDefinition definition);

  /** Atomically reconciles every currency owned by this registry scope. */
  List<Currency> synchronize(Collection<CurrencyDefinition> definitions);

  /** Finds an active currency from any owner by its stable key. */
  Optional<Currency> find(CurrencyKey key);

  /** Returns an active currency or fails when it is unavailable. */
  Currency require(CurrencyKey key);

  /** Removes an owned currency without deleting persisted balances. */
  boolean unregister(CurrencyKey key);

  /** Returns a snapshot of every currently active currency. */
  Collection<Currency> getRegisteredCurrencies();
}
