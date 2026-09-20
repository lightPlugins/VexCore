package dev.vexsoft.core.paper.packets.interactiveui;

/** Immutable input decoded without touching player or world state on the network thread. */
public record InteractiveUiPacketInput(
    Kind kind,
    float yaw,
    float pitch,
    int sequence,
    int blockX,
    int blockY,
    int blockZ
) {

    /** Gameplay actions routed to a canvas while it owns the viewer's controls. */
    public enum Kind {
        ROTATION,
        PRESS,
        RELEASE,
        EXIT,
        BLOCK_ACTION,
        OTHER_GAMEPLAY
    }
}
