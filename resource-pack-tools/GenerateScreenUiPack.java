import java.awt.Color;
import java.awt.Font;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import javax.imageio.ImageIO;

/** Generates the prototype's owned font atlas, UI panel and positioning variants. */
public final class GenerateScreenUiPack {

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        Path fontDirectory = Path.of(args[1]);
        Path metricsRoot = Path.of(args[2]);
        Path versionPack = Path.of(args[3]);
        Properties protocol = new Properties();

        try (var input = Files.newInputStream(versionPack.resolve("protocol.properties"))) {
            protocol.load(input);
        }

        Font font = Font.createFont(Font.TRUETYPE_FONT, fontDirectory.resolve("m5x7.ttf").toFile()).deriveFont(16f);

        if (!font.getFamily().equals("m5x7")) {
            throw new IllegalStateException("Unexpected UI font");
        }

        write(root, "FONT-LICENSE.txt", Files.readString(fontDirectory.resolve("LICENSE.txt")));
        write(
            root,
            "pack.mcmeta",
            "{\"pack\":{\"description\":\"VexCore Screen UI (" + protocol.getProperty("minecraft")
                + ")\",\"min_format\":[" + protocol.getProperty("packFormat") + ",0],\"max_format\":["
                + protocol.getProperty("packFormat") + ",0]}}"
        );
        final int cellWidth = 16;
        final int cellHeight = 12;
        BufferedImage atlas = new BufferedImage(32 * cellWidth, 8 * cellHeight, BufferedImage.TYPE_INT_ARGB);
        var graphics = atlas.createGraphics();

        graphics.setFont(font);
        graphics.setColor(Color.WHITE);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        List<String> rows = new ArrayList<>();
        StringBuilder metrics = new StringBuilder("height=12\n32=5\n160=5\n");

        for (int row = 0; row < 7; row++) {
            StringBuilder chars = new StringBuilder("\"");

            for (int col = 0; col < 32; col++) {
                int point = 32 + row * 32 + col;
                boolean visible = point >= 33 && point <= 126 || point >= 161;

                chars.append(String.format("\\u%04x", visible ? point : 0));

                if (!visible) {
                    continue;
                }

                if (!font.canDisplay(point)) {
                    throw new IllegalStateException("Missing glyph " + point);
                }

                var bounds = font.createGlyphVector(graphics.getFontRenderContext(), Character.toString(point))
                    .getPixelBounds(
                        null,
                        0,
                        10
                    );
                int offsetX = Math.max(0, -bounds.x);
                // Windows rasterization can put accented capitals above the nominal baseline.
                // Fit their complete ink inside the existing cell instead of clipping the accent.
                int offsetY = Math.max(0, -bounds.y);

                if (bounds.y + bounds.height + offsetY > cellHeight) {
                    offsetY = cellHeight - bounds.y - bounds.height;
                }

                if (bounds.y + offsetY < 0 || bounds.x + offsetX + bounds.width > cellWidth
                    || bounds.y + bounds.height + offsetY > cellHeight) {
                    throw new IllegalStateException("Clipped glyph " + point);
                }

                graphics.setClip(col * cellWidth, row * cellHeight, cellWidth, cellHeight);
                graphics.drawString(Character.toString(point), col * cellWidth + offsetX, row * cellHeight + 10 + offsetY);
                int inkWidth = 0;

                for (int x = 0; x < cellWidth; x++) {
                    for (int y = 0; y < cellHeight; y++) {
                        if ((atlas.getRGB(col * cellWidth + x, row * cellHeight + y) >>> 24) != 0) {
                            inkWidth = Math.max(inkWidth, x + 1);
                        }
                    }
                }

                metrics.append(point).append('=').append(inkWidth + 1).append('\n');
            }

            rows.add(chars.append('"').toString());
        }

        graphics.setClip(null);
        graphics.fillRect(0, 7 * cellHeight + 2, 8, 8);
        graphics.drawRect(cellWidth, 7 * cellHeight + 2, 7, 7);
        rows.add("\"\\u25a0\\u25a1" + "\\u0000".repeat(30) + "\"");
        metrics.append("9632=9\n9633=9\n");
        graphics.dispose();
        png(root, "assets/vexcore/textures/ui/text.png", atlas);
        write(metricsRoot, "dev/vexsoft/core/paper/service/screenui/m5x7.properties", metrics.toString());
        // Draw one quarter at the final pixel-art resolution, then reflect it on both axes.
        // This keeps corner brackets and side crystals exactly symmetric without resampling.
        BufferedImage quarter = new BufferedImage(70, 24, BufferedImage.TYPE_INT_ARGB);
        var g = quarter.createGraphics();

