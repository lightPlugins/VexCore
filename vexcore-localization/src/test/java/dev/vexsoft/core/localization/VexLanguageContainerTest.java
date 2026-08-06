package dev.vexsoft.core.localization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.api.localization.Language;
import dev.vexsoft.core.api.localization.LanguageKey;
import dev.vexsoft.core.api.localization.LanguageService;
import dev.vexsoft.core.api.player.VexPlayer;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

class VexLanguageContainerTest {

  @Test
  void validatesPersistsAndDispatchesLanguageChangesThroughThePlayerContainer() {
    VexPlayer player = new VexPlayer(UUID.randomUUID(), "Alex");
    player.install(VexCorePlayerData.LANGUAGE, new LanguageData());
    Language english = language(LanguageKey.EN_EN, true);
    Language german = language(LanguageKey.of("de_DE"), false);
    LanguageService languages = new TestLanguages(english, german);
    AtomicReference<Language> previous = new AtomicReference<>();
    AtomicReference<Language> selected = new AtomicReference<>();
    LanguageChangeDispatcherService changes = (changedPlayer, oldLanguage, newLanguage) -> {
      assertSame(player, changedPlayer);
      previous.set(oldLanguage);
      selected.set(newLanguage);
    };
    VexLanguageContainer container = new VexLanguageContainer(player, languages, changes);

    container.setLanguage(german.getKey());

    assertEquals(german, container.getLanguage());
    assertEquals(english, previous.get());
    assertEquals(german, selected.get());
    assertTrue(player.getDirtyKeys().contains(VexCorePlayerData.LANGUAGE));
  }

  private static Language language(final LanguageKey key, final boolean fallback) {
    return new Language(key, Component.text(key.getValue()), fallback);
  }

  private record TestLanguages(Language english, Language german) implements LanguageService {

    @Override
    public Optional<Language> findLanguage(final String language) {
      return List.of(english, german).stream()
          .filter(candidate -> candidate.getKey().getValue().equalsIgnoreCase(language))
          .findFirst();
    }

    @Override
    public Collection<Language> getLanguages() {
      return List.of(english, german);
    }

    @Override
    public void reload() {
    }
  }
}
