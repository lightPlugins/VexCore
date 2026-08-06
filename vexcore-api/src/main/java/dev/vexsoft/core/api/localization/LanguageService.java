package dev.vexsoft.core.api.localization;

import dev.vexsoft.core.api.service.VexService;
import java.util.Collection;
import java.util.Optional;

/**
 * Manages the global catalog of languages available through VexCore.
 *
 * <p>Player selections are accessed exclusively through {@link LanguageContainer}.</p>
 */
public interface LanguageService extends VexService {

  /** Finds a globally available language by its folder key */
  Optional<Language> findLanguage(String language);

  /** Returns every language globally available through VexCore */
  Collection<Language> getLanguages();

  /** Reloads every registered localization cache */
  void reload();
}
