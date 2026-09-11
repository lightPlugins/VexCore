package dev.vexsoft.core.api.configuration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable configuration section backed by an in-memory map.
 *
 * <p>This implementation is useful when persisted or generated data needs to pass through a
 * configuration compiler without being written to a temporary file.</p>
 */
public final class MapConfigurationSection implements ConfigurationSection {

    private final Map<String, Object> values;

    /** Creates an immutable recursive copy of the supplied values. */
    public MapConfigurationSection(final Map<String, ?> values) {
        Map<String, Object> normalizedValues = new LinkedHashMap<>();

        Objects.requireNonNull(values, "values")
            .forEach((key, value) -> normalizedValues.put(
                Objects.requireNonNull(key, "configuration key"),
                normalize(value)
            ));
        this.values = Collections.unmodifiableMap(normalizedValues);
    }

    @Override
    public boolean contains(final String path) {
        return resolve(path) != null;
    }

    @Override
    public Object get(final String path) {
        return resolve(path);
    }

    @Override
    public String getString(final String path) {
        Object value = resolve(path);

        return value instanceof String string ? string : null;
    }

    @Override
    public String getString(final String path, final String defaultValue) {
        String value = getString(path);

        return value == null ? defaultValue : value;
    }

    @Override
    public int getInt(final String path, final int defaultValue) {
        Object value = resolve(path);

        return value instanceof Number number ? number.intValue() : defaultValue;
    }

    @Override
    public long getLong(final String path, final long defaultValue) {
        Object value = resolve(path);

        return value instanceof Number number ? number.longValue() : defaultValue;
    }

    @Override
    public double getDouble(final String path, final double defaultValue) {
        Object value = resolve(path);

        return value instanceof Number number ? number.doubleValue() : defaultValue;
    }

    @Override
    public boolean getBoolean(final String path, final boolean defaultValue) {
        Object value = resolve(path);

        return value instanceof Boolean booleanValue ? booleanValue : defaultValue;
    }

    @Override
    public List<String> getStringList(final String path) {
        Object value = resolve(path);

        if (!(value instanceof List<?> list)) {
            return List.of();
        }

        List<String> strings = new ArrayList<>();

        for (Object entry : list) {
            if (entry instanceof String string) {
                strings.add(string);
            }
        }

        return List.copyOf(strings);
    }

    @Override
    public ConfigurationSection getSection(final String path) {
        Object value = resolve(path);

        return value instanceof ConfigurationSection section ? section : null;
    }

    @Override
    public Set<String> getKeys(final boolean deep) {
        Set<String> keys = new LinkedHashSet<>();

        collectKeys(values, "", deep, keys);

        return Collections.unmodifiableSet(keys);
    }

    @Override
    public Map<String, Object> getValues(final boolean deep) {
        if (!deep) {
            return values;
        }

        Map<String, Object> flattenedValues = new LinkedHashMap<>();

        collectValues(values, "", flattenedValues);

        return Collections.unmodifiableMap(flattenedValues);
    }

    @Override
    public void set(final String path, final Object value) {
        throw new UnsupportedOperationException("Map configuration sections are immutable");
    }

    private Object resolve(final String path) {
        Objects.requireNonNull(path, "path");

        if (path.isBlank()) {
            return this;
        }

        Object current = this;

        for (String segment : path.split("\\.")) {
            if (!(current instanceof MapConfigurationSection section)) {
                return null;
            }

            current = section.values.get(segment);

            if (current == null) {
                return null;
            }
        }

        return current;
    }

    private static Object normalize(final Object value) {
        if (value instanceof ConfigurationSection section) {
            return new MapConfigurationSection(section.getValues(false));
        }

        if (value instanceof Map<?, ?> map) {
            return new MapConfigurationSection(toStringKeyMap(map));
        }

        if (value instanceof List<?> list) {
            return list.stream().map(MapConfigurationSection::normalize).toList();
        }

        return value;
    }

    private static Map<String, Object> toStringKeyMap(final Map<?, ?> source) {
        Map<String, Object> convertedValues = new LinkedHashMap<>();

        source.forEach((key, value) -> convertedValues.put(String.valueOf(key), value));

        return convertedValues;
    }

    private static void collectKeys(
        final Map<String, Object> source,
        final String prefix,
        final boolean deep,
        final Set<String> result
    ) {
        source.forEach((key, value) -> {
            String path = prefix.isEmpty() ? key : prefix + "." + key;

            result.add(path);

            if (deep && value instanceof MapConfigurationSection section) {
                collectKeys(section.values, path, true, result);
            }
        });
    }

    private static void collectValues(
        final Map<String, Object> source,
        final String prefix,
        final Map<String, Object> result
    ) {
        source.forEach((key, value) -> {
            String path = prefix.isEmpty() ? key : prefix + "." + key;

            if (value instanceof MapConfigurationSection section) {
                collectValues(section.values, path, result);
            } else {
                result.put(path, value);
            }
        });
    }
}
