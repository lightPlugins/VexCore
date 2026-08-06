package dev.vexsoft.core.paper.command;

import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.vexsoft.core.api.localization.LanguageService;
import dev.vexsoft.core.api.service.Dependencies;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import dev.vexsoft.core.paper.command.suggestion.SuggestionProvider;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@Dependencies(LanguageService.class)
public final class LanguageSuggestionProvider implements SuggestionProvider {

  private final LanguageService languages;

  public LanguageSuggestionProvider(final VexServiceRegistry services) {
    languages = Objects.requireNonNull(services, "services").require(LanguageService.class);
  }

  @Override
  public CompletableFuture<Suggestions> suggest(
      final VexCommandSource source,
      final SuggestionsBuilder builder
  ) {
    String remaining = builder.getRemainingLowerCase();
    languages.getLanguages().stream()
        .map(language -> language.getKey().getValue())
        .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(remaining))
        .forEach(builder::suggest);
    return builder.buildFuture();
  }
}
