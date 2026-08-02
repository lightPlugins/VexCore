package dev.vexsoft.core.api.localization;

import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.VexService;
import java.util.Collection;
import java.util.Optional;

/**
 * Manages available languages and each player's selected language
 */
public interface LanguageService extends VexService {

  /** Returns the language currently selected by a Vex player */
  public Language getLanguage(VexPlayer player);

  /** Finds a globally available language by its folder key */
  public Optional<Language> findLanguage(String language);

  /** Returns every language globally available through VexCore */
  public Collection<Language> getLanguages();

  /** Changes the cached language selected by a Vex player */
  public void setLanguage(VexPlayer player, LanguageKey language);

  /** Reloads every registered localization cache */
  public void reload();
}
