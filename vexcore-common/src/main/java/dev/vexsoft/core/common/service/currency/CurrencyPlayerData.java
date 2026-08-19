package dev.vexsoft.core.common.service.currency;

import dev.vexsoft.core.api.player.DataContainerKey;
import dev.vexsoft.core.api.player.DataContainerRegistry;
import dev.vexsoft.core.api.player.PlayerDataDefinition;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import java.util.Objects;

/** Declares the persistent balance map backing every registered virtual currency. */
@Dependencies
public final class CurrencyPlayerData implements PlayerDataDefinition {

  static final DataContainerKey<CurrencyData> CURRENCIES = DataContainerKey.of(
      "currencies",
      CurrencyData.class,
      CurrencyData::new
  );

  /** Validates service-managed construction. */
  public CurrencyPlayerData(final VexServiceRegistry services) {
    Objects.requireNonNull(services, "services");
  }

  @Override
  public void register(final DataContainerRegistry registry) {
    registry.register(CURRENCIES);
  }
}
