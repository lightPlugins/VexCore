package dev.vexsoft.core.paper.mob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.paper.mob.goal.LookAtPlayerGoalDefinition;
import dev.vexsoft.core.paper.mob.goal.RandomMovementGoalDefinition;
import dev.vexsoft.core.paper.packets.display.DisplayBillboard;
import dev.vexsoft.core.paper.packets.display.DisplayBrightness;
import java.util.UUID;
import net.kyori.adventure.text.Component;
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

  @Test
  void definitionsExposeOptionalEntitySpecificInitializers() {
    MobInitializer initializer = mob -> { };
    MobDefinition definition = MobDefinition.builder(
        MobKey.of("arcane_monolith", "villager"), EntityType.VILLAGER
    ).initializer(initializer).build();

    assertEquals(initializer, definition.initializer().orElseThrow());
  }

  @Test
  void mobHologramsExposeCompleteTextDisplayStyling() {
    DisplayBrightness brightness = new DisplayBrightness(15, 15);
    MobHologramDefinition hologram = MobHologramDefinition.builder(
        0.35F, true, (viewer, mob) -> Component.empty()
    ).billboard(DisplayBillboard.VERTICAL)
        .scale(1.25F)
        .backgroundColor(0x40000000)
        .defaultBackground(true)
        .shadowed(true)
        .seeThrough(true)
        .brightness(brightness)
        .lineWidth(240)
        .build();

    assertEquals(DisplayBillboard.VERTICAL, hologram.billboard());
    assertEquals(1.25F, hologram.scale());
    assertEquals(0x40000000, hologram.backgroundColor());
    assertTrue(hologram.defaultBackground());
    assertTrue(hologram.shadowed());
    assertTrue(hologram.seeThrough());
    assertEquals(brightness, hologram.brightness().orElseThrow());
    assertEquals(240, hologram.lineWidth());
  }
}
