package dev.vexsoft.core.vexui.pack;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;

/** Adds bounded Unicode symbol coverage from the pinned, licensed vanilla Unifont fallback. */
public final class VexUIUnicodeFont {

    private VexUIUnicodeFont() {
    }

    /** Emits missing symbols, mathematical alphabets and emoji as positioned bitmap pages with matching advances. */
    public static List<JsonNode> generate(final Path root, final Path source, final Set<Integer> existing,
                                         final StringBuilder metrics) throws IOException {
        List<Glyph> glyphs = new ArrayList<>();
        try (var zip = new ZipFile(source.toFile())) {
            var hex = zip.stream().filter(entry -> entry.getName().endsWith(".hex")).findFirst().orElseThrow();
            try (var reader = new BufferedReader(new InputStreamReader(zip.getInputStream(hex), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    int separator = line.indexOf(':');
                    if (separator < 0) {
                        continue;
                    }
                    int point = Integer.parseInt(line.substring(0, separator), 16);
                    if (existing.contains(point) || !supported(point)) {
                        continue;
                    }
                    byte[] bits = HexFormat.of().parseHex(line.substring(separator + 1));
                    int width = bits.length / 16 * 8;
                    int left = width;
                    int right = -1;
                    for (int y = 0; y < 16; y++) {
                        for (int x = 0; x < width; x++) {
                            if (filled(bits, width, x, y)) {
                                left = Math.min(left, x);
                                right = Math.max(right, x);
                            }
                        }
                    }
                    if (right >= left) {
                        glyphs.add(new Glyph(point, bits, width, left, right));
                    }
                }
            }
            try (var license = zip.getInputStream(zip.getEntry("LICENSE.txt"))) {
                Files.write(root.resolve("VEXUI-UNICODE-LICENSE.txt"), license.readAllBytes());
            }
        }
        List<JsonNode> providers = new ArrayList<>();
        var json = new ObjectMapper();
        for (int start = 0; start < glyphs.size(); start += 256) {
            var atlas = new BufferedImage(512, 256, BufferedImage.TYPE_INT_ARGB);
            var chars = json.createArrayNode();
            for (int row = 0; row < 16; row++) {
                var characters = new StringBuilder();
                for (int column = 0; column < 16; column++) {
                    int index = start + row * 16 + column;
                    if (index >= glyphs.size()) {
                        characters.append('\0');
                        continue;
                    }
                    var glyph = glyphs.get(index);
                    characters.appendCodePoint(glyph.point());
                    for (int y = 0; y < 16; y++) {
                        for (int x = glyph.left(); x <= glyph.right(); x++) {
                            if (filled(glyph.bits(), glyph.width(), x, y)) {
                                atlas.setRGB(column * 32 + x - glyph.left(), row * 16 + y, 0xFFFFFFFF);
                            }
                        }
                    }
                    metrics.append("text.").append(glyph.point()).append('=')
                        .append(Math.round((glyph.right() - glyph.left() + 1) / 2f) + 1).append('\n');
                }
                chars.add(characters.toString());
            }
            String texture = "vexui/unicode/page_" + (start / 256) + ".png";
            Path file = root.resolve("assets/vexcore/textures/" + texture);
            Files.createDirectories(Objects.requireNonNull(file.getParent()));
            ImageIO.write(atlas, "PNG", file.toFile());
            var provider = json.createObjectNode().put("type", "bitmap").put("file", "vexcore:" + texture)
                .put("height", 8).put("ascent", 7);
            provider.set("chars", chars);
            providers.add(provider);
        }
        return providers;
    }

    private static boolean supported(final int point) {
        return point >= 0xA0 && point <= 0x2FFF || point >= 0x1D400 && point <= 0x1D7FF
            || point >= 0x1F000 && point <= 0x1FAFF;
    }

    private static boolean filled(final byte[] bits, final int width, final int x, final int y) {
        return (bits[y * (width / 8) + x / 8] & (1 << (7 - x % 8))) != 0;
    }

    private record Glyph(int point, byte[] bits, int width, int left, int right) {
    }
}
