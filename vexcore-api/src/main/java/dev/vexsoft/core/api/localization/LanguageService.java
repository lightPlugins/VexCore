package dev.vexsoft.core.api.localization;

import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.VexService;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface LanguageService extends VexService {

  /** Returns the language currently selected by a Vex player */
  public Language getLanguage(VexPlayer player);

  /** Finds a globally available language by its folder key */
  public Optional<Language> findLanguage(String language);

  /** Returns every language globally available through VexCore */
  public Collection<Language> getLanguages();

  /** Changes and persists the language selected by a Vex player */
  public CompletableFuture<Void> setLanguage(VexPlayer player, LanguageKey language);

  /** Reloads every registered localization cache */
  public void reload();
}
