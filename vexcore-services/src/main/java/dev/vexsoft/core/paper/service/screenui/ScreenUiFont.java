package dev.vexsoft.core.paper.service.screenui;

import java.io.IOException;
import java.util.Properties;

/** Metrics generated together with the resource pack; no runtime font rasterization. */
final class ScreenUiFont {

    private static final Properties METRICS = load();
    static final int HEIGHT = Integer.parseInt(METRICS.getProperty("height"));

    private ScreenUiFont() {
    }

    static int advance(int point) {
        String value = METRICS.getProperty(Integer.toString(point));

        if (value == null) {
            throw new IllegalArgumentException("Unsupported UI font code point: " + point);
        }

        return Integer.parseInt(value);
    }

    /** Maps unavailable text glyphs to pack-supported punctuation or a visible replacement. */
    static String fallback(int point) {
        if (METRICS.containsKey(Integer.toString(point))) {
            return Character.toString(point);
        }
        return switch (point) {
            case 0x2010, 0x2011, 0x2012, 0x2013, 0x2014, 0x2212 -> "-";
            case 0x2018, 0x2019, 0x201A, 0x2032 -> "'";
            case 0x201C, 0x201D, 0x201E, 0x2033 -> "\"";
            case 0x2026 -> "...";
            case 0x2022, 0x2023, 0x25CF, 0x2605, 0x2606 -> "*";
            case 0x2190 -> "<-";
            case 0x2192 -> "->";
            case 0x2194 -> "<->";
            default -> Character.isWhitespace(point) || Character.isSpaceChar(point) ? " " : "?";
        };
    }

    private static Properties load() {
        try (var input = ScreenUiFont.class.getResourceAsStream("m5x7.properties")) {
            if (input == null) {
                throw new IllegalStateException("Missing generated m5x7 font metrics");
            }

            Properties properties = new Properties();

            properties.load(input);

            return properties;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load m5x7 font metrics", exception);
        }
    }
}
