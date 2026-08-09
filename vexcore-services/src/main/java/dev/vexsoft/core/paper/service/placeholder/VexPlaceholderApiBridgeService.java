package dev.vexsoft.core.paper.service.placeholder;

import dev.vexsoft.core.api.service.placeholder.PlaceholderService;
import dev.vexsoft.core.api.service.player.PlayerService;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.placeholder.VexPlaceholderExpansion;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/** Optional PlaceholderAPI lifecycle bridge for one Bukkit plugin. */
@Dependencies({PlaceholderService.class, PlayerService.class})
public final class VexPlaceholderApiBridgeService
    implements PlaceholderApiBridgeService, AutoCloseable {

  private final Plugin plugin;
  private final VexPaperPlaceholderService placeholders;
  private final PlayerService players;
  private VexPlaceholderExpansion expansion;

  public VexPlaceholderApiBridgeService(final VexServiceRegistry services) {
    VexServiceRegistry checkedServices = Objects.requireNonNull(services, "services");
    if (!(checkedServices.getOwner() instanceof Plugin bukkitPlugin)) {
      throw new IllegalArgumentException("PlaceholderAPI bridge owner must be a Bukkit plugin");
    }
    plugin = bukkitPlugin;
    PlaceholderService service = checkedServices.require(PlaceholderService.class);
    if (!(service instanceof VexPaperPlaceholderService paperPlaceholders)) {
      throw new IllegalStateException("Unsupported PlaceholderService implementation");
    }
    placeholders = paperPlaceholders;
    players = checkedServices.require(PlayerService.class);
  }

  @Override
  public synchronized void enable() {
    if (expansion != null || !Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
      return;
    }
    VexPlaceholderExpansion created = new VexPlaceholderExpansion(plugin, placeholders, players);
    if (created.register()) {
      expansion = created;
    } else {
      plugin.getLogger().warning(
          "Could not register the " + created.getIdentifier() + " PlaceholderAPI expansion"
      );
    }
  }

  @Override
  public synchronized void close() {
    if (expansion != null) {
      expansion.unregister();
      expansion = null;
    }
  }
}
