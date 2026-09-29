package dev.vexsoft.core.api.localization;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Expands whole-line section markers inside localized YAML string lists. */
public final class LocalizedStringLists {

    private LocalizedStringLists() {
    }

    /** Inserts each section's formatted entries at its marker while preserving explicit line order. */
    public static List<String> expand(
        final List<String> template,
        final Map<String, List<String>> formats,
        final Map<String, List<Map<String, String>>> sections,
        final Map<String, String> values
    ) {
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(formats, "formats");
        Objects.requireNonNull(sections, "sections");
        Objects.requireNonNull(values, "values");
        List<String> result = new ArrayList<>();
        for (String line : template) {
            String section = marker(line);
            if (section == null || !sections.containsKey(section)) {
                result.add(replace(line, values));
                continue;
            }
            List<String> format = Objects.requireNonNull(formats.get(section),
                "Missing format for section " + section);
            for (Map<String, String> entry : sections.get(section)) {
                for (String formatted : format) {
                    result.add(replace(replace(formatted, values), entry));
                }
            }
        }
        return List.copyOf(result);
    }

    /** Replaces simple percent-delimited scalar values without changing text styling. */
    public static String replace(final String source, final Map<String, String> values) {
        String result = Objects.requireNonNull(source, "source");
        for (Map.Entry<String, String> value : values.entrySet()) {
            result = result.replace('%' + value.getKey() + '%', value.getValue());
        }
        return result;
    }

    private static String marker(final String line) {
        if (line.length() < 3 || line.charAt(0) != '%' || line.charAt(line.length() - 1) != '%') {
            return null;
        }
        String name = line.substring(1, line.length() - 1);
        return name.matches("[a-z][a-z0-9_-]*") ? name : null;
    }
}
