package dev.vexsoft.core.api.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import lombok.Getter;
import org.junit.jupiter.api.Test;

class VexPlayerTest {

  private static final DataContainerKey<SkillData> SKILLS = DataContainerKey.of(
      "skills",
      SkillData.class,
      SkillData::new
  );

  @Test
  void updateReturnsAValueAndMarksTheContainerDirty() {
    VexPlayer player = new VexPlayer(UUID.randomUUID(), "VexPlayer");
    player.install(SKILLS, new SkillData());

    int level = player.update(SKILLS, skills -> {
      skills.experience += 250;
      return skills.experience / 100;
    });

    assertEquals(2, level);
    assertEquals(250, player.read(SKILLS, SkillData::getExperience));
    assertTrue(player.getDirtyKeys().contains(SKILLS));
  }

  @Test
  void aSaveCannotClearAChangeMadeAfterItsSnapshot() {
    VexPlayer player = new VexPlayer(UUID.randomUUID(), "VexPlayer");
    player.install(SKILLS, new SkillData());
    player.update(SKILLS, skills -> {
      skills.experience = 100;
    });
    VexPlayer.ContainerSnapshot<Integer> snapshot = player.snapshot(
        SKILLS,
        value -> ((SkillData) value).getExperience()
    );

    player.update(SKILLS, skills -> {
      skills.experience = 200;
    });
    player.markClean(SKILLS, snapshot.getRevision());

    assertTrue(player.getDirtyKeys().contains(SKILLS));
    VexPlayer.ContainerSnapshot<Integer> current = player.snapshot(
        SKILLS,
        value -> ((SkillData) value).getExperience()
    );
    player.markClean(SKILLS, current.getRevision());
    assertFalse(player.getDirtyKeys().contains(SKILLS));
  }

  @Getter
  private static final class SkillData {
    private int experience;
  }
}
