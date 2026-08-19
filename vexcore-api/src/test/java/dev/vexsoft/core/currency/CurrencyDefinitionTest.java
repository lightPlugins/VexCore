package dev.vexsoft.core.currency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class CurrencyDefinitionTest {

  @Test
  void preservesKebabCaseValuesAndNormalizesNamespaces() {
    CurrencyKey key = CurrencyKey.parse("Arcane-Monolith:Monolith-Dust");

    assertEquals("arcane_monolith", key.namespace());
    assertEquals("monolith-dust", key.value());
    assertEquals("arcane_monolith:monolith-dust", key.toString());
  }

  @Test
  void validatesBalancesAndBuildsLocalizationKeys() {
    CurrencyKey key = CurrencyKey.of("arcane", "dust");
    CurrencyDefinition definition = CurrencyDefinition.builder(key)
        .defaultBalance(5L)
        .maximumBalance(100L)
        .build();

    assertEquals(5L, definition.getDefaultBalance());
    assertEquals(100L, definition.getMaximumBalance());
    assertEquals("currencies.dust.name", definition.getNameKey());
    assertEquals("currencies.dust.format", definition.getFormatKey());
    assertThrows(
        IllegalArgumentException.class,
        () -> CurrencyDefinition.builder(key).defaultBalance(2L).maximumBalance(1L).build()
    );
  }
}
