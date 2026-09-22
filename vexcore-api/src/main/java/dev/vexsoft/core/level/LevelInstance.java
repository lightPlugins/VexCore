package dev.vexsoft.core.level;

import java.util.Objects;

/** One named progression definition; its name is always a localization key. */
public record LevelInstance(String id, String nameKey, CompiledLevelDefinition definition) {

    /** Validates the stable instance identity and compiled definition. */
    public LevelInstance {
        if (id == null || !id.matches("[a-z][a-z0-9_-]{0,63}")) {
            throw new IllegalArgumentException("Invalid level instance ID: " + id);
        }
        if (nameKey == null || nameKey.isBlank()) {
            throw new IllegalArgumentException("Level instance requires a localization name key");
        }
        Objects.requireNonNull(definition, "definition");
    }
}
