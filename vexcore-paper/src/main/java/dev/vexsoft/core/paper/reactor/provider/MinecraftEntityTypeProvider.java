package dev.vexsoft.core.paper.reactor.provider;

import dev.vexsoft.core.api.service.Dependencies;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;
import net.kyori.adventure.key.Key;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

@Dependencies
public final class MinecraftEntityTypeProvider implements EntityTypeProvider {
  public MinecraftEntityTypeProvider(final VexServiceRegistry services) {
    Objects.requireNonNull(services, "services");
  }

  @Override
  public String getNamespace() {
    return Key.MINECRAFT_NAMESPACE;
  }

  @Override
  public Predicate<Entity> compile(final Key key) {
    String name = key.value().replace('-', '_').toUpperCase(Locale.ROOT);
    EntityType type;
    try {
      type = EntityType.valueOf(name);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Unknown Minecraft entity type: " + key.asString(), exception);
    }
    return entity -> entity.getType() == type;
  }
}
