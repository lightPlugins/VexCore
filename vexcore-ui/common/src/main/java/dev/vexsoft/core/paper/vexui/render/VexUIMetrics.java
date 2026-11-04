package dev.vexsoft.core.paper.vexui.render;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import net.kyori.adventure.key.Key;

/** Reads measurements generated from the same sources as the resource pack. */
public final class VexUIMetrics {

    private final int[] text = new int[Character.MAX_VALUE + 1];
    private final Map<Integer, Integer> supplementary;
    private final int[][] tiles = new int[3][257];
    private final Map<Key, Map<Integer, Icon>> icons;

    public VexUIMetrics() {
        var values = new Properties();
        try (var input = VexUIMetrics.class.getResourceAsStream("/dev/vexsoft/core/paper/vexui/metrics.properties")) {
            if (input == null) {
                throw new IllegalStateException("Missing generated VexUI measurements");
            }
            values.load(input);
            if (Integer.parseInt(values.getProperty("format")) != 3) {
                throw new IllegalStateException("Unsupported VexUI asset format");
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot read VexUI measurements", failure);
        }
        Arrays.fill(text, -1);
        Map<Integer, Integer> extra = new HashMap<>();
        Map<Key, Map<Integer, Icon>> sprites = new HashMap<>();
        for (String key : values.stringPropertyNames()) {
            if (key.startsWith("text.") && Character.isDigit(key.charAt(5))) {
                int point = Integer.parseInt(key.substring(5));
                int advance = Integer.parseInt(values.getProperty(key));
                if (point < text.length) {
                    text[point] = advance;
                } else {
                    extra.put(point, advance);
                }
            } else if (key.startsWith("tile.0_")) {
                tiles[key.charAt(7) - '0'][Integer.parseInt(key.substring(9))] = Integer.parseInt(values.getProperty(key));
            } else if (key.startsWith("icon.") && !key.endsWith(".advance")) {
                int separator = key.lastIndexOf('.');
                Key sprite = Key.key(key.substring(5, separator));
                int size = Integer.parseInt(key.substring(separator + 1));
                sprites.computeIfAbsent(sprite, ignored -> new HashMap<>()).put(size,
                    new Icon(Integer.parseInt(values.getProperty(key)),
                        Integer.parseInt(values.getProperty(key + ".advance"))));
            }
        }
        supplementary = Map.copyOf(extra);
        sprites.replaceAll((sprite, sizes) -> Map.copyOf(sizes));
        icons = Map.copyOf(sprites);
    }

    public int character(final int point) {
        return point >= 0 && (point < text.length ? text[point] >= 0 : supplementary.containsKey(point)) ? point : '?';
    }

    public int advance(final int point, final boolean bold) {
        int resolved = character(point);
        return (resolved < text.length ? text[resolved] : supplementary.get(resolved)) + (bold ? 1 : 0);
    }

    public int tile(final int color, final int kind, final int width) {
        if (color != 0 || kind < 0 || kind >= tiles.length || width < 0 || width >= tiles[kind].length
            || tiles[kind][width] == 0) {
            throw new IllegalArgumentException("Unregistered VexUI tile " + color + "_" + kind + "_" + width);
        }
        return tiles[kind][width];
    }

    public int icon(final Key sprite, final int size) {
        return requireIcon(sprite, size).point();
    }

    public boolean hasIcon(final Key sprite, final int size) {
        var sizes = icons.get(sprite);
        return sizes != null && sizes.containsKey(size);
    }

    public int iconAdvance(final Key sprite, final int size) {
        return requireIcon(sprite, size).advance();
    }

    private Icon requireIcon(final Key sprite, final int size) {
        var sizes = icons.get(sprite);
        var icon = sizes == null ? null : sizes.get(size);
        if (icon == null) {
            throw new IllegalArgumentException("Unregistered VexUI icon " + sprite + "." + size);
        }
        return icon;
    }

    private record Icon(int point, int advance) {
    }
}
