package dev.vexsoft.core.paper.commands;

import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.vexsoft.core.api.service.currency.CurrencyRegistry;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.command.VexCommandSource;
import dev.vexsoft.core.paper.command.suggestion.SuggestionProvider;
import java.util.concurrent.CompletableFuture;

/** Suggests active virtual currencies across all plugin owners. */
@Dependencies(CurrencyRegistry.class)
public final class CurrencySuggestionProvider implements SuggestionProvider {

    private final CurrencyRegistry currencies;

    public CurrencySuggestionProvider(final VexServiceRegistry services) {
        currencies = services.require(CurrencyRegistry.class);
    }

    @Override
    public CompletableFuture<Suggestions> suggest(final VexCommandSource source, final SuggestionsBuilder builder) {
        CurrencyCommandIds.suggestions(currencies.getRegisteredCurrencies()).stream()
            .filter(key -> key.startsWith(builder.getRemainingLowerCase())).sorted().forEach(builder::suggest);
        return builder.buildFuture();
    }
}
