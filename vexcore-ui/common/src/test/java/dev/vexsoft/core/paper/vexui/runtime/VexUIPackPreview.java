package dev.vexsoft.core.paper.vexui.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.flattener.ComponentFlattener;
import net.kyori.adventure.text.flattener.FlattenerListener;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.event.ClickEvent;

/** CPU preview decodes the actual emitted font glyphs, including shifts and client-derived icon advances. */
final class VexUIPackPreview {

    private final Map<Key, Map<Integer, Glyph>> fonts = new HashMap<>();
    private final Map<String, BufferedImage> images = new HashMap<>();
    private final ObjectMapper json = new ObjectMapper();
    private final List<NativeHit> hits = new ArrayList<>();
    private final Path pack = Path.of(System.getProperty("vexui.pack"));

    BufferedImage render(final VexUIRenderedFrame frame) throws IOException {
        hits.clear();
        BufferedImage image = new BufferedImage(frame.width() + 1, frame.height() + 12, BufferedImage.TYPE_INT_ARGB);
        var styles = new ArrayDeque<>(List.of(Style.empty()));
        int[] position = {0, 0};
        ComponentFlattener.basic().flatten(frame.body(), new FlattenerListener() {
            @Override
            public void pushStyle(final Style style) {
                styles.push(styles.peek().merge(style));
            }

            @Override
            public void component(final String text) {
                for (int point : text.codePoints().toArray()) {
                    if (point == '\n') {
                        if (position[0] != frame.width()) {
                            throw new AssertionError("Inconsistent line advance: " + position[0]);
                        }
                        position[0] = 0;
                        position[1] += 9;
                        continue;
                    }
                    var style = styles.peek();
                    Glyph glyph = font(style.font()).get(point);
                    if (glyph == null) {
                        throw new AssertionError("Unknown glyph " + point + " in " + style.font());
                    }
                    var click = style.clickEvent();
                    if (click != null && click.payload() instanceof ClickEvent.Payload.Custom custom) {
                        int top = position[1] + (glyph.image() == null ? 0 : glyph.top());
                        int height = glyph.image() == null ? 9 : glyph.image().getHeight();
                        hits.add(new NativeHit(position[0], top, glyph.advance(), height, custom.key()));
                    }
                    if (glyph.image() != null) {
                        var color = style.color();
                        int tint = color == null ? 0xFFFFFF : color.value();
                        paint(image, glyph, position[0], position[1], tint);
                        if (style.decoration(TextDecoration.BOLD) == TextDecoration.State.TRUE) {
                            paint(image, glyph, position[0] + 1, position[1], tint);
                        }
                    }
                    position[0] += glyph.advance()
                        + (style.decoration(TextDecoration.BOLD) == TextDecoration.State.TRUE ? 1 : 0);
                }
            }

            @Override
            public void popStyle(final Style style) {
                styles.pop();
            }
        });
        return image;
    }

    Key actionAt(final double x, final double y) {
        Key result = null;
        for (var hit : hits) {
            if (x >= hit.x() && x < hit.x() + hit.width() && y >= hit.y() && y < hit.y() + hit.height()) {
                result = hit.action();
            }
        }
        return result;
    }

    void save(final VexUIRenderedFrame frame) throws IOException {
        save(frame, Path.of(System.getProperty("vexui.preview")));
    }

    void save(final VexUIRenderedFrame frame, final Path destination) throws IOException {
        BufferedImage source = render(frame);
        BufferedImage scaled = new BufferedImage(source.getWidth() * 3, source.getHeight() * 3, BufferedImage.TYPE_INT_ARGB);
        var graphics = scaled.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        graphics.drawImage(source, 0, 0, scaled.getWidth(), scaled.getHeight(), null);
        graphics.dispose();
        Files.createDirectories(Objects.requireNonNull(destination.toAbsolutePath().getParent()));
        ImageIO.write(scaled, "PNG", destination.toFile());
    }

