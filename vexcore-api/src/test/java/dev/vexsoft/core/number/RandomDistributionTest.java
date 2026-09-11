package dev.vexsoft.core.number;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

final class RandomDistributionTest {

    @Test
    void exactChanceBoundariesNeverDependOnLuck() {
        Random random = new Random(12L);

        for (int attempt = 0; attempt < 10_000; attempt++) {
            assertFalse(RandomDistribution.chance(0.0D, random));
            assertTrue(RandomDistribution.chance(100.0D, random));
        }
    }

    @Test
    void increasingWeightBonusIsMonotoneAndBounded() {
        for (int step = 0; step <= 1_000; step++) {
            double uniformSample = step / 1_000.0D;
            double regular = RandomDistribution.biased(400.0D, 5_500.0D, 0.0D, uniformSample);
            double improved = RandomDistribution.biased(400.0D, 5_500.0D, 250.0D, uniformSample);

            assertTrue(improved >= regular);
            assertTrue(improved >= 400.0D && improved <= 5_500.0D);
        }

        assertEquals(2.0D, RandomDistribution.biased(2.0D, 2.0D, 50.0D, 0.8D));
    }

    @Test
    void zeroWeightEntriesCannotBeSelectedAndInvalidTablesFail() {
        for (int seed = 0; seed < 1_000; seed++) {
            assertEquals(3, RandomDistribution.weighted(List.of(0, 3, 0), Integer::doubleValue, new Random(seed)));
        }

        assertThrows(
            IllegalArgumentException.class,
            () -> RandomDistribution.weighted(List.of(0, 0), Integer::doubleValue, new Random())
        );
        assertThrows(IllegalArgumentException.class, () -> RandomDistribution.biased(5.0D, 1.0D, 0.0D, 0.5D));
        assertThrows(IllegalArgumentException.class, () -> RandomDistribution.biased(1.0D, 5.0D, Double.NaN, 0.5D));
    }
}