        g.setColor(new Color(13, 18, 29, 235));
        g.fillRect(1, 1, 69, 23);
        g.setColor(new Color(40, 68, 97, 240));
        g.drawLine(3, 0, 69, 0);
        g.drawLine(0, 3, 0, 23);
        g.setColor(new Color(75, 108, 151, 255));
        g.drawLine(4, 1, 69, 1);
        g.drawLine(1, 4, 1, 23);
        // Stepped stone corner with an inward-facing luminous bracket.
        g.setColor(new Color(124, 178, 204, 255));
        g.drawLine(3, 0, 7, 0);
        g.drawLine(0, 3, 0, 7);
        g.drawLine(0, 3, 3, 0);
        g.setColor(new Color(70, 119, 156, 255));
        g.drawLine(3, 3, 6, 3);
        g.drawLine(3, 3, 3, 6);
        g.setColor(new Color(157, 210, 226, 255));
        g.fillRect(3, 3, 1, 1);
        // A faceted blue monolith crystal at the middle of each vertical edge.
        g.setColor(new Color(43, 78, 112, 255));
        g.fillPolygon(new int[]{1, 3, 3, 0, 0}, new int[]{17, 20, 23, 23, 20}, 5);
        g.setColor(new Color(106, 168, 200, 255));
        g.drawLine(1, 18, 2, 20);
        g.drawLine(2, 20, 2, 23);
        g.setColor(new Color(183, 230, 236, 255));
        g.drawLine(1, 21, 1, 23);
        g.setColor(new Color(65, 104, 141, 130));
        g.drawLine(7, 11, 69, 11);
        g.setColor(new Color(101, 159, 193, 220));
        g.drawLine(67, 0, 69, 2);
        g.dispose();
        BufferedImage panel = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < 48; y++) {
            for (int x = 0; x < 140; x++) {
                panel.setRGB(58 + x, 104 + y, quarter.getRGB(Math.min(x, 139 - x), Math.min(y, 47 - y)));
            }
        }

        png(root, "assets/vexcore/textures/ui/panel.png", panel);
        generateAnchors(root, versionPack, protocol, atlas, panel, rows);

        for (int y = 0; y <= 240; y++) {
            write(
                root,
                "assets/vexcore/font/ui/text/y_" + y + ".json",
                "{\"providers\":[{\"type\":\"space\",\"advances\":{\" \":5,\"\\u00a0\":5}},"
                    + "{\"type\":\"bitmap\",\"file\":\"vexcore:ui/text.png\",\"height\":12,\"ascent\":" + (8 - y)
                    + ",\"chars\":[" + String.join(",", rows) + "]}]}"
            );
            write(
                root,
                "assets/vexcore/font/ui/panel/y_" + y + ".json",
                "{\"providers\":[{\"type\":\"bitmap\",\"file\":\"vexcore:ui/panel.png\",\"height\":512," + "\"ascent\":"
                    + (8 - y + 208) + ",\"chars\":[\"\\ue100\"]}]}"
            );
        }

        List<String> advances = new ArrayList<>();

        for (int bit = 0; bit <= 20; bit++) {
            advances.add(String.format("\"\\u%04x\":%d", 0xE000 + bit, 1 << bit));
            advances.add(String.format("\"\\u%04x\":%d", 0xE020 + bit, -(1 << bit)));
        }

        write(
            root,
            "assets/vexcore/font/ui/space.json",
            "{\"providers\":[{\"type\":\"space\",\"advances\":{" + String.join(",", advances) + "}}]}"
        );
        BufferedImage transparent = new BufferedImage(182, 5, BufferedImage.TYPE_INT_ARGB);

        png(root, "assets/minecraft/textures/gui/sprites/boss_bar/purple_background.png", transparent);
        png(root, "assets/minecraft/textures/gui/sprites/boss_bar/purple_progress.png", transparent);
        System.out.println("Generated VexCore Screen UI pack at " + root.toAbsolutePath());
    }

    private static void generateAnchors(
        Path root,
        Path versionPack,
        Properties protocol,
        BufferedImage atlas,
        BufferedImage panel,
        List<String> rows
    ) throws Exception {
        png(root, "assets/vexcore/textures/ui/anchored_text.png", marked(atlas, 32, 8));
        BufferedImage anchoredPanel = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        var anchoredGraphics = anchoredPanel.createGraphics();

        anchoredGraphics.drawImage(panel, 0, 0, null);
        anchoredGraphics.dispose();
        anchoredPanel.setRGB(0, 0, 0x01565831);
        anchoredPanel.setRGB(0, 1, 0x01434F52);
        png(root, "assets/vexcore/textures/ui/anchored_panel.png", anchoredPanel);
        int minY = Integer.parseInt(protocol.getProperty("minY"));
        int maxY = Integer.parseInt(protocol.getProperty("maxY"));
        int count = maxY - minY + 1;
        int base = Integer.parseInt(protocol.getProperty("base"));
        int stride = Integer.parseInt(protocol.getProperty("stride"));
        int bias = Integer.parseInt(protocol.getProperty("bias"));
        String[] anchors = protocol.getProperty("anchors").split(",");

        for (int kind = 0; kind < 2; kind++) {
            for (int anchor = 0; anchor < anchors.length; anchor++) {
                String name = kind == 0 ? "text" : "panel";

                for (String registered : protocol.getProperty(name + "." + anchors[anchor], "").split(",")) {
                    if (registered.isBlank()) {
                        continue;
                    }

                    int y = Integer.parseInt(registered.trim());

                    if (y < minY || y > maxY) {
                        throw new IllegalArgumentException("Invalid registered UI origin: " + y);
                    }

                    int encoded = base + (kind * anchors.length + anchor) * count + y - minY;
                    int ascent = 8 - (encoded * stride + bias);
                    String spaces = kind == 0 ? "{\"type\":\"space\",\"advances\":{\" \":5,\"\\u00a0\":5}}," : "";

                    write(
                        root,
                        "assets/vexcore/font/ui/" + protocol.getProperty("renderer") + "/" + name + "/"
                            + anchors[anchor] + "/y_" + y + ".json",
                        "{\"providers\":[" + spaces + "{\"type\":\"bitmap\",\"file\":\"vexcore:ui/anchored_" + name
                            + ".png\",\"height\":" + (kind == 0 ? 14 : 512) + ",\"ascent\":" + ascent + ",\"chars\":["
                            + (kind == 0 ? String.join(",", rows) : "\"\\ue100\"") + "]}]}"
                    );
                }
            }
        }

        for (int height : new int[]{20, 48, 64, 80, 96, 128}) {
            int width = height == 20 ? 160 : 240;
            BufferedImage card = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
            var cg = card.createGraphics();

            cg.setColor(new Color(20, 24, 30, 190));
            cg.fillRoundRect(8, 8, width, height, 8, 8);
            cg.setColor(new Color(200, 200, 200, 220));
            cg.drawRoundRect(8, 8, width - 1, height - 1, 8, 8);
            cg.dispose();
            card.setRGB(0, 0, 0x01565831);
            card.setRGB(0, 1, 0x01434F52);
            png(root, "assets/vexcore/textures/ui/card_" + height + ".png", card);

            for (int anchor = 0; anchor < anchors.length; anchor++) {
                extraFont(
                    root,
                    protocol,
                    "card_" + height,
                    anchors[anchor],
                    base + (18 + anchor) * count - minY,
                    "card_" + height,
                    256,
                    "\"\\ue100\"",
                    false
                );
                extraFont(
                    root,
                    protocol,
                    "compact_card_" + height,
                    anchors[anchor],
                    base + (54 + anchor) * count - minY,
                    "card_" + height,
                    256,
                    "\"\\ue100\"",
                    false
                );
            }
        }

        for (int anchor = 0; anchor < anchors.length; anchor++) {
            extraFont(
                root,
                protocol,
                "toast_text",
                anchors[anchor],
                base + (27 + anchor) * count - minY,
                "anchored_text",
                14,
                String.join(",", rows),
                true
            );
            extraFont(
                root,
                protocol,
                "toast_card",
                anchors[anchor],
                base + (36 + anchor) * count - minY,
                "card_20",
                256,
                "\"\\ue100\"",
                false
            );
            extraFont(
                root,
                protocol,
                "compact_text",
                anchors[anchor],
                base + (45 + anchor) * count - minY,
                "anchored_text",
                14,
                String.join(",", rows),
                true
            );
            extraFont(
                root,
                protocol,
                "compact_toast_text",
                anchors[anchor],
                base + (63 + anchor) * count - minY,
                "anchored_text",
                14,
                String.join(",", rows),
                true
            );
            extraFont(
                root,
                protocol,
                "compact_toast_card",
                anchors[anchor],
                base + (72 + anchor) * count - minY,
                "card_20",
                256,
                "\"\\ue100\"",
                false
            );
        }
        // Four additional scale presets reuse the same atlases; no duplicate textures.
        for (int tenth = 6; tenth <= 9; tenth++) {
            int firstKind = 9 + (tenth - 6) * 4;
            String prefix = "scale_" + tenth * 10 + "_";

            for (int anchor = 0; anchor < anchors.length; anchor++) {
                extraFont(
                    root,
                    protocol,
                    prefix + "text",
                    anchors[anchor],
                    base + (firstKind * 9 + anchor) * count - minY,
                    "anchored_text",
                    14,
                    String.join(",", rows),
                    true
                );

                for (int height : new int[]{20, 48, 64, 80, 96, 128}) {
                    extraFont(
                        root,
                        protocol,
                        prefix + "card_" + height,
                        anchors[anchor],
                        base + ((firstKind + 1) * 9 + anchor) * count - minY,
                        "card_" + height,
                        256,
                        "\"\\ue100\"",
                        false
                    );
                }

                extraFont(
                    root,
                    protocol,
                    prefix + "toast_text",
                    anchors[anchor],
                    base + ((firstKind + 2) * 9 + anchor) * count - minY,
                    "anchored_text",
                    14,
                    String.join(",", rows),
                    true
                );
                extraFont(
                    root,
                    protocol,
                    prefix + "toast_card",
                    anchors[anchor],
                    base + ((firstKind + 3) * 9 + anchor) * count - minY,
                    "card_20",
                    256,
                    "\"\\ue100\"",
                    false
                );
            }
        }

        // Bounded motion/fade presets reuse the text atlas, with no extra texture allocation.
        for (int mode = 0; mode < 2; mode++) {
            for (int duration = 0; duration < 4; duration++) {
                for (int tenth = 5; tenth <= 10; tenth++) {
                    int kind = 25 + (mode * 4 + duration) * 6 + tenth - 5;
                    String name = "transition_" + (mode == 0 ? "move" : "fade") + "_" + (2 << duration)
                        + "_" + (tenth * 10);

                    for (int anchor = 0; anchor < anchors.length; anchor++) {
                        extraFont(root, protocol, name, anchors[anchor],
                            base + (kind * 9 + anchor) * count - minY,
                            "anchored_text", 14, String.join(",", rows), true);
                    }
                }
            }
        }

        try (var files = Files.walk(versionPack.resolve("assets"))) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                String content = Files.readString(file).replace("@yCount@", Integer.toString(count));

                for (String key : protocol.stringPropertyNames()) {
                    content = content.replace("@" + key + "@", protocol.getProperty(key));
                }

                write(root, versionPack.relativize(file).toString(), content);
            }
        }

        write(root, "vexcore-screen-ui.properties", Files.readString(versionPack.resolve("protocol.properties")));
    }

    private static void extraFont(
        Path root,
        Properties protocol,
        String kind,
        String anchor,
        int encoded,
        String texture,
        int height,
        String chars,
        boolean spaces
    ) throws Exception {
        int ascent =
            8 - (encoded * Integer.parseInt(protocol.getProperty("stride")) + Integer.parseInt(protocol.getProperty(
                "bias")));

        write(
            root,
            "assets/vexcore/font/ui/" + protocol.getProperty("renderer") + "/" + kind + "/" + anchor + "/y_0.json",
            "{\"providers\":[" + (spaces ? "{\"type\":\"space\",\"advances\":{\" \":5,\"\\u00a0\":5}}," : "")
                + "{\"type\":\"bitmap\",\"file\":\"vexcore:ui/" + texture + ".png\",\"height\":" + height
                + ",\"ascent\":" + ascent + ",\"chars\":[" + chars + "]}]}"
        );
    }

    private static BufferedImage marked(BufferedImage source, int columns, int rows) {
        int width = source.getWidth() / columns;
        int height = source.getHeight() / rows;
        BufferedImage output = new BufferedImage(source.getWidth(), (height + 2) * rows, BufferedImage.TYPE_INT_ARGB);

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < columns; col++) {
                boolean visible = false;

                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int pixel = source.getRGB(col * width + x, row * height + y);

                        visible |= (pixel >>> 24) != 0;
                        output.setRGB(col * width + x, row * (height + 2) + 2 + y, pixel);
                    }
                }

                if (visible) {
                    output.setRGB(col * width, row * (height + 2), 0x01565831);
                    output.setRGB(col * width, row * (height + 2) + 1, 0x01434F52);
                }
            }
        }

        return output;
    }

    private static void write(Path root, String file, String content) throws Exception {
        Path output = root.resolve(file);

        Files.createDirectories(output.getParent());
        Files.writeString(output, content);
    }

    private static void png(Path root, String file, BufferedImage image) throws Exception {
        Path output = root.resolve(file);

        Files.createDirectories(output.getParent());

        if (!ImageIO.write(image, "PNG", output.toFile())) {
            throw new IllegalStateException("PNG writer missing");
        }
    }
}
