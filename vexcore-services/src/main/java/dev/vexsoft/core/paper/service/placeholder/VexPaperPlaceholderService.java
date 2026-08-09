package dev.vexsoft.core.paper.service.placeholder;

import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.placeholder.PlaceholderService;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.common.service.placeholder.PlaceholderRegistryCoordinatorService;
import dev.vexsoft.core.common.service.placeholder.PlaceholderComponents;
import dev.vexsoft.core.placeholder.PlaceholderContext;
import dev.vexsoft.core.placeholder.VexPlaceholder;
import java.util.Objects;
import java.util.regex.Pattern;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Paper placeholder facade with optional inbound PlaceholderAPI resolution. */
@Dependencies(PlaceholderRegistryCoordinatorService.class)
public final class VexPaperPlaceholderService implements PlaceholderService, AutoCloseable {

  private static final Pattern TOKEN = Pattern.compile("%[A-Za-z0-9_]+%");
  private final VexServiceRegistry services;
  private final ServiceOwner owner;
  private final PlaceholderRegistryCoordinatorService coordinator;

  public VexPaperPlaceholderService(final VexServiceRegistry services) {
    this.services = Objects.requireNonNull(services, "services");
    owner = services.getOwner();
    coordinator = services.require(PlaceholderRegistryCoordinatorService.class);
  }

  @Override
  public <T extends VexPlaceholder> T register(final Class<T> placeholderType) {
    return coordinator.register(owner, services, placeholderType);
  }

  @Override
  public String resolve(final VexPlayer player, final String input) {
    return resolve(PlaceholderContext.of(player), input);
  }

  @Override
  public String resolve(final PlaceholderContext context, final String input) {
    String resolved = resolveRegistered(context, input);
    if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
      return resolved;
    }
    Player player = context.getPlayer().findPlatformPlayer(Player.class).orElse(null);
    return player == null ? resolved : PlaceholderAPI.setPlaceholders(player, resolved);
  }

  /** Resolves only VexCore-managed placeholders without calling PlaceholderAPI. */
  public String resolveRegistered(final PlaceholderContext context, final String input) {
    return coordinator.resolve(context, input);
  }

  @Override
  public Component resolve(final VexPlayer player, final Component component) {
    Component resolved = PlaceholderComponents.resolve(
        this,
        player,
        component
    );
    if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
      return resolved;
    }
    Player bukkitPlayer = player.findPlatformPlayer(Player.class).orElse(null);
    if (bukkitPlayer == null) {
      return resolved;
    }
    return resolved.replaceText(
        TextReplacementConfig.builder()
            .match(TOKEN)
            .replacement((match, builder) -> {
              String source = match.group();
              String replacement = PlaceholderAPI.setPlaceholders(bukkitPlayer, source);
              return source.equals(replacement) ? builder : Component.text(replacement);
            })
            .build()
    );
  }

  @Override
  public void clear() {
    coordinator.unregisterOwner(owner);
  }

  @Override
  public void close() {
    clear();
  }
}
