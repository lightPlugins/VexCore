package dev.vexsoft.core.paper.nms.v26_2;

import dev.vexsoft.core.paper.nms.service.NmsMobAdapterService;
import dev.vexsoft.core.paper.nms.version.NmsVersionDefinition;
import java.util.Set;

/** Native entity control definition for Minecraft 26.2. */
public final class V26_2NmsVersionDefinition implements NmsVersionDefinition {

    private static final String VERSION = "26.2";

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
        return V26_2NmsMobAdapterService.class;
    }
}
