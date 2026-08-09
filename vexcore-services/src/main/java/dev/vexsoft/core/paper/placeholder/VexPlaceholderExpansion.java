package dev.vexsoft.core.paper.placeholder;

import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.player.PlayerService;
import dev.vexsoft.core.paper.service.placeholder.VexPaperPlaceholderService;
import dev.vexsoft.core.placeholder.PlaceholderContext;
import dev.vexsoft.core.placeholder.PlaceholderNames;
import java.util.Objects;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Publishes one Vex plugin's registered placeholders to PlaceholderAPI. */
public final class VexPlaceholderExpansion extends PlaceholderExpansion {

  private final Plugin plugin;
  private final String identifier;
  private final VexPaperPlaceholderService placeholders;
  private final PlayerService players;

  public VexPlaceholderExpansion(
      final Plugin plugin,
      final VexPaperPlaceholderService placeholders,
      final PlayerService players
  ) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    identifier = PlaceholderNames.namespace(plugin.getName());
    this.placeholders = Objects.requireNonNull(placeholders, "placeholders");
    this.players = Objects.requireNonNull(players, "players");
  }

  @Override
  public @NotNull String getIdentifier() {
    return identifier;
  }

  @Override
  public @NotNull String getAuthor() {
    return String.join(", ", plugin.getPluginMeta().getAuthors());
  }

  @Override
  public @NotNull String getVersion() {
    return plugin.getPluginMeta().getVersion();
  }

  @Override
  public boolean persist() {
    return true;
  }

  @Override
  public @Nullable String onRequest(
      final OfflinePlayer player,
      final @NotNull String parameters
  ) {
    if (player == null || parameters.isBlank()) {
      return null;
    }
    VexPlayer vexPlayer = players.find(player.getUniqueId()).orElse(null);
    if (vexPlayer == null) {
      return null;
    }
    String source = '%' + identifier + '_' + parameters + '%';
    String resolved = placeholders.resolveRegistered(PlaceholderContext.of(vexPlayer), source);
    return source.equals(resolved) ? null : resolved;
  }
}
