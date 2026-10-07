package dev.vexsoft.core.common.service.currency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.vexsoft.core.api.player.VexPlayer;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vexsoft.core.currency.Currency;
import dev.vexsoft.core.currency.CurrencyDefinition;
import dev.vexsoft.core.currency.CurrencyKey;
import dev.vexsoft.core.number.WholeAmount;
import dev.vexsoft.core.number.WholeAmountFormatter;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class VexCurrencyContainerTest {

    @Test
    void largeIndependentBalancesRoundTripAsExactStringsAndSurviveReregistration() throws Exception {
        VexPlayer original = player();
        var currency = currency("essence", 0, Long.MAX_VALUE);
        var unlimited = new RegisteredCurrency("test", CurrencyDefinition.builder(
            CurrencyKey.of("test", "unlimited")).build());
        var container = new VexCurrencyContainer(original);
        var huge = WholeAmountFormatter.parse("35ab");
        assertTrue(container.deposit(unlimited, huge).successful());
        assertTrue(container.deposit(currency, WholeAmountFormatter.parse("5m")).successful());
        var mapper = new ObjectMapper();
        String json = original.read(CurrencyPlayerData.CURRENCIES, data -> {
            try {
                return mapper.writeValueAsString(data);
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
        });
        assertTrue(json.contains("\"35000000000000000000\""));
        VexPlayer restored = new VexPlayer(UUID.randomUUID(), "Restored");
        restored.install(CurrencyPlayerData.CURRENCIES, mapper.readValue(json, CurrencyData.class));
        var restoredContainer = new VexCurrencyContainer(restored);
        unlimited.unregister();
        var registeredAgain = new RegisteredCurrency("test", unlimited.getDefinition());
        assertEquals(huge, restoredContainer.getBalance(registeredAgain));
        assertEquals(WholeAmount.of(5_000_000), restoredContainer.getBalance(currency));
        assertEquals(WholeAmount.ZERO, new VexCurrencyContainer(player()).getBalance(registeredAgain));
    }

    @Test
    void receiptSurvivesContainerRecreationAndRejectsMismatchedReplay() {
        VexPlayer player = player();
        Currency coins = currency("coins", 0, 1000);
        VexCurrencyContainer first = new VexCurrencyContainer(player);
        assertTrue(first.depositOnce(coins, WholeAmount.of(250), "sale-1").successful());
        VexCurrencyContainer restored = new VexCurrencyContainer(player);
        assertTrue(restored.depositOnce(coins, WholeAmount.of(250), "sale-1").successful());
        assertEquals(WholeAmount.of(250), restored.getBalance(coins));
        assertThrows(IllegalArgumentException.class,
            () -> restored.depositOnce(coins, WholeAmount.of(251), "sale-1"));
        assertTrue(restored.depositOnce(coins, WholeAmount.of(250), "sale-2").successful());
        assertEquals(WholeAmount.of(500), restored.getBalance(coins));
    }

    @Test
    void rejectedDepositDoesNotConsumeReceipt() {
        VexCurrencyContainer container = new VexCurrencyContainer(player());
        Currency coins = currency("coins", 90, 100);
        assertFalse(container.depositOnce(coins, WholeAmount.of(20), "retry").successful());
        container.withdraw(coins, WholeAmount.of(50));
        assertTrue(container.depositOnce(coins, WholeAmount.of(20), "retry").successful());
        assertEquals(WholeAmount.of(60), container.getBalance(coins));
    }

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

        assertFalse(container.depositAll(Map.of(dust, WholeAmount.of(3L), crystals, WholeAmount.of(4L))).successful());
        assertEquals(WholeAmount.of(8L), container.getBalance(dust));
        assertEquals(WholeAmount.ZERO, container.getBalance(crystals));
        assertTrue(container.depositAll(Map.of(dust, WholeAmount.of(2L), crystals, WholeAmount.of(4L))).successful());
        assertEquals(WholeAmount.of(10L), container.getBalance(dust));
        assertEquals(WholeAmount.of(4L), container.getBalance(crystals));
    }

    private static VexPlayer player() {
        VexPlayer player = new VexPlayer(UUID.randomUUID(), "CurrencyTest");

        player.install(CurrencyPlayerData.CURRENCIES, new CurrencyData());

        return player;
    }

    private static Currency currency(final String id, final long defaultBalance, final long maximumBalance) {
        return new RegisteredCurrency(
            "test",
            CurrencyDefinition.builder(CurrencyKey.of("test", id))
                .defaultBalance(WholeAmount.of(defaultBalance))
                .maximumBalance(WholeAmount.of(maximumBalance))
                .build()
        );
    }
}
