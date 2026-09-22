package dev.vexsoft.core.paper.screenui;

import dev.vexsoft.core.api.configuration.ConfigurationSection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import net.kyori.adventure.text.Component;

/** Viewer-owned configurable panel collection, stacking matching anchors without overlapping. */
public final class UiPanelSet {

    private final ScreenUi screen;
    private final Map<String, UiPanelDefinition> definitions;
    private final Map<String, UiNode> previous = new LinkedHashMap<>();
    private final Map<String, UiPanelLayout> layouts = new LinkedHashMap<>();
    private final Map<String, UiPanelBounds> bounds = new LinkedHashMap<>();

    /** Retains the managed screen and copies definitions, requiring one scale per shared anchor. */
    public UiPanelSet(ScreenUi screen, Map<String, UiPanelDefinition> definitions) {
        this.screen = Objects.requireNonNull(screen);
        this.definitions = Collections.unmodifiableMap(new LinkedHashMap<>(definitions));
        Map<ScreenAnchor, Double> scales = new LinkedHashMap<>();
        for (UiPanelDefinition definition : definitions.values()) {
            UiPanelLayout layout = definition.layout();
            Double previousScale = scales.putIfAbsent(layout.anchor(), layout.scale());
            if (previousScale != null && previousScale != layout.scale()) {
                throw new IllegalArgumentException("Panels at the same anchor must share a scale");
            }
        }
    }

    /** Parses fields in declaration order; each collection owns a stable prefix on its screen. */
    public static Map<String, UiPanelDefinition> parse(
        ConfigurationSection config,
        UiPanelStyle style,
        String localizationPrefix
    ) {
        if (config == null) {
            throw new IllegalArgumentException("Missing panels configuration");
        }
        Map<String, UiPanelDefinition> result = new LinkedHashMap<>();
        for (String id : config.getKeys(false)) {
            result.put(id, UiPanelDefinition.parse(config.getSection(id), style, localizationPrefix + "." + id));
        }
        return Collections.unmodifiableMap(result);
    }

    /** Resolves visible content and updates only changed panels; following panels use measured height. */
    public void render(
        Function<String, List<Component>> localize, Map<String, UiNode> bindings,
        Map<String, Boolean> conditions
    ) {
        Map<ScreenAnchor, Integer> edges = new LinkedHashMap<>();
        definitions.forEach((id, definition) -> {
            UiNode content = definition.resolve(localize, bindings, conditions);
            UiPanelLayout base = definition.layout();
            boolean bottom = base.anchor().ordinal() / 3 == 2;
            int edge = edges.getOrDefault(base.anchor(), base.y());
            int y = bottom ? Math.min(base.y(), edge) : Math.max(base.y(), edge);
            UiPanelLayout layout = base.toBuilder().y(y).build();
            if (!content.equals(previous.get(id)) || !layout.equals(layouts.get(id))) {
                UiPanelBounds measured = screen.panel("panel." + id, content, layout);
                previous.put(id, content);
                layouts.put(id, layout);
                bounds.put(id, measured);
            }
            UiPanelBounds measured = bounds.get(id);
            if (measured.height() > 0) {
                edges.put(
                    base.anchor(), bottom ? measured.y() - base.style().gap()
                        : measured.y() + measured.height() + base.style().gap()
                );
            }
        });
    }
}
