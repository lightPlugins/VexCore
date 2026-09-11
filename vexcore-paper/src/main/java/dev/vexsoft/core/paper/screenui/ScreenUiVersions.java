package dev.vexsoft.core.paper.screenui;

import dev.vexsoft.core.paper.screenui.v26_2.V26_2ScreenUiVersionDefinition;
import dev.vexsoft.core.paper.screenui.version.ScreenUiVersionDefinition;
import java.util.List;
import org.bukkit.Bukkit;

/** Selects an explicitly supported screen UI resource-pack adapter. */
public final class ScreenUiVersions {

    private static final List<ScreenUiVersionDefinition> DEFINITIONS = List.of(new V26_2ScreenUiVersionDefinition());

    private ScreenUiVersions() {
    }

    public static Class<? extends ScreenUiVersionDefinition> select() {
        String version = Bukkit.getMinecraftVersion();

        return DEFINITIONS.stream()
            .filter(definition -> definition.getSupportedVersions().contains(version))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Unsupported screen UI version: " + version))
            .getClass();
    }
}
