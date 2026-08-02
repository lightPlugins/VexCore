package dev.vexsoft.core.api.localization;

import lombok.Value;
import net.kyori.adventure.text.Component;

@Value
public class Language {
  LanguageKey key;
  Component displayName;
  boolean defaultLanguage;
}
