package dev.vexsoft.core.common.service.currency;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.currency.Currency;
import dev.vexsoft.core.currency.CurrencyDefinition;
import dev.vexsoft.core.currency.CurrencyKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Default global coordinator for dynamic virtual-currency registrations. */
@Dependencies
public final class VexCurrencyRegistryCoordinatorService
    implements CurrencyRegistryCoordinatorService {

  private final Map<CurrencyKey, RegisteredCurrency> currencies = new LinkedHashMap<>();

  /** Creates an empty service-managed currency registry. */
  public VexCurrencyRegistryCoordinatorService(final VexServiceRegistry services) {
    Objects.requireNonNull(services, "services");
  }

  @Override
  public synchronized Currency register(
      final ServiceOwner owner,
      final CurrencyDefinition definition
  ) {
    String ownerName = ownerName(owner);
    CurrencyDefinition checked = requireOwned(ownerName, definition);
    RegisteredCurrency existing = currencies.get(checked.getKey());
    if (existing != null) {
      if (!existing.getOwner().equals(ownerName)) {
        throw new IllegalStateException("Currency is owned by another plugin: " + checked.getKey());
      }
      existing.update(checked);
      return existing;
    }
    RegisteredCurrency registered = new RegisteredCurrency(ownerName, checked);
    currencies.put(checked.getKey(), registered);
    return registered;
  }

  @Override
  public synchronized List<Currency> synchronize(
      final ServiceOwner owner,
      final Collection<CurrencyDefinition> definitions
  ) {
    String ownerName = ownerName(owner);
    Map<CurrencyKey, CurrencyDefinition> desired = new LinkedHashMap<>();
    for (CurrencyDefinition definition : Objects.requireNonNull(definitions, "definitions")) {
      CurrencyDefinition checked = requireOwned(ownerName, definition);
      if (desired.putIfAbsent(checked.getKey(), checked) != null) {
        throw new IllegalArgumentException("Duplicate currency definition: " + checked.getKey());
      }
    }
    validateOwnership(ownerName, desired.keySet());
    Set<CurrencyKey> removed = new HashSet<>();
    currencies.forEach((key, currency) -> {
      if (currency.getOwner().equals(ownerName) && !desired.containsKey(key)) {
        removed.add(key);
      }
    });
    removed.forEach(key -> unregister(owner, key));
    List<Currency> result = new ArrayList<>(desired.size());
    desired.values().forEach(definition -> result.add(register(owner, definition)));
    return List.copyOf(result);
  }

  @Override
  public synchronized Optional<Currency> find(final CurrencyKey key) {
    return Optional.ofNullable(currencies.get(Objects.requireNonNull(key, "key")));
  }

  @Override
  public synchronized boolean unregister(final ServiceOwner owner, final CurrencyKey key) {
    String ownerName = ownerName(owner);
    RegisteredCurrency existing = currencies.get(Objects.requireNonNull(key, "key"));
    if (existing == null) {
      return false;
    }
    if (!existing.getOwner().equals(ownerName)) {
      throw new IllegalArgumentException("Currency is owned by another plugin: " + key);
    }
    currencies.remove(key);
    existing.unregister();
    return true;
  }

  @Override
  public synchronized void unregisterOwner(final ServiceOwner owner) {
    String ownerName = ownerName(owner);
    List<CurrencyKey> owned = currencies.entrySet().stream()
        .filter(entry -> entry.getValue().getOwner().equals(ownerName))
        .map(Map.Entry::getKey)
        .toList();
    owned.forEach(key -> unregister(owner, key));
  }

  @Override
  public synchronized Collection<Currency> getRegisteredCurrencies() {
    return currencies.values().stream().map(Currency.class::cast).toList();
  }

  private void validateOwnership(final String owner, final Collection<CurrencyKey> keys) {
    for (CurrencyKey key : keys) {
      RegisteredCurrency existing = currencies.get(key);
      if (existing != null && !existing.getOwner().equals(owner)) {
        throw new IllegalStateException("Currency is owned by another plugin: " + key);
      }
    }
  }

  private static CurrencyDefinition requireOwned(
      final String owner,
      final CurrencyDefinition definition
  ) {
    CurrencyDefinition checked = Objects.requireNonNull(definition, "definition");
    if (!checked.getKey().namespace().equals(owner)) {
      throw new IllegalArgumentException(
          "Currency namespace must match its owner '" + owner + "': " + checked.getKey()
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
      throw new IllegalArgumentException("Invalid currency owner name: " + normalized);
    }
    return normalized;
  }
}
