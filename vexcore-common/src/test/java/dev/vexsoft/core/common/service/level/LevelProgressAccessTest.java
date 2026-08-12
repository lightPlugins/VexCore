package dev.vexsoft.core.common.service.level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.api.player.DataContainerKey;
import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.level.LevelProgress;
import dev.vexsoft.core.level.LevelProgressAccess;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class LevelProgressAccessTest {

  @Test
  void readsAndWritesOnlyThroughTheVexPlayerContainerApi() {
    DataContainerKey<TestData> key = DataContainerKey.of(
        "level_test", TestData.class, TestData::new
    );
    VexPlayer player = new VexPlayer(UUID.randomUUID(), "Tester");
    player.install(key, new TestData(500.0D, 2));
    LevelProgressAccess<TestData> access = new LevelProgressAccess<>(
        key,
        data -> new Snapshot(data.experience, data.claimedLevel),
        (data, level) -> data.claimedLevel = level
    );

    assertEquals(500.0D, access.read(player).getExperience());
    access.updateClaimedLevel(player, 3);

    int claimedLevel = player.read(key, data -> data.claimedLevel);
    assertEquals(3, claimedLevel);
    assertTrue(player.getDirtyKeys().contains(key));
  }

  private static final class TestData {
    private double experience;
    private int claimedLevel;

    private TestData() {}

    private TestData(final double experience, final int claimedLevel) {
      this.experience = experience;
      this.claimedLevel = claimedLevel;
    }
  }

  private record Snapshot(double experience, int claimedLevel) implements LevelProgress {
    @Override
    public double getExperience() {
      return experience;
    }

    @Override
    public int getClaimedLevel() {
      return claimedLevel;
    }
  }
}
