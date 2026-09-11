package dev.vexsoft.core.common.service.currency;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.vexsoft.core.number.WholeAmount;
import dev.vexsoft.core.number.WholeAmountFormatter;
import org.junit.jupiter.api.Test;

final class VexCurrencyLocalizationServiceTest {

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
}
