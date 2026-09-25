package dev.vexsoft.core.api.localization;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** Published text templates grouped by language and fully qualified message key. */
public record PublishedLocalizationCatalog(Map<String, Map<String, Template>> languages) {

    /** Defensively copies the complete language catalog. */
    public PublishedLocalizationCatalog {
        Objects.requireNonNull(languages, "languages");
        languages = languages.entrySet().stream().collect(
            Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> Map.copyOf(entry.getValue()))
        );
    }

    /** Returns an immutable view of the published language templates. */
    @Override
    public Map<String, Map<String, Template>> languages() {
        return Map.copyOf(languages);
    }

    /** One message represented by a string or a list of strings. */
    public record Template(List<String> lines, boolean list) {

        /** Copies the lines so a published template cannot change after validation. */
        public Template {
            lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
        }
    }
}
