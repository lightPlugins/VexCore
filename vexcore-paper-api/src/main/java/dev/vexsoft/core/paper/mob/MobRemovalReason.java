package dev.vexsoft.core.paper.mob;

/** Describes why a runtime mob was removed. */
public enum MobRemovalReason {
    EXPLICIT,
    DEATH,
    PLAYER_DEATH,
    PLAYER_TELEPORT,
    OWNER_QUIT,
    CHUNK_UNLOAD,
    DEFINITION_REMOVED,
    SPAWNER_DEACTIVATED,
    SPAWNER_REMOVED,
    SPAWNER_RELOADED,
    PLUGIN_DISABLE,
    SERVER_SHUTDOWN,
    INVALID_ENTITY
}
