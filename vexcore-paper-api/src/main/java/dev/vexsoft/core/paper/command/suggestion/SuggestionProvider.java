package dev.vexsoft.core.paper.command.suggestion;

import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.vexsoft.core.paper.command.VexCommandSource;
import java.util.concurrent.CompletableFuture;

/**
 * Provides dynamic Brigadier suggestions for command arguments
 */
public interface SuggestionProvider {

  /** Creates suggestions for the current command argument */
  CompletableFuture<Suggestions> suggest(
      VexCommandSource source,
      SuggestionsBuilder builder
  );
}
