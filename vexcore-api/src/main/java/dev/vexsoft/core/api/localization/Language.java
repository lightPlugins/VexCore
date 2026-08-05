package dev.vexsoft.core.api.localization;

import lombok.Value;
import net.kyori.adventure.text.Component;

/** Describes an available language and the component used to present it to players. */
@Value
public class Language {
  LanguageKey key;
  Component displayName;
  boolean defaultLanguage;
}
