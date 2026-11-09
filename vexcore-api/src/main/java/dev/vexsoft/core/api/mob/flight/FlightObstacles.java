package dev.vexsoft.core.api.mob.flight;

/** Selects the terrain that an explicit flight order must avoid. Liquids always remain solid. */
public enum FlightObstacles {
    SOLID,
    IGNORE_LEAVES
}
