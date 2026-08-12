package dev.vexsoft.core.common.service.level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.level.LevelChange;
import dev.vexsoft.core.level.LevelSnapshot;
import org.junit.jupiter.api.Test;

final class VexCompiledLevelCurveTest {

  @Test
  void calculatesBoundariesAndMaximumProgress() {
    VexCompiledLevelCurve curve = new VexCompiledLevelCurve(
        0, 3, new double[] {0.0D, 100.0D, 250.0D, 450.0D}
    );

    LevelSnapshot second = curve.calculate(175.0D);
    assertEquals(1, second.level());
    assertEquals(75.0D, second.experienceInLevel());
    assertEquals(0.5D, second.progress());

    LevelSnapshot maximum = curve.calculate(900.0D);
    assertEquals(3, maximum.level());
    assertTrue(maximum.maximumLevel());
    assertEquals(1.0D, maximum.progress());
  }

  @Test
  void comparesAllCrossedLevelsInOrder() {
    VexCompiledLevelCurve curve = new VexCompiledLevelCurve(
        0, 3, new double[] {0.0D, 100.0D, 250.0D, 450.0D}
    );

    LevelChange gained = curve.compare(20.0D, 500.0D);
    assertEquals(java.util.List.of(1, 2, 3), gained.gainedLevels());
    assertEquals(java.util.List.of(), gained.lostLevels());

    LevelChange lost = curve.compare(500.0D, 150.0D);
    assertEquals(java.util.List.of(3, 2), lost.lostLevels());
  }

  @Test
  void rejectsInvalidExperienceAndUnsupportedThresholds() {
    VexCompiledLevelCurve curve = new VexCompiledLevelCurve(
        0, 1, new double[] {0.0D, 100.0D}
    );
    assertThrows(IllegalArgumentException.class, () -> curve.calculate(-1.0D));
    assertThrows(IllegalArgumentException.class, () -> curve.getRequiredExperience(2));
  }
}
