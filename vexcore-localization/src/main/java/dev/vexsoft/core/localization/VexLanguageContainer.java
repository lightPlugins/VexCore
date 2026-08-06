package dev.vexsoft.core.localization;

import dev.vexsoft.core.api.localization.Language;
import dev.vexsoft.core.api.localization.LanguageContainer;
import dev.vexsoft.core.api.localization.LanguageKey;
import dev.vexsoft.core.api.localization.LanguageService;
import dev.vexsoft.core.api.player.VexPlayer;
import java.util.Objects;
import java.util.function.Consumer;

/** Default player-bound language facade. */
final class VexLanguageContainer implements LanguageContainer {

  private final VexPlayer player;
  private final LanguageService languages;
  private final LanguageChangeDispatcherService changes;

  VexLanguageContainer(
      final VexPlayer player,
      final LanguageService languages,
      final LanguageChangeDispatcherService changes
  ) {
    this.player = Objects.requireNonNull(player, "player");
    this.languages = Objects.requireNonNull(languages, "languages");
    this.changes = Objects.requireNonNull(changes, "changes");
  }

  @Override
  public Language getLanguage() {
    String selected = player.read(VexCorePlayerData.LANGUAGE, LanguageData::getLanguage);
    return languages.findLanguage(selected)
        .orElseGet(() -> requireLanguage(LanguageKey.EN_EN));
  }

  @Override
  public void setLanguage(final LanguageKey language) {
    Language selected = requireLanguage(Objects.requireNonNull(language, "language"));
    Language previous = getLanguage();
    if (previous.getKey().equals(selected.getKey())) {
      return;
    }
    player.update(
        VexCorePlayerData.LANGUAGE,
        (Consumer<LanguageData>) data -> data.setLanguage(selected.getKey().getValue())
    );
    changes.dispatch(player, previous, selected);
  }

  private Language requireLanguage(final LanguageKey key) {
    return languages.findLanguage(key.getValue()).orElseThrow(
        () -> new IllegalArgumentException("Language is not available: " + key)
    );
  }
}
