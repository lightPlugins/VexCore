package dev.vexsoft.core.common.service.localization;


import dev.vexsoft.core.api.localization.LanguageKey;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Persisted language selection kept separate from the player-bound feature container. */
@Getter
@Setter
@NoArgsConstructor
final class LanguageData {

  private String language = LanguageKey.EN_EN.getValue();
}
