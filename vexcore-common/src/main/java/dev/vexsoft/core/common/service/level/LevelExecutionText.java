package dev.vexsoft.core.common.service.level;

import dev.vexsoft.core.api.configuration.ConfigurationSection;
import dev.vexsoft.core.api.localization.LanguageContainer;
import dev.vexsoft.core.api.service.level.LevelInstanceService;
import dev.vexsoft.core.api.service.localization.LocalizationService;
import dev.vexsoft.core.execution.PlayerExecutionContext;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.kyori.adventure.text.Component;

/** Shared configuration and localized text for level execution components. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LevelExecutionText {

    public static Map<?, ?> values(final Object value) {
        if (value instanceof ConfigurationSection section) {
            return section.getValues(false);
        }
        if (value instanceof Map<?, ?> map) {
            return map;
        }
        throw new IllegalArgumentException("Level execution component must be a map");
    }

    public static Component render(
        final LocalizationService localizations,
        final LevelInstanceService levels,
        final PlayerExecutionContext context,
        final String id,
        final String key,
        final Map<String, String> values
    ) {
        var language = context.player().getContainer(LanguageContainer.class).getLanguage().getKey();
        Component name = localizations.resolve(language, levels.require(id).nameKey(), Map.of()).getComponent();
        return localizations.resolve(language, key, values).getComponent()
            .replaceText(builder -> builder.matchLiteral("%level_name%").replacement(name));
    }
}
