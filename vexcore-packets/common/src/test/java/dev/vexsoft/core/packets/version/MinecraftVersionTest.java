package dev.vexsoft.core.packets.version;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

public final class MinecraftVersionTest {

  @Test
  public void parsesPatchVersions() {
    MinecraftVersion version = MinecraftVersion.of("26.2.3");

    assertEquals(26, version.getPart(0));
    assertEquals(2, version.getPart(1));
    assertEquals(3, version.getPart(2));
    assertEquals("26.2.3", version.toString());
  }

  @Test
  public void normalizesTrailingZeroComponents() {
    assertEquals(MinecraftVersion.of("26.2"), MinecraftVersion.of("26.2.0"));
    assertEquals("26.2", MinecraftVersion.of("26.2.0.0").toString());
  }

  @Test
  public void comparesEveryNumericComponent() {
    MinecraftVersion base = MinecraftVersion.of("26.2");
    MinecraftVersion patch = MinecraftVersion.of("26.2.3");
    MinecraftVersion nextMinor = MinecraftVersion.of("26.3");

    assertEquals(-1, Integer.signum(base.compareTo(patch)));
    assertEquals(-1, Integer.signum(patch.compareTo(nextMinor)));
  }

  @Test
  public void rejectsNonNumericVersions() {
    assertThrows(IllegalArgumentException.class, () -> MinecraftVersion.of("26.2-pre1"));
  }
}
