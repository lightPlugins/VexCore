package dev.vexsoft.core.vexui.pack;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.vexsoft.core.paper.screenui.progression.ProgressionLayout;
import dev.vexsoft.core.paper.screenui.reward.RewardCardLayout;
import dev.vexsoft.core.paper.vexui.style.VexUITheme;
import java.awt.AlphaComposite;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;

/** Build-only marked vanilla text, menu-palette frames and model previews for animated reward cards. */
public final class RewardCardAssets {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int TEXT_CELL_WIDTH = 18;
    private static final int TEXT_CELL_HEIGHT = 22;
    private static final String DIGITS = "0123456789";
    // RewardCardRenderer formats amounts with WholeAmountFormatter's lowercase suffixes.
    private static final String QUANTITY_GLYPHS = DIGITS + "+.abcdefghijklmnopqrstuvwxyz";

    private RewardCardAssets() {
    }

    /** Generates font variants from the exact same bitmap sources and baselines as the menu text. */
    public static void text(Path root, Path client, List<JsonNode> providers) throws IOException {
        var marked = new ArrayList<JsonNode>();
        try (var zip = new ZipFile(client.toFile())) {
            for (int index = 0; index < providers.size(); index++) {
                var provider = providers.get(index);
                String file = provider.get("file").asText();
                String[] location = file.split(":", 2);
                String asset = "assets/" + location[0] + "/textures/" + location[1];
                BufferedImage source;
                if (Files.isRegularFile(root.resolve(asset))) {
                    source = ImageIO.read(root.resolve(asset).toFile());
                } else {
                    try (var input = zip.getInputStream(zip.getEntry(asset))) {
                        source = ImageIO.read(input);
                    }
                }
                var rows = provider.get("chars");
                int columns = rows.get(0).asText().codePointCount(0, rows.get(0).asText().length());
                int cellWidth = source.getWidth() / columns;
                int cellHeight = source.getHeight() / rows.size();
                int height = provider.path("height").asInt(8);
                int width = Math.round(cellWidth * height / (float) cellHeight);
                int top = 2 + 10 - provider.path("ascent").asInt(7);
                if (width > TEXT_CELL_WIDTH || top < 2 || top + height > TEXT_CELL_HEIGHT) {
                    throw new IOException("Unsupported reward text cell: " + file);
                }
                var atlas = new BufferedImage(columns * TEXT_CELL_WIDTH, rows.size() * TEXT_CELL_HEIGHT,
                    BufferedImage.TYPE_INT_ARGB);
                var graphics = atlas.createGraphics();
                graphics.setComposite(AlphaComposite.Src);
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                for (int row = 0; row < rows.size(); row++) {
                    int[] points = rows.get(row).asText().codePoints().toArray();
                    for (int column = 0; column < columns; column++) {
                        if (points[column] == 0) {
                            continue;
                        }
                        int x = column * TEXT_CELL_WIDTH;
                        int y = row * TEXT_CELL_HEIGHT;
                        graphics.drawImage(source, x, y + top, x + width, y + top + height,
                            column * cellWidth, row * cellHeight,
                            (column + 1) * cellWidth, (row + 1) * cellHeight, null);
                        mark(atlas, x, y, TEXT_CELL_WIDTH);
                    }
                }
                graphics.dispose();
                String texture = "vexcore:reward/text/" + index + ".png";
                png(root, texture, atlas);
                var copy = JSON.createObjectNode().put("type", "bitmap").put("file", texture)
                    .put("height", TEXT_CELL_HEIGHT);
                copy.set("chars", rows.deepCopy());
                marked.add(copy);
            }
        }
        var quantity = subset(root, "vexcore:reward/quantity.png", marked, QUANTITY_GLYPHS);
        var level = subset(root, "vexcore:progression/level.png", marked, DIGITS + "-◇");
        var effect = subset(root, "vexcore:progression/effect.png", marked, "✦");
        fonts(root, "vexcore:reward/text", marked, RewardCardLayout.textAscent(false));
        fonts(root, "vexcore:reward/quantity", List.of(quantity), RewardCardLayout.textAscent(true));
        for (int part = 0; part < ProgressionLayout.PARTS; part++) {
            List<? extends JsonNode> glyphs = switch (part) {
                case 0, ProgressionLayout.EXPERIENCE_TEXT_PART -> marked;
                case 6, ProgressionLayout.EXPERIENCE_BAR_PART -> List.of(effect);
                default -> List.of(level);
            };
            fonts(root, ProgressionLayout.font(part), glyphs, ProgressionLayout.ascent(part));
        }
        frame(root);
    }

