package dev.vexsoft.core.common.service.currency;

import dev.vexsoft.core.currency.Currency;
import dev.vexsoft.core.currency.CurrencyDefinition;
import dev.vexsoft.core.currency.CurrencyKey;
import java.util.Objects;

final class RegisteredCurrency implements Currency {

  private final String owner;
  private volatile CurrencyDefinition definition;
  private volatile boolean registered = true;

  RegisteredCurrency(final String owner, final CurrencyDefinition definition) {
    this.owner = Objects.requireNonNull(owner, "owner");
    this.definition = Objects.requireNonNull(definition, "definition");
  }

  @Override
  public CurrencyKey getKey() {
    return definition.getKey();
  }

  @Override
  public CurrencyDefinition getDefinition() {
    return definition;
  }

  @Override
  public boolean isRegistered() {
    return registered;
  }

  String getOwner() {
    return owner;
  }

  void update(final CurrencyDefinition updatedDefinition) {
    definition = Objects.requireNonNull(updatedDefinition, "updatedDefinition");
  }

  void unregister() {
    registered = false;
  }
}
