package dev.vexsoft.core.common.reward.currency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.currency.CurrencyLocalizationService;
import dev.vexsoft.core.api.service.currency.CurrencyRegistry;
import dev.vexsoft.core.api.service.expression.ExpressionService;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.currency.Currency;
import dev.vexsoft.core.currency.CurrencyDefinition;
import dev.vexsoft.core.currency.CurrencyKey;
import dev.vexsoft.core.execution.PlayerExecutionContext;
import dev.vexsoft.core.expression.ConstantExpression;
import dev.vexsoft.core.number.WholeAmount;
import dev.vexsoft.core.reward.QuantifiedReward;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

/** Ensures inclusive currency ranges freeze once, retain exact large values and reject invalid input. */
final class CurrencyRewardRangeTest {

    @Test
    void inclusiveRangeAndReceiptsStayFrozenAfterPreparation() {
        var reward = reward();
        var context = new PlayerExecutionContext(new VexPlayer(UUID.randomUUID(), "Test"), Map.of());
        var compiled = reward.compile(Map.of("test:coins", Map.of("min", 100, "max", 350)));
        var prepared = (QuantifiedReward) compiled.prepare(context);
        var amount = prepared.quantities(context).get("test:coins");
        assertTrue(amount.compareTo(WholeAmount.of(100)) >= 0);
        assertTrue(amount.compareTo(WholeAmount.of(350)) <= 0);
        for (int index = 0; index < 100; index++) {
            assertEquals(amount, prepared.quantities(context).get("test:coins"));
            assertEquals(new BigDecimal(amount.toBigInteger()), prepared.notifications(context).getFirst().amount());
        }
        var huge = "123456789012345678901234567890";
        var exact = (QuantifiedReward) reward.compile(Map.of("test:coins", Map.of("min", huge, "max", huge)))
            .prepare(context);
        assertEquals(WholeAmount.parse(huge), exact.quantities(context).get("test:coins"));
        assertEquals(WholeAmount.of(4), ((QuantifiedReward) reward.compile(Map.of("test:coins", "4"))
            .prepare(context)).quantities(context).get("test:coins"));
    }

    @Test
    void invalidRangesFailAtCompilation() {
        var reward = reward();
        for (Object range : new Object[] {Map.of("min", 0, "max", 3), Map.of("min", 5, "max", 3),
            Map.of("min", 1.5, "max", 3), Map.of("min", 1), Map.of("min", 1, "max", 3, "extra", 4)}) {
            assertThrows(IllegalArgumentException.class, () -> reward.compile(Map.of("test:coins", range)));
        }
    }

    private static CurrencyReward reward() {
        var key = CurrencyKey.of("test", "coins");
        var currency = new Currency() {
            @Override
            public CurrencyKey getKey() {
                return key;
            }

            @Override
            public CurrencyDefinition getDefinition() {
                return CurrencyDefinition.builder(key).build();
            }

            @Override
            public boolean isRegistered() {
                return true;
            }
        };
        var dependencies = Map.of(
            CurrencyRegistry.class, Proxy.newProxyInstance(CurrencyRegistry.class.getClassLoader(),
                new Class<?>[] {CurrencyRegistry.class}, (proxy, method, args) -> currency),
            ExpressionService.class, Proxy.newProxyInstance(ExpressionService.class.getClassLoader(),
                new Class<?>[] {ExpressionService.class}, (proxy, method, args) -> new ConstantExpression(new BigDecimal(args[0].toString()))),
            CurrencyLocalizationService.class, Proxy.newProxyInstance(CurrencyLocalizationService.class.getClassLoader(),
                new Class<?>[] {CurrencyLocalizationService.class}, (proxy, method, args) -> Component.text("Coins")));
        var registry = (VexServiceRegistry) Proxy.newProxyInstance(VexServiceRegistry.class.getClassLoader(),
            new Class<?>[] {VexServiceRegistry.class}, (proxy, method, args) -> dependencies.get(args[0]));
        return new CurrencyReward(registry);
    }
}
