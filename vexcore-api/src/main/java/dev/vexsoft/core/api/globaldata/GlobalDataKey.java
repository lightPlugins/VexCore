package dev.vexsoft.core.api.globaldata;

import java.util.Objects;
import java.util.function.Supplier;
import lombok.Getter;

/** Identifies one typed global value owned by a plugin. */
public final class GlobalDataKey<T> {

    /** Owner-local persistence name. */
    @Getter
    private final String name;
    @Getter
    private final Class<T> type;
    private final Supplier<? extends T> defaultValue;

    private GlobalDataKey(final String name, final Class<T> type, final Supplier<? extends T> defaultValue) {
        this.name = validateName(name);
        this.type = Objects.requireNonNull(type, "type");
        this.defaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
    }

    /** Creates a typed global-data key and its default-value factory. */
    public static <T> GlobalDataKey<T> of(
        final String name,
        final Class<T> type,
        final Supplier<? extends T> defaultValue
    ) {
        return new GlobalDataKey<>(name, type, defaultValue);
    }

    /** Creates a fresh default value. */
    public T createDefaultValue() {
        return type.cast(Objects.requireNonNull(
            defaultValue.get(),
            "Default value supplier returned null for " + name
        ));
    }

    private static String validateName(final String name) {
        String checkedName = Objects.requireNonNull(name, "name");

        if (!checkedName.matches("[a-z][a-z0-9_]{0,62}")) {
            throw new IllegalArgumentException(
                "Global data name must use lower-case SQL-safe characters: " + checkedName);
        }

        return checkedName;
    }
}
