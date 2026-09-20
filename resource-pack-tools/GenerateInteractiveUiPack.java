import java.awt.Color;
import java.awt.Font;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/** Builds the independent interactive UI pack without modifying the existing screen UI pack. */
public final class GenerateInteractiveUiPack {

    private static final int SIGNATURE_A = 0x01564955;
    private static final int SIGNATURE_B = 0x01434F52;

    public static void main(String[] arguments) throws Exception {
        Path output = Path.of(arguments[0]);
        Path source = Path.of(arguments[1]);
        Path fonts = Path.of(arguments[2]);
        Path screenPack = Path.of(arguments[3]);
        Files.createDirectories(output);
        write(output, "pack.mcmeta", "{\"pack\":{\"description\":\"VexCore Interactive UI - Minecraft 26.2\","
            + "\"min_format\":[88,0],\"max_format\":[88,0]}}\n");
        write(output, "FONT-LICENSE.txt", Files.readString(fonts.resolve("LICENSE.txt")));
        write(output, "vexcore-interactive-ui.properties", Files.readString(source.resolve("protocol.properties")));
        copyTree(source.resolve("assets"), output.resolve("assets"));
        Font font = Font.createFont(Font.TRUETYPE_FONT, fonts.resolve("m5x7.ttf").toFile()).deriveFont(12f);
        generateText(output, font);
        generateRectangles(output);
        generateCursor(output);
        generateSpaces(output);
        generateShaders(output, screenPack);
        BufferedImage transparent = new BufferedImage(182, 5, BufferedImage.TYPE_INT_ARGB);
        png(output, "assets/minecraft/textures/gui/sprites/boss_bar/purple_background.png", transparent);
        png(output, "assets/minecraft/textures/gui/sprites/boss_bar/purple_progress.png", transparent);
        System.out.println("Generated independent interactive UI pack at " + output.toAbsolutePath());
    }

    private static void generateText(Path output, Font font) throws Exception {
        BufferedImage atlas = new BufferedImage(16 * 6, 14 * 10, BufferedImage.TYPE_INT_ARGB);
        List<String> rows = new ArrayList<>();
        for (int row = 0; row < 14; row++) {
            StringBuilder characters = new StringBuilder("\"");
            for (int column = 0; column < 16; column++) {
                int point = 32 + row * 16 + column;
                characters.append(String.format("\\u%04x", point));
                int x = column * 6;
                int y = row * 10;
                BufferedImage letter = new BufferedImage(16, 12, BufferedImage.TYPE_INT_ARGB);
                var graphics = letter.createGraphics();
                graphics.setFont(font);
                graphics.setColor(Color.WHITE);
                graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
                var bounds = font.createGlyphVector(graphics.getFontRenderContext(), Character.toString(point))
                    .getPixelBounds(null, 0, 7);
                if (point >= 33 && point <= 126 || point >= 161) {
                    graphics.drawString(Character.toString(point), Math.max(0, -bounds.x), 7 - Math.min(0, bounds.y));
                }
                graphics.dispose();
                int width = Math.max(1, bounds.width);
                int height = Math.min(8, Math.max(1, bounds.y + bounds.height));
                var destination = atlas.createGraphics();
                destination.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                destination.drawImage(letter, x, y + 2, x + Math.min(5, width), y + 2 + height,
                    0, 0, width, height, null);
                destination.dispose();
                mark(atlas, x, y, 6);
            }
            rows.add(characters.append('"').toString());
        }
        png(output, "assets/vexcore/textures/interactive_ui/text.png", atlas);
        font(output, "text", "text", 10, 0, rows);
    }

    private static void generateRectangles(Path output) throws Exception {
        BufferedImage atlas = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        List<String> rows = new ArrayList<>();
        for (int row = 0; row < 64; row++) {
            StringBuilder characters = new StringBuilder("\"");
            for (int column = 0; column < 64; column++) {
                int index = row * 64 + column;
                characters.append(String.format("\\u%04x", 0xE000 + index));
                int color = 0xFF000000 | ((index >> 8) & 15) * 17 << 16
                    | ((index >> 4) & 15) * 17 << 8 | (index & 15) * 17;
                mark(atlas, column * 4, row * 4, 4);
                atlas.setRGB(column * 4 + 1, row * 4, color);
            }
            rows.add(characters.append('"').toString());
        }
        png(output, "assets/vexcore/textures/interactive_ui/rectangle.png", atlas);
        font(output, "rectangle", "rectangle", 4, 1, rows);
    }

