package dev.vexsoft.core.paper.screenui;

import dev.vexsoft.core.api.configuration.ConfigurationSection;

/** Shared rounded background and spacing; opacity is a fraction, not transparency. */
public record UiPanelStyle(boolean background, double opacity, int radius, int padding, int gap) {

    public static final UiPanelStyle DEFAULT = new UiPanelStyle(true, 0.15, 3, 4, 3);

    /** Validates opacity and the bounded logical spacing and radius values. */
    public UiPanelStyle {
        if (!Double.isFinite(opacity) || opacity < 0 || opacity > 1 || radius < 0 || radius > 8
            || padding < 0 || padding > 16 || gap < 0 || gap > 16) {
            throw new IllegalArgumentException("Invalid panel style");
        }
    }

    /** Reads overrides while retaining the explicitly supplied shared style. */
    public static UiPanelStyle parse(ConfigurationSection config, UiPanelStyle defaults) {
        if (config == null) {
            return defaults;
        }
        return new UiPanelStyle(
            config.getBoolean("background", defaults.background()),
            config.getDouble("opacity", defaults.opacity()),
            config.getInt("radius", defaults.radius()),
            config.getInt("padding", defaults.padding()),
            config.getInt("gap", defaults.gap())
        );
    }
}
