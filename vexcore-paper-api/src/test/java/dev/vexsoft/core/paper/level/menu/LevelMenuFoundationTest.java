package dev.vexsoft.core.paper.level.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.vexsoft.core.execution.TypedExecutionDescription;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

final class LevelMenuFoundationTest {

  @Test
  void usesTheExactDefaultSnakeOrder() {
    LevelMenuSettings settings = LevelMenuSettings.defaults();
    assertEquals(LevelMenuSettings.DEFAULT_SNAKE_SLOTS, settings.bounds().getSlots());
    assertEquals(45, settings.inventorySize());
  }

  @Test
  void expandsEveryMandatoryMultilineBlock() {
    List<Component> rendered = LevelLoreRenderer.render(
        List.of(
            Component.text("Header"),
            Component.text(LevelLoreRenderer.REWARDS),
            Component.text(LevelLoreRenderer.COSTS),
            Component.text(LevelLoreRenderer.REQUIREMENTS)
        ),
        List.of(Component.text("Reward A"), Component.text("Reward B")),
        List.of(Component.text("Cost")),
        List.of(Component.text("Requirement"))
    );
    assertEquals(5, rendered.size());
    assertEquals(Component.text("Reward A"), rendered.get(1));
    assertThrows(IllegalArgumentException.class, () -> LevelLoreRenderer.render(
        List.of(Component.text(LevelLoreRenderer.REWARDS)), List.of(), List.of(), List.of()
    ));
  }

  @Test
  void replacesStructuredTypeLinePlaceholders() {
    List<Component> rendered = LevelTypeLineRenderer.render(
        Map.of("coins", Component.text("+ %amount% Coins")),
        List.of(new TypedExecutionDescription(
            "coins", "", Map.of("amount", Component.text("200")), Component.empty()
        ))
    );
    assertEquals(Component.text("+ ").append(Component.text("200")).append(Component.text(" Coins")),
        rendered.getFirst());
  }
}
