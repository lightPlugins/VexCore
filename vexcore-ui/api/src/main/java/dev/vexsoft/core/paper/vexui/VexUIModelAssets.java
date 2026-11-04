package dev.vexsoft.core.paper.vexui;

import dev.vexsoft.core.paper.vexui.element.VexUITexture;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;

/** Immutable build-generated model previews; nested elements reuse the existing positioned texture primitive. */
public record VexUIModelAssets(Map<Key, Map<Integer, VexUITexture>> models) {

    /** Copies every size mapping and rejects mismatched preview dimensions. */
    public VexUIModelAssets {
        Map<Key, Map<Integer, VexUITexture>> copy = new LinkedHashMap<>();
        models.forEach((key, sizes) -> {
            Objects.requireNonNull(key, "model");
            sizes.forEach((pixels, texture) -> {
                if (pixels != texture.width() || pixels != texture.height()) {
                    throw new IllegalArgumentException("Model preview size differs from its texture");
                }
            });
            copy.put(key, Map.copyOf(sizes));
        });
        models = Map.copyOf(copy);
    }

    /** Reads a format-1 model manifest without taking ownership of the input stream. */
    public static VexUIModelAssets read(final InputStream input) throws IOException {
        var properties = new Properties();
        properties.load(Objects.requireNonNull(input, "input"));
        if (!"1".equals(properties.getProperty("format"))) {
            throw new IOException("Unsupported VexUI model manifest");
        }
        int count = Integer.parseInt(properties.getProperty("count"));
        if (count < 0 || count > 16_384) {
            throw new IOException("Invalid VexUI model manifest size");
        }
        Map<Key, Map<Integer, VexUITexture>> models = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            String prefix = "model." + index + ".";
            Key key = Key.key(properties.getProperty(prefix + "key"));
            int pixels = Integer.parseInt(properties.getProperty(prefix + "pixels"));
            var texture = new VexUITexture(Key.key(properties.getProperty(prefix + "font")),
                Integer.parseInt(properties.getProperty(prefix + "point")), pixels, pixels,
                Integer.parseInt(properties.getProperty(prefix + "advance")), Component.empty());
            if (models.computeIfAbsent(key, ignored -> new LinkedHashMap<>()).putIfAbsent(pixels, texture) != null) {
                throw new IOException("Duplicate model preview: " + key + " / " + pixels);
            }
        }
        return new VexUIModelAssets(models);
    }
}
