package dev.vexsoft.core.paper.commands;

import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.command.VexCommandSource;
import dev.vexsoft.core.paper.command.suggestion.SuggestionProvider;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Suggests online names for commands that handle missing players with localized feedback. */
@Dependencies
public final class OnlinePlayerSuggestionProvider implements SuggestionProvider {

    public OnlinePlayerSuggestionProvider(final VexServiceRegistry services) {
    }

    @Override
    public CompletableFuture<Suggestions> suggest(final VexCommandSource source, final SuggestionsBuilder builder) {
        Bukkit.getOnlinePlayers().stream().map(Player::getName)
            .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(builder.getRemainingLowerCase()))
            .sorted().forEach(builder::suggest);
        return builder.buildFuture();
    }
}
