package dev.vexsoft.core.paper.mob.spawner;

import java.util.Locale;
import java.util.Objects;

/** Stable namespaced identity of a custom mob spawner. */
public record MobSpawnerKey(String namespace, String value) implements Comparable<MobSpawnerKey> {

    /** Creates and validates a spawner key. */
    public MobSpawnerKey {
        namespace = validate(namespace, "namespace");
        value = validate(value, "value");
    }

    /** Creates a spawner key from namespace and value. */
    public static MobSpawnerKey of(final String namespace, final String value) {
        return new MobSpawnerKey(namespace, value);
    }

    @Override
    public int compareTo(final MobSpawnerKey other) {
        return toString().compareTo(Objects.requireNonNull(other, "other").toString());
    }

    @Override
    public String toString() {
        return namespace + ':' + value;
    }

    private static String validate(final String input, final String name) {
        String normalized = Objects.requireNonNull(input, name).trim().toLowerCase(Locale.ROOT).replace('-', '_');

        if (!normalized.matches("[a-z][a-z0-9_./]{0,126}")) {
            throw new IllegalArgumentException("Invalid mob spawner " + name + ": " + input);
        }

        return normalized;
    }
}
