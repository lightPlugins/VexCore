package dev.vexsoft.core.level;

import dev.vexsoft.core.api.player.profile.PlayerDataScope;
import java.util.Objects;

/** One named progression definition; its name is always a localization key. */
public record LevelInstance(String id, String nameKey, CompiledLevelDefinition definition, PlayerDataScope dataScope) {

    /** Keeps existing consumers account-scoped without requiring profile support. */
    public LevelInstance(final String id, final String nameKey, final CompiledLevelDefinition definition) {
        this(id, nameKey, definition, PlayerDataScope.ACCOUNT);
    }

    /** Validates the stable instance identity and compiled definition. */
    public LevelInstance {
        if (id == null || !id.matches("[a-z][a-z0-9_-]{0,63}")) {
            throw new IllegalArgumentException("Invalid level instance ID: " + id);
        }
        if (nameKey == null || nameKey.isBlank()) {
            throw new IllegalArgumentException("Level instance requires a localization name key");
        }
        Objects.requireNonNull(definition, "definition");
        dataScope = dataScope == null ? PlayerDataScope.ACCOUNT : dataScope;
    }
}
