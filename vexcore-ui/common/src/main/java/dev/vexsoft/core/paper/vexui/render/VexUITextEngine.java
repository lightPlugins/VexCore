package dev.vexsoft.core.paper.vexui.render;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.flattener.ComponentFlattener;
import net.kyori.adventure.text.flattener.FlattenerListener;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;

/** Measures and clips resolved components without discarding their nested styles. */
@RequiredArgsConstructor
public final class VexUITextEngine {

    private static final int CACHE_LIMIT = 512;
    private static final Key[] FONTS = IntStream.range(0, 9)
        .mapToObj(offset -> Key.key("vexcore:vexui/text/y_" + offset)).toArray(Key[]::new);

    private final VexUIMetrics metrics;
    // Engines belong to one render/wrap operation; no viewer text survives in a shared cache.
    private final Map<Component, List<Letter>> measured = new IdentityHashMap<>();
    private final Map<Component, List<Letter>> multiline = new IdentityHashMap<>();

    public int width(final Component text) {
        return letters(text).stream().mapToInt(letter -> advance(letter)).sum();
    }

    public Component fit(final Component text, final int maxWidth, final int offset) {
        return fitMeasured(text, maxWidth, offset).component();
    }

    /** Separates native decorations whose baseline does not follow our vertically shifted bitmap glyphs. */
    DecoratedText decorations(final Component component) {
        var spans = new ArrayList<Decoration>();
        var clean = new ArrayList<Letter>();
        int x = 0;
        for (var letter : letters(component)) {
            var style = letter.style();
            int width = advance(letter);
            boolean underline = style.decoration(TextDecoration.UNDERLINED) == TextDecoration.State.TRUE;
            boolean strike = style.decoration(TextDecoration.STRIKETHROUGH) == TextDecoration.State.TRUE;
            if (underline || strike) {
                var textColor = style.color();
                int color = textColor == null ? 0xFFFFFF : textColor.value();
                var previous = spans.isEmpty() ? null : spans.getLast();
                if (previous != null && previous.x() + previous.width() == x && previous.color() == color
                    && previous.underline() == underline && previous.strike() == strike) {
                    spans.set(spans.size() - 1, new Decoration(previous.x(), previous.width() + width,
                        color, underline, strike));
                } else {
                    spans.add(new Decoration(x, width, color, underline, strike));
                }
            }
            clean.add(new Letter(letter.point(), style.decoration(TextDecoration.UNDERLINED, false)
                .decoration(TextDecoration.STRIKETHROUGH, false)));
            x += width;
        }
        return new DecoratedText(compose(clean), List.copyOf(spans));
    }

    FittedText fitMeasured(final Component text, final int maxWidth, final int offset) {
        List<Letter> letters = letters(text);
        int total = letters.stream().mapToInt(letter -> advance(letter)).sum();
        int limit = Math.max(0, maxWidth);
        boolean clipped = total > limit;
        int dots = metrics.advance('.', false) * 3;
        if (clipped && dots <= limit) {
            limit -= dots;
        }
        int used = 0;
        Style last = Style.empty();
        int end = 0;
        for (Letter letter : letters) {
            int advance = advance(letter);
            if (used + advance > limit) {
                break;
            }
            last = letter.style();
            used += advance;
            end++;
        }
        Component result = compose(letters.subList(0, end));
        if (clipped && dots <= maxWidth) {
            result = result.append(Component.text("...", last).decoration(TextDecoration.BOLD, false));
            used += dots;
        }
        return new FittedText(result.font(FONTS[offset])
            .shadowColor(ShadowColor.shadowColor(0)), used);
    }

    public List<Component> wrap(final Component component, final int width, final int maximumLines) {
        List<Component> lines = new ArrayList<>();
        var letters = letters(component, true);
        int start = 0;
        while (start < letters.size()) {
            int end = start;
            int used = 0;
            int space = -1;
            while (end < letters.size() && letters.get(end).point() != '\n') {
                int advance = advance(letters.get(end));
                if (end > start && used + advance > Math.max(1, width)) {
                    break;
                }
                used += advance;
                if (letters.get(end).point() == ' ') {
                    space = end;
                }
                end++;
            }
            boolean newline = end < letters.size() && letters.get(end).point() == '\n';
            int next = newline ? end + 1 : end;
            if (!newline && end < letters.size() && letters.get(end).point() == ' ') {
                next = end + 1;
            } else if (!newline && end < letters.size() && space > start) {
                end = space;
                next = space + 1;
            }
            Component line = compose(letters.subList(start, end));
            if (maximumLines > 0 && lines.size() + 1 == maximumLines && next < letters.size()) {
                // Fitting an intentionally overlong suffix retains the same styled ellipsis as one-line labels.
                line = fit(line.append(Component.text("...")), width, 0);
                lines.add(line);
                return List.copyOf(lines);
            }
            lines.add(line);
            start = next;
            if (newline && start == letters.size() && (maximumLines == 0 || lines.size() < maximumLines)) {
                lines.add(Component.empty());
            }
        }
        if (lines.isEmpty()) {
            lines.add(Component.empty());
        }
        return List.copyOf(lines);
    }

    private Component compose(final List<Letter> letters) {
        var result = Component.text();
        var run = new StringBuilder();
        Style previous = Style.empty();
        for (var letter : letters) {
            if (!previous.equals(letter.style()) && !run.isEmpty()) {
                result.append(Component.text(run.toString(), previous));
                run.setLength(0);
            }
            previous = letter.style();
            run.appendCodePoint(metrics.character(letter.point()));
        }
        if (!run.isEmpty()) {
            result.append(Component.text(run.toString(), previous));
        }
        return result.build();
    }

    private int advance(final Letter letter) {
        return metrics.advance(letter.point(), letter.style().decoration(TextDecoration.BOLD) == TextDecoration.State.TRUE);
    }

    private List<Letter> letters(final Component text) {
        return letters(text, false);
    }

    private List<Letter> letters(final Component text, final boolean preserveLines) {
        var cache = preserveLines ? multiline : measured;
        var existing = cache.get(text);
        if (existing != null) {
            return existing;
        }
        if (cache.size() >= CACHE_LIMIT) {
            cache.clear();
        }
        var result = flatten(text, preserveLines);
        cache.put(text, result);
        return result;
    }

    private List<Letter> flatten(final Component text, final boolean preserveLines) {
        List<Letter> letters = new ArrayList<>();
        ComponentFlattener.basic().flatten(VexUITranslations.render(text, Locale.ENGLISH), new FlattenerListener() {
            private final Deque<Style> styles = new ArrayDeque<>(List.of(Style.empty()));

            @Override
            public void pushStyle(final Style style) {
                styles.push(styles.peek().merge(style));
            }

            @Override
            public void component(final String value) {
                value.codePoints().forEach(point -> letters.add(new Letter(
                    point == '\n' && !preserveLines ? ' ' : point, styles.peek())));
            }

            @Override
            public void popStyle(final Style style) {
                styles.pop();
            }
        });
        return letters;
    }

    private record Letter(int point, Style style) {
    }

    record FittedText(Component component, int width) {
    }

    record DecoratedText(Component component, List<Decoration> spans) {
    }

    record Decoration(int x, int width, int color, boolean underline, boolean strike) {
    }
}
