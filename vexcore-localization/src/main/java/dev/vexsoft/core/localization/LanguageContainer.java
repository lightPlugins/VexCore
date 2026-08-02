package dev.vexsoft.core.localization;

import dev.vexsoft.core.api.localization.LanguageKey;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public final class LanguageContainer {

  private String language = LanguageKey.EN_EN.getValue();
}
