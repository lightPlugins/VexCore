package dev.vexsoft.core.paper.packets.interactiveui;

import java.util.UUID;
import lombok.Value;

/** Viewer-local transport identifiers and camera geometry, without references to native entities. */
@Value
public class InteractiveUiPacketTransport {

    UUID viewerId;
    UUID worldId;
    int mountEntityId;
    int cameraEntityId;
    int blockX;
    int blockY;
    int blockZ;
    double cameraX;
    double cameraY;
    double cameraZ;
    float initialYaw;
    float initialPitch;
}