    private Map<Integer, Glyph> font(final Key key) {
        return fonts.computeIfAbsent(key, value -> {
            Map<Integer, Glyph> glyphs = new HashMap<>();
            try {
                JsonNode definition = json.readTree(pack.resolve("assets/" + value.namespace() + "/font/" + value.value() + ".json").toFile());
                for (var provider : definition.get("providers")) {
                    if (provider.get("type").asText().equals("space")) {
                        provider.get("advances").properties().forEach(entry -> glyphs.put(entry.getKey().codePointAt(0),
                            new Glyph(null, 0, entry.getValue().asInt())));
                    } else {
                        BufferedImage atlas = image(provider.get("file").asText());
                        JsonNode rows = provider.get("chars");
                        int columns = rows.get(0).asText().codePointCount(0, rows.get(0).asText().length());
                        int cellWidth = atlas.getWidth() / columns;
                        int cellHeight = atlas.getHeight() / rows.size();
                        int height = provider.get("height").asInt();
                        int ascent = provider.get("ascent").asInt();
                        if (ascent > height) {
                            throw new AssertionError("Invalid bitmap ascent");
                        }
                        for (int row = 0; row < rows.size(); row++) {
                            int[] chars = rows.get(row).asText().codePoints().toArray();
                            for (int column = 0; column < chars.length; column++) {
                                if (chars[column] == 0) {
                                    continue;
                                }
                                BufferedImage cell = atlas.getSubimage(column * cellWidth, row * cellHeight, cellWidth, cellHeight);
                                int ink = 0;
                                for (int x = 0; x < cellWidth; x++) {
                                    for (int y = 0; y < cellHeight; y++) {
                                        if ((cell.getRGB(x, y) >>> 24) != 0) {
                                            ink = Math.max(ink, x + 1);
                                        }
                                    }
                                }
                                float scale = height / (float) cellHeight;
                                BufferedImage scaled = new BufferedImage(Math.max(1, Math.round(cellWidth * scale)), height,
                                    BufferedImage.TYPE_INT_ARGB);
                                var graphics = scaled.createGraphics();
                                graphics.drawImage(cell, 0, 0, scaled.getWidth(), scaled.getHeight(), null);
                                graphics.dispose();
                                glyphs.putIfAbsent(chars[column], new Glyph(scaled, 7 - ascent, (int) (0.5 + ink * scale) + 1));
                            }
                        }
                    }
                }
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
            return glyphs;
        });
    }

    private BufferedImage image(final String name) throws IOException {
        BufferedImage cached = images.get(name);
        if (cached != null) {
            return cached;
        }
        Key key = Key.key(name);
        String path = "assets/" + key.namespace() + "/textures/" + key.value();
        BufferedImage image;
        if (key.namespace().equals("minecraft")) {
            try (var zip = new ZipFile(System.getProperty("vexui.client"));
                 var input = zip.getInputStream(zip.getEntry(path))) {
                image = ImageIO.read(input);
            }
        } else {
            image = ImageIO.read(pack.resolve(path).toFile());
        }
        images.put(name, image);
        return image;
    }

    private void paint(final BufferedImage destination, final Glyph glyph, final int x, final int y, final int tint) {
        for (int column = 0; column < glyph.image().getWidth(); column++) {
            for (int row = 0; row < glyph.image().getHeight(); row++) {
                int pixel = glyph.image().getRGB(column, row);
                if ((pixel >>> 24) == 0) {
                    continue;
                }
                int targetX = x + column;
                int targetY = y + glyph.top() + row;
                if (targetX < 0 || targetY < 0 || targetX >= destination.getWidth() || targetY >= destination.getHeight()) {
                    throw new AssertionError("Visible glyph escaped the measured canvas");
                }
                int r = ((pixel >>> 16) & 255) * ((tint >>> 16) & 255) / 255;
                int g = ((pixel >>> 8) & 255) * ((tint >>> 8) & 255) / 255;
                int b = (pixel & 255) * (tint & 255) / 255;
                destination.setRGB(targetX, targetY, (pixel & 0xFF000000) | r << 16 | g << 8 | b);
            }
        }
    }

    private record Glyph(BufferedImage image, int top, int advance) {
    }

    private record NativeHit(int x, int y, int width, int height, Key action) {
    }
}
