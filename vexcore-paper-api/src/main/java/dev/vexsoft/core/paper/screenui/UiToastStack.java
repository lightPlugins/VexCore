package dev.vexsoft.core.paper.screenui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

/**
 * Bounded, owner-thread notification queue using measured panels. Call tick from an existing
 * player task; updates are confined to changed content, admissions, expiry and stack reflow.
 * Never stores Player or schedules tasks.
 */
public final class UiToastStack<T> implements AutoCloseable {

    private final ScreenUi screen;
    private final ScreenAnchor anchor;
    private final int x, y, gap, maximum;
    private final double scale;
    private final UiPanelStyle style;
    private final BinaryOperator<T> merge;
    private final Function<T, Component> line;
    private final Function<T, TextColor> color;
    private final List<Entry<T>> visible = new ArrayList<>();
    private final LinkedHashMap<String, T> waiting = new LinkedHashMap<>();
    private long sequence;

    /** Creates a bounded toast stack with the supplied layout and rendering callbacks. */
    public UiToastStack(
        ScreenUi screen,
        ScreenAnchor anchor,
        int x,
        int y,
        int gap,
        int maximum,
        BinaryOperator<T> merge,
        Function<T, Component> line,
        Function<T, TextColor> color
    ) {
        this(screen, anchor, x, y, gap, maximum, false, merge, line, color);
    }

    /** Creates a bounded toast stack with the supplied layout and rendering callbacks. */
    public UiToastStack(
        ScreenUi screen,
        ScreenAnchor anchor,
        int x,
        int y,
        int gap,
        int maximum,
        boolean compact,
        BinaryOperator<T> merge,
        Function<T, Component> line,
        Function<T, TextColor> color
    ) {
        this(screen, anchor, x, y, gap, maximum, compact ? 0.5 : 1.0, merge, line, color);
    }

    /** Creates a bounded toast stack with the supplied layout and rendering callbacks. */
    public UiToastStack(
        ScreenUi screen,
        ScreenAnchor anchor,
        int x,
        int y,
        int gap,
        int maximum,
        double scale,
        BinaryOperator<T> merge,
        Function<T, Component> line,
        Function<T, TextColor> color
    ) {
        this(screen, anchor, x, y, gap, maximum, scale, UiPanelStyle.DEFAULT, merge, line, color);
    }

    /** Creates notifications sharing a plugin's configured panel style. */
    public UiToastStack(
        ScreenUi screen, ScreenAnchor anchor, int x, int y, int gap, int maximum,
        double scale, UiPanelStyle style, BinaryOperator<T> merge,
        Function<T, Component> line, Function<T, TextColor> color
    ) {
        this.style = Objects.requireNonNull(style);
        this.screen = Objects.requireNonNull(screen);
        this.anchor = Objects.requireNonNull(anchor);

        if (maximum < 1 || maximum > 5 || gap < 0 || gap > 12 || Math.abs((long) x) > 272 || y > 256
            || y - maximum * (20 + gap) < -256) {
            throw new IllegalArgumentException("Invalid toast stack geometry");
        }

        this.x = x;
        this.y = y;
        this.gap = gap;
        this.maximum = maximum;
        this.scale = UiScale.validate(scale);
        this.merge = Objects.requireNonNull(merge);
        this.line = Objects.requireNonNull(line);
        this.color = Objects.requireNonNull(color);
    }

    /** False means queue capacity is reached; callers can use a localized chat fallback. */
    public boolean push(String key, T value, long gameTime) {
        if (screen.isClosed()) {
            return false;
        }

        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");

        for (Entry<T> entry : visible) {
            // Keep every generation finite, even for continuously mined resources.
            if (entry.key.equals(key) && gameTime - entry.started < 55) {
                entry.value = merge.apply(entry.value, value);
                entry.dirty = true;

                return true;
            }
        }

        if (!waiting.containsKey(key) && waiting.size() >= 64) {
            return false;
        }

        waiting.merge(key, value, merge);

        return true;
    }

    /** Advances expiration and renders the visible toast entries for the given game time. */
    public void tick(long gameTime) {
        if (screen.isClosed()) {
            waiting.clear();
            visible.clear();

            return;
        }

        visible.removeIf(entry -> {
            if (gameTime < entry.started - 4 || gameTime - entry.started >= UiAnimation.TOTAL_TICKS) {
                screen.remove(entry.id);

                return true;
            }

            return false;
        });

        while (visible.size() < maximum && !waiting.isEmpty()) {
            var next = waiting.entrySet().iterator().next();
            Entry<T> entry = new Entry<>(next.getKey(), "toast-" + sequence++, next.getValue(), gameTime + 2);

            waiting.remove(next.getKey());
            visible.add(entry);
        }

        int edge = y;
        var entries = visible.iterator();
        while (entries.hasNext()) {
            Entry<T> entry = entries.next();
            if (entry.dirty || entry.lastY != edge) {
                UiNode content = new UiNode.Text(
                    "message",
                    line.apply(entry.value).colorIfAbsent(color.apply(entry.value))
                );
                UiPanelLayout layout = UiPanelLayout.builder().anchor(anchor).x(x).y(y)
                    .maxWidth(224).scale(scale).layer(20).style(style).build();
                int height = screen.measurePanel(content, layout).height();
                if (edge - height < -256) {
                    // Keep accepted notifications pending until the older measured panels have expired.
                    waiting.merge(entry.key, entry.value, merge);
                    screen.remove(entry.id);
                    entries.remove();
                    continue;
                }
                UiPanelBounds bounds = screen.panel(entry.id, content, layout.toBuilder().y(edge).build());
                entry.height = bounds.height();
                entry.lastY = edge;
                entry.dirty = false;
            }
            edge -= entry.height + gap;
        }
    }

    public boolean isEmpty() {
        return visible.isEmpty() && waiting.isEmpty();
    }

    @Override
    public void close() {
        waiting.clear();
        visible.clear();
        screen.close();
    }

    /** Tracks a toast value, identity, lifetime and measured stack position. */
    public static final class Entry<T> {

        private final String key, id;
        private final long started;
        private T value;
        private boolean dirty = true;
        private int height;
        private int lastY = Integer.MIN_VALUE;

        private Entry(String key, String id, T value, long started) {
            this.key = key;
            this.id = id;
            this.value = value;
            this.started = started;
        }
    }
}
