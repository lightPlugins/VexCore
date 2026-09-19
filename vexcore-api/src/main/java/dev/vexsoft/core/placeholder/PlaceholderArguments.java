package dev.vexsoft.core.placeholder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

/** Immutable argument path following a registered placeholder identifier. */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class PlaceholderArguments {

    private static final PlaceholderArguments EMPTY = new PlaceholderArguments(List.of());
    private final List<String> values;

    /** Returns an empty argument path. */
    public static PlaceholderArguments empty() {
        return EMPTY;
    }

    /** Creates an immutable argument path. */
    public static PlaceholderArguments of(final List<String> values) {
        List<String> checked = List.copyOf(Objects.requireNonNull(values, "values"));

        return checked.isEmpty() ? EMPTY : new PlaceholderArguments(checked);
    }

    /** Returns the number of path segments. */
    public int size() {
        return values.size();
    }

    /** Returns whether no arguments were supplied. */
    public boolean isEmpty() {
        return values.isEmpty();
    }

    /** Returns one path segment. */
    public String get(final int index) {
        return values.get(index);
    }

    /** Returns a copy of the argument segments. */
    public List<String> asList() {
        return new ArrayList<>(values);
    }
}
