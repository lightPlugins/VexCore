package dev.vexsoft.core.stats.contribution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.vexsoft.core.reward.RewardContribution;
import dev.vexsoft.core.stats.StatKey;
import dev.vexsoft.core.stats.StatModifier;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StatRewardContributionTest {

  private static final StatKey DEFENSE = StatKey.of("vexskills", "defense");

  @Test
  void mergesFlatSnapshotsWithoutMutatingInputs() {
    StatRewardContribution first = new StatRewardContribution(Map.of(
        DEFENSE,
        StatModifier.flat(4D)
    ));
    StatRewardContribution second = new StatRewardContribution(Map.of(
        DEFENSE,
        StatModifier.flat(7D)
    ));

    RewardContribution merged = first.merge(second);

    StatRewardContribution stats = (StatRewardContribution) merged;
    assertEquals(11D, stats.modifiers().get(DEFENSE).amount());
    assertEquals(4D, first.modifiers().get(DEFENSE).amount());
  }

  @Test
  void rejectsMixedOperationsForOneStat() {
    StatRewardContribution flat = new StatRewardContribution(Map.of(
        DEFENSE,
        StatModifier.flat(4D)
    ));
    StatRewardContribution multiplier = new StatRewardContribution(Map.of(
        DEFENSE,
        StatModifier.additiveMultiplier(0.2D)
    ));

    assertThrows(IllegalArgumentException.class, () -> flat.merge(multiplier));
  }
}
