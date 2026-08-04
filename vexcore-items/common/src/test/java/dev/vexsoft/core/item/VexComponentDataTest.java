package dev.vexsoft.core.item;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public final class VexComponentDataTest {

  @Test
  public void copiesLoreBeforeStoringIt() {
    List<Component> source = new ArrayList<>();
    source.add(Component.text("First"));

    List<Component> normalized = VexComponentData.LORE.normalize(source);
    source.add(Component.text("Second"));

    assertEquals(List.of(Component.text("First")), normalized);
  }

  @Test
  public void rejectsInvalidStackSizes() {
    assertThrows(
        IllegalArgumentException.class,
        () -> VexComponentData.MAX_STACK_SIZE.normalize(0)
    );
    assertThrows(
        IllegalArgumentException.class,
        () -> VexComponentData.MAX_STACK_SIZE.normalize(100)
    );
  }

  @Test
  public void marksPresentationComponentsForPackets() {
    assertEquals(VexComponentTarget.PACKET_PRESENTATION, VexComponentData.DISPLAY_NAME.getTarget());
    assertEquals(VexComponentTarget.PACKET_PRESENTATION, VexComponentData.LORE.getTarget());
  }
}
