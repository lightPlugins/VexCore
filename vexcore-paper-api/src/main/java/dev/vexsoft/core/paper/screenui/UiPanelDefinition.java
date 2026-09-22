package dev.vexsoft.core.paper.screenui;

import dev.vexsoft.core.api.configuration.ConfigurationSection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import net.kyori.adventure.text.Component;

/** Validated configuration tree with localized text, plugin bindings and conditional visibility. */
public record UiPanelDefinition(boolean enabled, String condition, UiPanelLayout layout, Field content) {

    /** Compiles one configured panel and rejects malformed field definitions. */
    public static UiPanelDefinition parse(ConfigurationSection config, UiPanelStyle style, String localizationPrefix) {
        Objects.requireNonNull(config, "Panel configuration is required");
        if (localizationPrefix == null || !localizationPrefix.matches("[a-zA-Z0-9_-]+(\\.[a-zA-Z0-9_-]+)*")) {
            throw new IllegalArgumentException("Invalid panel localization prefix");
        }
        return new UiPanelDefinition(
            config.getBoolean("enabled", true),
            config.getString("show-if", ""),
            UiPanelLayout.parse(config, style),
            Field.parse("root", config.getSection("content"), 0, localizationPrefix, new HashSet<>())
        );
    }

    /** Resolves current plugin data without storing players, tasks or localization services. */
    public UiNode resolve(
        Function<String, List<Component>> localize, Map<String, UiNode> bindings,
        Map<String, Boolean> conditions
    ) {
        if (!enabled || !condition.isEmpty() && !conditions.getOrDefault(condition, false)) {
            return new UiNode.Group("root", false, List.of());
        }
        return content.resolve(localize, bindings, conditions);
    }

    /** One compiled configuration field with localization, data and visibility references. */
    public record Field(String id, String type, String key, String condition, List<Field> children) {

        /** Takes an immutable snapshot of nested fields. */
        public Field {
            children = List.copyOf(children);
        }

        private static Field parse(
            String id, ConfigurationSection config, int depth, String prefix, Set<String> textNames
        ) {
            Objects.requireNonNull(config, "Missing panel content: " + id);
            if (depth > 8) {
                throw new IllegalArgumentException("Panel content nesting exceeds eight levels");
            }
            String type = config.getString("type", "text");
            if (!List.of("text", "binding", "row", "column").contains(type)) {
                throw new IllegalArgumentException("Unknown panel field type: " + type);
            }
            if (config.contains("localization")) {
                throw new IllegalArgumentException(
                    "Panel localization keys are inferred; remove localization from " + id);
            }
            String name = id.equals("root") ? "text" : id.substring(id.lastIndexOf('.') + 1);
            String key = type.equals("binding") ? config.getString("source", "") : prefix + "." + name;
            if (type.equals("binding") && key.isBlank()) {
                throw new IllegalArgumentException("Panel binding requires a source: " + id);
            }
            if (type.equals("text") && !textNames.add(name)) {
                throw new IllegalArgumentException("Text field names must be unique within a panel: " + name);
            }
            List<Field> children = new ArrayList<>();
            ConfigurationSection fields = config.getSection("children");
            if (fields != null) {
                if (fields.getKeys(false).size() > 32) {
                    throw new IllegalArgumentException("Too many panel fields");
                }
                for (String child : fields.getKeys(false)) {
                    if (!child.matches("[a-zA-Z0-9_-]+")) {
                        throw new IllegalArgumentException("Invalid panel field name: " + child);
                    }
                    children.add(parse(id + "." + child, fields.getSection(child), depth + 1, prefix, textNames));
                }
            }
            return new Field(id, type, key, config.getString("show-if", ""), children);
        }

        private UiNode resolve(
            Function<String, List<Component>> localize, Map<String, UiNode> bindings,
            Map<String, Boolean> conditions
        ) {
            if (!condition.isEmpty() && !conditions.getOrDefault(condition, false)) {
                return new UiNode.Group(id, false, List.of());
            }
            return switch (type) {
                case "text" -> new UiNode.Text(id, localize.apply(key));
                case "binding" -> Objects.requireNonNull(bindings.get(key), "Missing UI binding: " + key);
                default -> new UiNode.Group(
                    id, type.equals("row"), children.stream()
                    .map(child -> child.resolve(localize, bindings, conditions)).toList()
                );
            };
        }
    }
}
