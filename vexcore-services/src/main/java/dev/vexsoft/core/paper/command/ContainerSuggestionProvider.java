package dev.vexsoft.core.paper.command;

import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.common.service.data.PlayerDataCoordinatorService;
import dev.vexsoft.core.paper.command.suggestion.SuggestionProvider;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Suggests registered persistent player-data container identifiers. */
@Dependencies(PlayerDataCoordinatorService.class)
public final class ContainerSuggestionProvider implements SuggestionProvider {

  private final PlayerDataCoordinatorService players;

  public ContainerSuggestionProvider(final VexServiceRegistry services) {
    players = Objects.requireNonNull(services, "services")
        .require(PlayerDataCoordinatorService.class);
  }

  @Override
  public CompletableFuture<Suggestions> suggest(
      final VexCommandSource source,
      final SuggestionsBuilder builder
  ) {
    String remaining = builder.getRemainingLowerCase();
    players.getContainerIds().stream()
        .filter(container -> container.startsWith(remaining))
        .forEach(builder::suggest);
    return builder.buildFuture();
  }
}
