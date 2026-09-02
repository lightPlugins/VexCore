package dev.vexsoft.core.number;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

final class WholeAmountTest {

  @Test
  void formatsIncrementalSuffixesBeyondLongRange() {
    assertEquals("999", WholeAmountFormatter.format(WholeAmount.of(999L)));
    assertEquals("1k", WholeAmountFormatter.format(WholeAmount.of(1_000L)));
    assertEquals("1.25k", WholeAmountFormatter.format(WholeAmount.of(1_250L)));
    assertEquals("1t", WholeAmountFormatter.format(WholeAmount.parse("1000000000000")));
    assertEquals("1aa", WholeAmountFormatter.format(WholeAmount.parse("1000000000000000")));
    assertEquals("35ab", WholeAmountFormatter.format(
        WholeAmount.parse("35000000000000000000")
    ));
  }

  @Test
  void parsesAlphabeticSuffixesWithoutPrecisionLoss() {
    assertEquals(
        new BigInteger("35000000000000000000"),
        WholeAmountFormatter.parse("35ab").toBigInteger()
    );
    assertEquals(
        new BigInteger("1250000000000000000000"),
        WholeAmountFormatter.parse("1.25ac").toBigInteger()
    );
    assertEquals("1aaa", WholeAmountFormatter.format(WholeAmountFormatter.parse("1aaa")));
  }

  @Test
  void rejectsNegativeAndFractionalWholeAmounts() {
    assertThrows(IllegalArgumentException.class, () -> WholeAmount.parse("-1"));
    assertThrows(IllegalArgumentException.class, () -> WholeAmountFormatter.parse("1.1"));
    assertThrows(IllegalArgumentException.class, () -> WholeAmountFormatter.parse("0.0001k"));
  }
}
