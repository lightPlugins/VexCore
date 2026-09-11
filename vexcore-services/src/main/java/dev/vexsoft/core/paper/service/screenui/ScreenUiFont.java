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
