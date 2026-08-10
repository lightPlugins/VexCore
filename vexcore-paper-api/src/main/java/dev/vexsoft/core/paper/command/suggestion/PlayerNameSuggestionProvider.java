package dev.vexsoft.core.paper.command.suggestion;

import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.vexsoft.core.api.network.NetworkPlayer;
import dev.vexsoft.core.api.service.network.PlayerDirectoryService;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.command.VexCommandSource;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Suggests local and cached cross-server player names without blocking command completion. */
@Dependencies(PlayerDirectoryService.class)
public final class PlayerNameSuggestionProvider implements SuggestionProvider {

  private final PlayerDirectoryService directory;

  /** Creates a provider backed by the shared network player directory. */
  public PlayerNameSuggestionProvider(final VexServiceRegistry services) {
    directory = Objects.requireNonNull(services, "services")
        .require(PlayerDirectoryService.class);
  }

  @Override
  public CompletableFuture<Suggestions> suggest(
      final VexCommandSource source,
      final SuggestionsBuilder builder
  ) {
    String remaining = builder.getRemainingLowerCase();
    Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    Bukkit.getOnlinePlayers().stream().map(Player::getName).forEach(names::add);
    directory.getOnlinePlayers().stream().map(NetworkPlayer::name).forEach(names::add);
    names.stream()
        .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(remaining))
        .forEach(builder::suggest);
    return builder.buildFuture();
  }
}
