package dev.vexsoft.core.paper.reactor.provider;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.block.Block;

@Dependencies
public final class MinecraftBlockTypeProvider implements BlockTypeProvider {

  public MinecraftBlockTypeProvider(final VexServiceRegistry services) {
    Objects.requireNonNull(services, "services");
  }

  @Override
  public String getNamespace() {
    return Key.MINECRAFT_NAMESPACE;
  }

  @Override
  public Predicate<Block> compile(final Key key) {
    String materialName = key.value().replace('-', '_').toUpperCase(Locale.ROOT);
    Material material;
    try {
      material = Material.valueOf(materialName);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Unknown Minecraft block type: " + key.asString(), exception);
    }
    if (!material.isBlock()) {
      throw new IllegalArgumentException("Minecraft material is not a block: " + key.asString());
    }
    return block -> block.getType() == material;
  }
}
