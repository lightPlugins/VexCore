package dev.vexsoft.core.paper.nms;

import dev.vexsoft.core.paper.nms.v26_2.V26_2NmsVersionDefinition;
import dev.vexsoft.core.paper.nms.version.NmsVersionDefinition;
import java.util.List;
import org.bukkit.Bukkit;

/** Selects the native adapter matching the running Minecraft server. */
public final class NmsVersions {

  private static final List<NmsVersionDefinition> DEFINITIONS = List.of(
      new V26_2NmsVersionDefinition()
  );

  private NmsVersions() {
  }

  /** Returns the native definition for the running Minecraft version. */
  public static NmsVersionDefinition select() {
    String minecraftVersion = Bukkit.getMinecraftVersion();
    return DEFINITIONS.stream()
        .filter(definition -> definition.getSupportedVersions().contains(minecraftVersion))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException(
            "Unsupported Minecraft version for VexCore NMS: " + minecraftVersion
        ));
  }
}
