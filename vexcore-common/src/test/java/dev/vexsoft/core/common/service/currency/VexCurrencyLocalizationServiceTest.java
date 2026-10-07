package dev.vexsoft.core.common.service.currency;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.vexsoft.core.api.localization.LanguageKey;
import dev.vexsoft.core.api.localization.LocalizedMessage;
import dev.vexsoft.core.api.service.placeholder.PlaceholderService;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.common.service.localization.LocalizationRegistryService;
import dev.vexsoft.core.currency.CurrencyDefinition;
import dev.vexsoft.core.currency.CurrencyKey;
import dev.vexsoft.core.number.WholeAmount;
import dev.vexsoft.core.number.WholeAmountFormatter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;

final class VexCurrencyLocalizationServiceTest {

    @Test
    void explicitLanguageRoutesToCurrencyOwnerAndKeepsExactAmountAndStyling() {
        Map<Class<?>, Object> dependencies = new HashMap<>();
        VexServiceRegistry services = proxy(VexServiceRegistry.class, (object, method, args) ->
            switch (method.getName()) {
                case "require" -> dependencies.get(args[0]);
                case "getOwner" -> (ServiceOwner) () -> "VexIsles";
                default -> throw new UnsupportedOperationException(method.getName());
            });
        var coordinator = new VexCurrencyRegistryCoordinatorService(services);
        dependencies.put(CurrencyRegistryCoordinatorService.class, coordinator);
        var key = CurrencyKey.of("vexisles", "arkie_tumblebud_essence");
        coordinator.register(() -> "VexIsles", CurrencyDefinition.builder(key).build());
        Component localized = Component.text("15aa Tumblebud-Essenz", NamedTextColor.LIGHT_PURPLE);
        dependencies.put(LocalizationRegistryService.class,
            proxy(LocalizationRegistryService.class, (object, method, args) -> {
                assertEquals("vexisles", args[0]);
                assertEquals(LanguageKey.DE_DE, args[1]);
                if (args[2].equals("currencies.arkie_tumblebud_essence.format")) {
                    assertEquals(Map.of("amount", "15000000000000000", "formatted_amount", "15aa"), args[3]);
                } else {
                    assertEquals("currencies.arkie_tumblebud_essence.name", args[2]);
                    assertEquals(Map.of(), args[3]);
                }
                return LocalizedMessage.single(localized);
            }));
        dependencies.put(PlaceholderService.class,
            proxy(PlaceholderService.class, (object, method, args) -> {
                throw new AssertionError("Explicit-language presentation must not require a player");
            }));
        var service = new VexCurrencyLocalizationService(services);
        assertEquals(localized, service.format(LanguageKey.DE_DE, key, WholeAmountFormatter.parse("15aa")));
        assertEquals(localized, service.getName(LanguageKey.DE_DE, key));
    }

    @Test
    void formatsCompactGamingAmountsWithoutChangingSmallValues() {
        assertEquals("999", WholeAmountFormatter.format(WholeAmount.of(999L)));
        assertEquals("1k", WholeAmountFormatter.format(WholeAmount.of(1_000L)));
        assertEquals("10k", WholeAmountFormatter.format(WholeAmount.of(10_000L)));
        assertEquals("12.5k", WholeAmountFormatter.format(WholeAmount.of(12_500L)));
        assertEquals("1m", WholeAmountFormatter.format(WholeAmount.of(1_000_000L)));
        assertEquals("1.5b", WholeAmountFormatter.format(WholeAmount.of(1_500_000_000L)));
        assertEquals("9.22ab", WholeAmountFormatter.format(WholeAmount.of(Long.MAX_VALUE)));
    }

    private static <T> T proxy(final Class<T> type, final InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }
}
