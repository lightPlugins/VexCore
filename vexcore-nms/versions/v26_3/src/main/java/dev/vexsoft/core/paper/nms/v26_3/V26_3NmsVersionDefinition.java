package dev.vexsoft.core.paper.nms.v26_3;

import dev.vexsoft.core.paper.nms.service.NmsMobAdapterService;
import dev.vexsoft.core.paper.nms.version.NmsVersionDefinition;
import java.util.Set;

/** Native entity control definition for Minecraft 26.3. */
public final class V26_3NmsVersionDefinition implements NmsVersionDefinition {

    private static final String VERSION = "26.3";

    @Override
    public String getAdapterVersion() {
        return VERSION;
    }

    @Override
    public Set<String> getSupportedVersions() {
        return Set.of(VERSION);
    }

    @Override
    public Class<? extends NmsMobAdapterService> getMobAdapter() {
        return VexNmsMobAdapterService.class;
    }
}
