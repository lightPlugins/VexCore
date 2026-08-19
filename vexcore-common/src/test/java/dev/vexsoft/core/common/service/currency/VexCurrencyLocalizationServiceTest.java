package dev.vexsoft.core.common.service.currency;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class VexCurrencyLocalizationServiceTest {

  @Test
  void formatsCompactGamingAmountsWithoutChangingSmallValues() {
    assertEquals("999", VexCurrencyLocalizationService.compact(999L));
    assertEquals("1k", VexCurrencyLocalizationService.compact(1_000L));
    assertEquals("10k", VexCurrencyLocalizationService.compact(10_000L));
    assertEquals("12.5k", VexCurrencyLocalizationService.compact(12_500L));
    assertEquals("1m", VexCurrencyLocalizationService.compact(1_000_000L));
    assertEquals("1.5b", VexCurrencyLocalizationService.compact(1_500_000_000L));
    assertEquals("9.2qi", VexCurrencyLocalizationService.compact(Long.MAX_VALUE));
  }
}
