package dev.vexsoft.core.paper.item;

import dev.vexsoft.core.api.service.VexClassFactory;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import dev.vexsoft.core.item.version.ItemVersionDefinition;
import dev.vexsoft.core.packets.version.MinecraftVersion;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class VexItemVersionRegistry {

  private final VexServiceRegistry services;
  private final Map<MinecraftVersion, ItemVersionDefinition> definitions = new LinkedHashMap<>();

  public VexItemVersionRegistry(final VexServiceRegistry services) {
    this.services = Objects.requireNonNull(services, "services");
  }

  public void register(final Class<? extends ItemVersionDefinition> definitionType) {
    ItemVersionDefinition definition = VexClassFactory.create(
        definitionType,
        services,
        "Item version definition"
    );
    for (String value : definition.getSupportedVersions()) {
      MinecraftVersion version = MinecraftVersion.of(value);
      ItemVersionDefinition existing = definitions.putIfAbsent(version, definition);
      if (existing != null) {
        throw new IllegalStateException("Item version is already registered: " + version);
      }
    }
  }

  public ItemVersionDefinition require(final String minecraftVersion) {
    MinecraftVersion version = MinecraftVersion.of(minecraftVersion);
    ItemVersionDefinition definition = definitions.get(version);
    if (definition == null) {
      throw new IllegalStateException(
          "VexCore does not support Minecraft " + version
              + " data components. Supported versions: " + definitions.keySet()
      );
    }
    return definition;
  }
}
