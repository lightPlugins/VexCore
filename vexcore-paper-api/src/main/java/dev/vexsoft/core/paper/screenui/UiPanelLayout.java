package dev.vexsoft.core.paper.screenui;

import dev.vexsoft.core.api.configuration.ConfigurationSection;
import java.util.Locale;
import lombok.Builder;

/** A content-sized panel; maximum width includes padding and text always wraps. */
@Builder(toBuilder = true)
public record UiPanelLayout(ScreenAnchor anchor, int x, int y, int maxWidth, double scale,
                            int layer, UiPanelStyle style, HorizontalAlignment textAlign) {

    /** Resolves omitted defaults and rejects unsupported geometry or scale. */
    public UiPanelLayout {
        anchor = anchor == null ? ScreenAnchor.TOP_LEFT : anchor;
        textAlign = textAlign == null ? HorizontalAlignment.LEFT : textAlign;
        style = style == null ? UiPanelStyle.DEFAULT : style;
        maxWidth = maxWidth == 0 ? 224 : maxWidth;
        scale = scale == 0 ? 0.7 : UiScale.validate(scale);
        if (maxWidth < 32 || maxWidth > 256 || maxWidth - style.padding() * 2 < 16
            || Math.abs((long) x) > 256 || y < -256 || y > 240) {
            throw new IllegalArgumentException("Invalid panel layout");
        }
    }

    /** Reads one panel's placement and optional shared-style overrides. */
    public static UiPanelLayout parse(ConfigurationSection config, UiPanelStyle style) {
        return new UiPanelLayout(
            ScreenAnchor.valueOf(config.getString("anchor", "TOP_LEFT").toUpperCase(Locale.ROOT)),
            config.getInt("offset-x", 0),
            config.getInt("offset-y", 8),
            config.getInt("max-width", 224),
            config.getDouble("scale", 0.7),
            config.getInt("layer", 10),
            UiPanelStyle.parse(config.getSection("style"), style),
            HorizontalAlignment.valueOf(config.getString("text-align", "LEFT").toUpperCase(Locale.ROOT))
        );
    }
}
