package dev.vexsoft.core.localization;

import dev.vexsoft.core.api.localization.Language;
import dev.vexsoft.core.api.localization.LanguageKey;
import dev.vexsoft.core.api.localization.LanguageService;
import dev.vexsoft.core.api.localization.LocalizedMessage;
import dev.vexsoft.core.api.player.PlayerService;
import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.Dependencies;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;

@Dependencies({
    LocalizationRegistryService.class,
    LanguageChangeDispatcherService.class,
    PlayerService.class
})
public final class VexLanguageService implements LanguageService {

  private static final String CORE_OWNER = "VexCore";

  private final LocalizationRegistryService localizations;
  private final PlayerService players;
  private final LanguageChangeDispatcherService changes;

  public VexLanguageService(final VexServiceRegistry services) {
    Objects.requireNonNull(services, "services");
    localizations = services.require(LocalizationRegistryService.class);
    changes = services.require(LanguageChangeDispatcherService.class);
    players = services.require(PlayerService.class);
  }

  @Override
  public Language getLanguage(final VexPlayer player) {
    String selected = player.read(VexCorePlayerData.LANGUAGE, LanguageContainer::getLanguage);
    return findLanguage(selected).orElseGet(() -> requireLanguage(LanguageKey.EN_EN));
  }

  @Override
  public Optional<Language> findLanguage(final String language) {
    LanguageKey key;
    try {
      key = LanguageKey.of(language);
    } catch (IllegalArgumentException exception) {
      return Optional.empty();
    }
    return localizations.getLanguages(CORE_OWNER).contains(key)
        ? Optional.of(language(key))
        : Optional.empty();
  }

  @Override
  public Collection<Language> getLanguages() {
    return localizations.getLanguages(CORE_OWNER).stream()
        .sorted()
        .map(this::language)
        .toList();
  }

  @Override
  public CompletableFuture<Void> setLanguage(
      final VexPlayer player,
      final LanguageKey language
  ) {
    Objects.requireNonNull(player, "player");
    Language selected = requireLanguage(Objects.requireNonNull(language, "language"));
    Language previous = getLanguage(player);
    if (previous.getKey().equals(selected.getKey())) {
      return CompletableFuture.completedFuture(null);
    }
    player.update(VexCorePlayerData.LANGUAGE, container -> {
      container.setLanguage(selected.getKey().getValue());
    });
    changes.dispatch(player, previous, selected);
    return players.save(player);
  }

  @Override
  public void reload() {
    localizations.reloadAll();
  }

  private Language requireLanguage(final LanguageKey key) {
    return findLanguage(key.getValue()).orElseThrow(
        () -> new IllegalArgumentException("Language is not available: " + key)
    );
  }

  private Language language(final LanguageKey key) {
    LocalizedMessage name = localizations.resolve(
        CORE_OWNER,
        key,
        "language.name",
        Map.of()
    );
    Component displayName = name.getLines().getFirst();
    return new Language(key, displayName, key.equals(LanguageKey.EN_EN));
  }
}
