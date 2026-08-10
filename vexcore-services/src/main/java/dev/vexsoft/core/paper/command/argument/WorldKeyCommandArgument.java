package dev.vexsoft.core.paper.command.argument;

import dev.vexsoft.core.api.world.WorldKey;
import dev.vexsoft.core.paper.command.VexCommandSource;
import dev.vexsoft.core.paper.service.world.WorldService;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import java.util.Objects;
import java.util.Locale;
import java.util.function.Consumer;
import org.bukkit.Bukkit;

/** Parses namespaced world IDs and suggests currently loaded worlds. */
public final class WorldKeyCommandArgument implements CommandArgumentType<WorldKey> {

  private final WorldService worlds;

  public WorldKeyCommandArgument(final VexServiceRegistry services) {
    worlds = Objects.requireNonNull(services, "services").require(WorldService.class);
  }

  @Override
  public Class<WorldKey> getValueType() {
    return WorldKey.class;
  }

  @Override
  public WorldKey parseValue(final String nativeType) {
    return WorldKey.parse(nativeType);
  }

  @Override
  public void suggest(
      final VexCommandSource source,
      final String input,
      final Consumer<String> suggestion
  ) {
    Bukkit.getWorlds().stream()
        .map(worlds::getKey)
        .map(WorldKey::asString)
        .filter(value -> value.startsWith(input.toLowerCase(Locale.ROOT)))
        .forEach(suggestion);
  }
}