    private static void generateCursor(Path output) throws Exception {
        BufferedImage cursor = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        mark(cursor, 0, 0, 4);
        png(output, "assets/vexcore/textures/interactive_ui/cursor.png", cursor);
        font(output, "cursor", "cursor", 4, 2, List.of("\"\\ue000\""));
    }

    private static void generateSpaces(Path output) throws Exception {
        List<String> advances = new ArrayList<>();
        for (int bit = 0; bit <= 21; bit++) {
            double advance = (1 << bit) / 4.0;
            advances.add("\"" + String.format("\\u%04x", 0xE000 + bit) + "\":" + advance);
            advances.add("\"" + String.format("\\u%04x", 0xE020 + bit) + "\":" + -advance);
        }
        write(output, "assets/vexcore/font/interactive_ui/space.json",
            "{\"providers\":[{\"type\":\"space\",\"advances\":{" + String.join(",", advances) + "}}]}\n");
    }

    private static void generateShaders(Path output, Path screenPack) throws Exception {
        String vertex = Files.readString(screenPack.resolve("assets/minecraft/shaders/core/text.vsh"));
        vertex = replaceOnce(vertex, "#moj_import <vexcore:screen_ui/v26_2.glsl>",
            "#moj_import <vexcore:screen_ui/v26_2.glsl>\n#moj_import <vexcore:interactive_ui/v26_2.glsl>");
        vertex = replaceOnce(vertex, "position = vex_screen_ui_position(position, UV0, gl_VertexID);",
            "position = interactive_ui_position(position, UV0, gl_VertexID);\n"
                + "    if (interactiveKind < 0) position = vex_screen_ui_position(position, UV0, gl_VertexID);");
        write(output, "assets/minecraft/shaders/core/text.vsh", vertex);
        String fragment = Files.readString(screenPack.resolve("assets/minecraft/shaders/core/text.fsh"));
        fragment = replaceOnce(fragment, "flat in float vexUi;",
            "flat in float vexUi;\n#moj_import <vexcore:interactive_ui/fragment.glsl>");
        fragment = replaceOnce(fragment, "void main() {", "void main() {\n"
            + "#if defined(IS_GUI) && !defined(IS_GRAYSCALE)\n"
            + "    if (interactiveKind >= 1) {\n"
            + "        fragColor = interactive_ui_fragment();\n"
            + "        return;\n"
            + "    }\n"
            + "#endif");
        write(output, "assets/minecraft/shaders/core/text.fsh", fragment);
        write(output, "assets/vexcore/shaders/include/screen_ui/v26_2.glsl",
            Files.readString(screenPack.resolve("assets/vexcore/shaders/include/screen_ui/v26_2.glsl")));
    }

    private static String replaceOnce(String source, String expected, String replacement) {
        int index = source.indexOf(expected);
        if (index < 0 || source.indexOf(expected, index + expected.length()) >= 0) {
            throw new IllegalStateException("The existing screen shader changed; review the compatibility dispatcher");
        }
        return source.substring(0, index) + replacement + source.substring(index + expected.length());
    }

    private static void font(Path output, String name, String texture, int height, int kind, List<String> rows)
        throws Exception {
        int ascent = 8 - ((512 + kind) * 4096 + 512);
        write(output, "assets/vexcore/font/interactive_ui/" + name + ".json",
            "{\"providers\":[{\"type\":\"bitmap\",\"file\":\"vexcore:interactive_ui/" + texture
                + ".png\",\"height\":" + height + ",\"ascent\":" + ascent
                + ",\"chars\":[" + String.join(",", rows) + "]}]}\n");
    }

    private static void mark(BufferedImage image, int x, int y, int width) {
        image.setRGB(x, y, SIGNATURE_A);
        image.setRGB(x, y + 1, SIGNATURE_B);
        // Nonzero alpha at the last column fixes the exact Minecraft advance to cell width + one.
        image.setRGB(x + width - 1, y, 0x01000000);
    }

    private static void copyTree(Path source, Path destination) throws Exception {
        try (var files = Files.walk(source)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                Path target = destination.resolve(source.relativize(file));
                Files.createDirectories(target.getParent());
                Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static void png(Path root, String relativePath, BufferedImage image) throws Exception {
        Path target = root.resolve(relativePath);
        Files.createDirectories(target.getParent());
        ImageIO.write(image, "png", target.toFile());
    }

    private static void write(Path root, String relativePath, String content) throws Exception {
        Path target = root.resolve(relativePath);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content);
    }
}
