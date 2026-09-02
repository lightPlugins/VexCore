package dev.vexsoft.core.common.service.currency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.currency.Currency;
import dev.vexsoft.core.currency.CurrencyDefinition;
import dev.vexsoft.core.currency.CurrencyKey;
import dev.vexsoft.core.number.WholeAmount;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class VexCurrencyContainerTest {

  @Test
  void mutatesPersistentBalancesAndRejectsInvalidTransactions() {
    VexPlayer player = player();
    VexCurrencyContainer container = new VexCurrencyContainer(player);
    Currency dust = currency("dust", 3L, 10L);

    assertEquals(WholeAmount.of(3L), container.getBalance(dust));
    assertEquals(WholeAmount.of(7L), container.deposit(dust, WholeAmount.of(4L)).balance());
    assertFalse(container.deposit(dust, WholeAmount.of(4L)).successful());
    assertFalse(container.withdraw(dust, WholeAmount.of(8L)).successful());
    assertEquals(WholeAmount.of(5L), container.withdraw(dust, WholeAmount.of(2L)).balance());
    assertEquals(WholeAmount.of(9L), container.setBalance(dust, WholeAmount.of(9L)).balance());
    assertTrue(player.getDirtyKeys().contains(CurrencyPlayerData.CURRENCIES));
  }

  @Test
  void multiCurrencyDepositIsAtomic() {
    VexPlayer player = player();
    VexCurrencyContainer container = new VexCurrencyContainer(player);
    Currency dust = currency("dust", 0L, 10L);
    Currency crystals = currency("crystals", 0L, 10L);
    container.deposit(dust, WholeAmount.of(8L));

    assertFalse(container.depositAll(Map.of(
        dust, WholeAmount.of(3L), crystals, WholeAmount.of(4L)
    )).successful());
    assertEquals(WholeAmount.of(8L), container.getBalance(dust));
    assertEquals(WholeAmount.ZERO, container.getBalance(crystals));
    assertTrue(container.depositAll(Map.of(
        dust, WholeAmount.of(2L), crystals, WholeAmount.of(4L)
    )).successful());
    assertEquals(WholeAmount.of(10L), container.getBalance(dust));
    assertEquals(WholeAmount.of(4L), container.getBalance(crystals));
  }

  private static VexPlayer player() {
    VexPlayer player = new VexPlayer(UUID.randomUUID(), "CurrencyTest");
    player.install(CurrencyPlayerData.CURRENCIES, new CurrencyData());
    return player;
  }

  private static Currency currency(
      final String id,
      final long defaultBalance,
      final long maximumBalance
  ) {
    return new RegisteredCurrency(
        "test",
        CurrencyDefinition.builder(CurrencyKey.of("test", id))
            .defaultBalance(WholeAmount.of(defaultBalance))
            .maximumBalance(WholeAmount.of(maximumBalance))
            .build()
    );
  }
}
