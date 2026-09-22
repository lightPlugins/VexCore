package dev.vexsoft.core.level;

import dev.vexsoft.core.api.configuration.ConfigurationSection;
import java.util.Map;
import java.util.Objects;

/** A non-consuming minimum reached-level requirement usable by existing domain cost models. */
public record LevelRequirementDefinition(String system, int level) {

    /** Validates the instance ID and non-negative required level. */
    public LevelRequirementDefinition {
        if (system == null || !system.matches("[a-z][a-z0-9_-]{0,63}") || level < 0) {
            throw new IllegalArgumentException("Invalid level requirement: " + system + '/' + level);
        }
    }

    /** Parses an optional level requirement from the same shape as the registered requirement type. */
    public static LevelRequirementDefinition parse(final Object value) {
        if (value == null) {
            return null;
        }
        Map<?, ?> fields;
        if (value instanceof ConfigurationSection section) {
            fields = section.getValues(false);
        } else if (value instanceof Map<?, ?> map) {
            fields = map;
        } else {
            throw new IllegalArgumentException("Level requirement must be a map");
        }
        return new LevelRequirementDefinition(
            Objects.toString(fields.get("system"), ""),
            Integer.parseInt(Objects.toString(fields.get("level"), ""))
        );
    }
}
