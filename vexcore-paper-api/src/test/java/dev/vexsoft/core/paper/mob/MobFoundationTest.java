package dev.vexsoft.core.paper.mob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.paper.mob.goal.LookAtPlayerGoalDefinition;
import dev.vexsoft.core.paper.mob.goal.RandomMovementGoalDefinition;
import java.util.UUID;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

class MobFoundationTest {

  @Test
  void normalizesStableKeys() {
    assertEquals(MobKey.of("arcane_monolith", "cave/chicken"),
        MobKey.parse("Arcane-Monolith:Cave/Chicken"));
    assertThrows(IllegalArgumentException.class, () -> MobKey.parse("missing_namespace"));
  }

  @Test
  void personalScopeIncludesOnlyItsOwner() {
    UUID owner = UUID.randomUUID();
    MobScope personal = MobScope.player(owner);

    assertFalse(personal.isGlobal());
    assertTrue(personal.includes(owner));
    assertFalse(personal.includes(UUID.randomUUID()));
    assertTrue(MobScope.global().includes(UUID.randomUUID()));
  }

  @Test
  void passiveDefinitionHasNoGoalsOrMovementByDefault() {
    MobDefinition definition = MobDefinition.builder(
        MobKey.of("arcane_monolith", "passive_chicken"), EntityType.CHICKEN
    ).build();

    assertTrue(definition.goals().isEmpty());
    assertEquals(0.0D, definition.movementSpeed());
    assertFalse(definition.gravity());
    assertFalse(definition.collidable());
  }

  @Test
  void optInGoalsRemainInRegistrationOrder() {
    RandomMovementGoalDefinition movement = RandomMovementGoalDefinition.builder().build();
    LookAtPlayerGoalDefinition looking = LookAtPlayerGoalDefinition.builder().build();
    MobDefinition definition = MobDefinition.builder(
        MobKey.of("arcane_monolith", "moving_chicken"), EntityType.CHICKEN
    ).movementSpeed(0.25D).goal(movement).goal(looking).build();

    assertEquals(java.util.List.of(movement, looking), definition.goals());
  }
}
