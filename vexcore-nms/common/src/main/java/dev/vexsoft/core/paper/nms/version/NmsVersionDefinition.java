package dev.vexsoft.core.paper.nms.version;

import dev.vexsoft.core.paper.nms.service.NmsMobAdapterService;
import java.util.Set;

/** Selects native entity control implementations for compatible Minecraft versions. */
public interface NmsVersionDefinition {

    /** Returns the base Minecraft revision represented by this definition. */
    String getAdapterVersion();

    /** Returns every Minecraft version explicitly supported by this definition. */
    Set<String> getSupportedVersions();

    /** Returns the selected native mob control implementation. */
    Class<? extends NmsMobAdapterService> getMobAdapter();
}
