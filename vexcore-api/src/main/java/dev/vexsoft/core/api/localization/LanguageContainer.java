package dev.vexsoft.core.api.localization;

import dev.vexsoft.core.api.player.PlayerContainer;

/** Provides the selected language of one loaded Vex player. */
public interface LanguageContainer extends PlayerContainer {

  /** Returns the player's validated selected language. */
  Language getLanguage();

  /** Changes the player's selected language and publishes the language-change notification. */
  void setLanguage(LanguageKey language);
}
