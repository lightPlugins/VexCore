package dev.vexsoft.core.paper.reactor.provider;

import dev.vexsoft.core.api.service.Dependencies;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

@Dependencies
public final class MinecraftItemTypeProvider implements ItemTypeProvider {
  public MinecraftItemTypeProvider(final VexServiceRegistry services) {
    Objects.requireNonNull(services, "services");
  }

  @Override
  public String getNamespace() {
    return Key.MINECRAFT_NAMESPACE;
  }

  @Override
  public Predicate<ItemStack> compile(final Key key) {
    String name = key.value().replace('-', '_').toUpperCase(Locale.ROOT);
    Material material;
    try {
      material = Material.valueOf(name);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Unknown Minecraft item type: " + key.asString(), exception);
    }
    if (!material.isItem()) {
      throw new IllegalArgumentException("Minecraft material is not an item: " + key.asString());
    }
    return item -> item.getType() == material;
  }
}