    /** Copies marked glyph cells in provider order, preserving their pixels and shader geometry. */
    private static ObjectNode subset(Path root, String texture, List<JsonNode> providers, String characters)
        throws IOException {
        int[] codePoints = characters.codePoints().toArray();
        var remaining = new HashMap<Integer, Integer>();
        for (int index = 0; index < codePoints.length; index++) {
            remaining.put(codePoints[index], index);
        }
        var atlas = new BufferedImage(codePoints.length * TEXT_CELL_WIDTH, TEXT_CELL_HEIGHT,
            BufferedImage.TYPE_INT_ARGB);
        var pixels = new int[TEXT_CELL_WIDTH * TEXT_CELL_HEIGHT];
        for (JsonNode provider : providers) {
            if (remaining.isEmpty()) {
                break;
            }
            String[] source = provider.get("file").asText().split(":", 2);
            var image = ImageIO.read(root.resolve("assets/" + source[0] + "/textures/" + source[1]).toFile());
            var rows = provider.get("chars");
            for (int row = 0; row < rows.size(); row++) {
                int[] points = rows.get(row).asText().codePoints().toArray();
                for (int column = 0; column < points.length; column++) {
                    Integer target = remaining.remove(points[column]);
                    if (target != null) {
                        image.getRGB(column * TEXT_CELL_WIDTH, row * TEXT_CELL_HEIGHT,
                            TEXT_CELL_WIDTH, TEXT_CELL_HEIGHT, pixels, 0, TEXT_CELL_WIDTH);
                        atlas.setRGB(target * TEXT_CELL_WIDTH, 0, TEXT_CELL_WIDTH, TEXT_CELL_HEIGHT,
                            pixels, 0, TEXT_CELL_WIDTH);
                    }
                }
            }
        }
        if (!remaining.isEmpty()) {
            throw new IOException("Missing glyphs for " + texture + ": " + remaining.keySet());
        }
        png(root, texture, atlas);
        var provider = JSON.createObjectNode().put("type", "bitmap").put("file", texture)
            .put("height", TEXT_CELL_HEIGHT);
        provider.putArray("chars").add(characters);
        return provider;
    }

    /** Converts a static menu-model preview into marked 32-pixel icons for all stack movements. */
    public static void icon(Path root, String prefix, BufferedImage source) throws IOException {
        var image = new BufferedImage(34, 34, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        graphics.drawImage(source, 0, 2, 32, 34, 0, 0, source.getWidth(), source.getHeight(), null);
        graphics.dispose();
        mark(image, 0, 0, 34);
        String texture = prefix + ".png";
        png(root, texture, image);
        var provider = JSON.createObjectNode().put("type", "bitmap").put("file", texture).put("height", 34);
        provider.putArray("chars").add("\uE001");
        for (int slot = 0; slot < 4; slot++) {
            for (int previous = 0; previous < 4; previous++) {
                var layout = new RewardCardLayout(slot, previous, 0, 0, false);
                fonts(root, prefix + "/" + layout.variant(), List.of(provider), layout.ascent(2));
            }
        }
    }

    private static void frame(Path root) throws IOException {
        var theme = VexUITheme.PANEL;
        var image = new BufferedImage(224, 42, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 40; y++) {
            for (int x = 0; x < 224; x++) {
                int color = theme.background();
                if (x == 0 || y == 0 || x == 223 || y == 39) {
                    color = theme.outerBorder();
                } else if (x == 1 || y == 1) {
                    color = theme.lightBorder();
                } else if (x == 222 || y == 38) {
                    color = theme.darkBorder();
                } else if (x >= 4 && x <= 37 && y >= 3 && y <= 36) {
                    color = VexUITheme.BUTTON.background();
                    if (x == 4 || y == 3) {
                        color = theme.outerBorder();
                    }
                }
                image.setRGB(x, y + 2, 0xFF000000 | color);
            }
        }
        mark(image, 0, 0, 224);
        png(root, "vexcore:reward/frame.png", image);
        var provider = JSON.createObjectNode().put("type", "bitmap").put("file", "vexcore:reward/frame.png")
            .put("height", 42);
        provider.putArray("chars").add("\uE001");
        for (int slot = 0; slot < 4; slot++) {
            for (int previous = 0; previous < 4; previous++) {
                var layout = new RewardCardLayout(slot, previous, 0, 0, false);
                fonts(root, "vexcore:reward/frame/" + layout.variant(), List.of(provider), layout.ascent(1));
            }
        }
    }

    private static void mark(BufferedImage image, int x, int y, int width) {
        image.setRGB(x, y, 0x01565831);
        image.setRGB(x, y + 1, 0x01434F52);
        // Force a fixed glyph width, independent of the letter's ink width, for quad-origin recovery.
        image.setRGB(x + width - 1, y, 0x01565831);
    }

    private static void fonts(Path root, String key, List<? extends JsonNode> providers, int ascent) throws IOException {
        var definition = JSON.createObjectNode();
        var list = definition.putArray("providers");
        for (var provider : providers) {
            ObjectNode copy = provider.deepCopy();
            copy.put("ascent", ascent);
            list.add(copy);
        }
        String[] location = key.split(":", 2);
        Path target = root.resolve("assets/" + location[0] + "/font/" + location[1] + ".json");
        Files.createDirectories(Objects.requireNonNull(target.getParent(), "Generated asset directory"));
        Files.writeString(target, JSON.writeValueAsString(definition));
    }

    private static void png(Path root, String key, BufferedImage image) throws IOException {
        String[] location = key.split(":", 2);
        Path target = root.resolve("assets/" + location[0] + "/textures/" + location[1]);
        Files.createDirectories(Objects.requireNonNull(target.getParent(), "Generated asset directory"));
        ImageIO.write(image, "PNG", target.toFile());
    }
}
