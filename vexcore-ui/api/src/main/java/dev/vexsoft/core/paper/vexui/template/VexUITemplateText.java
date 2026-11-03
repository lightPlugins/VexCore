package dev.vexsoft.core.paper.vexui.template;

import java.util.Map;
import net.kyori.adventure.text.Component;

/** Resolves template text through the caller's existing localization system on every render. */
@FunctionalInterface
public interface VexUITemplateText {

    /** Resolves a template key and scalar replacements without flattening prepared styled components. */
    Component text(String key, Map<String, String> values);

    /** Resolves a template key without scalar replacements. */
    default Component text(final String key) {
        return text(key, Map.of());
    }
}
