package dev.vexsoft.core.common.service.currency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.currency.Currency;
import dev.vexsoft.core.currency.CurrencyDefinition;
import dev.vexsoft.core.currency.CurrencyKey;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class VexCurrencyContainerTest {

  @Test
  void mutatesPersistentBalancesAndRejectsInvalidTransactions() {
    VexPlayer player = player();
    VexCurrencyContainer container = new VexCurrencyContainer(player);
    Currency dust = currency("dust", 3L, 10L);

    assertEquals(3L, container.getBalance(dust));
    assertEquals(7L, container.deposit(dust, 4L).balance());
    assertFalse(container.deposit(dust, 4L).successful());
    assertFalse(container.withdraw(dust, 8L).successful());
    assertEquals(5L, container.withdraw(dust, 2L).balance());
    assertEquals(9L, container.setBalance(dust, 9L).balance());
    assertTrue(player.getDirtyKeys().contains(CurrencyPlayerData.CURRENCIES));
  }

  @Test
  void multiCurrencyDepositIsAtomic() {
    VexPlayer player = player();
    VexCurrencyContainer container = new VexCurrencyContainer(player);
    Currency dust = currency("dust", 0L, 10L);
    Currency crystals = currency("crystals", 0L, 10L);
    container.deposit(dust, 8L);

    assertFalse(container.depositAll(Map.of(dust, 3L, crystals, 4L)).successful());
    assertEquals(8L, container.getBalance(dust));
    assertEquals(0L, container.getBalance(crystals));
    assertTrue(container.depositAll(Map.of(dust, 2L, crystals, 4L)).successful());
    assertEquals(10L, container.getBalance(dust));
    assertEquals(4L, container.getBalance(crystals));
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
            .defaultBalance(defaultBalance)
            .maximumBalance(maximumBalance)
            .build()
    );
  }
}
