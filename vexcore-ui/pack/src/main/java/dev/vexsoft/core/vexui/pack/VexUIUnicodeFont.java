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
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;

/** Adds bounded Unicode symbol coverage from the pinned, licensed vanilla Unifont fallback. */
public final class VexUIUnicodeFont {

    private static final int CELL_HEIGHT = 16;
    private static final int PAGE_COLUMNS = 16;
    private static final int PAGE_SIZE = PAGE_COLUMNS * 16;

    private VexUIUnicodeFont() {
    }

    /** Emits missing symbols, mathematical alphabets and emoji as positioned bitmap pages with matching advances. */
    public static List<JsonNode> generate(final Path root, final Path source, final Set<Integer> existing,
                                         final StringBuilder metrics) throws IOException {
        var glyphsByWidth = new TreeMap<Integer, List<Glyph>>();
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
                    int width = bits.length / CELL_HEIGHT * 8;
                    int left = width;
                    int right = -1;
                    for (int y = 0; y < CELL_HEIGHT; y++) {
                        for (int x = 0; x < width; x++) {
                            if (filled(bits, width, x, y)) {
                                left = Math.min(left, x);
                                right = Math.max(right, x);
                            }
                        }
                    }
                    if (right >= left) {
                        int inkWidth = right - left + 1;
                        // An even width keeps the original 2:1 downsampling in reward glyphs.
                        int cellWidth = (inkWidth + 1) & ~1;
                        glyphsByWidth.computeIfAbsent(cellWidth, ignored -> new ArrayList<>())
                            .add(new Glyph(point, bits, width, left, right));
                        metrics.append("text.").append(point).append('=')
                            .append(Math.round(inkWidth / 2f) + 1).append('\n');
                    }
                }
            }
            try (var license = zip.getInputStream(zip.getEntry("LICENSE.txt"))) {
                Files.write(root.resolve("VEXUI-UNICODE-LICENSE.txt"), license.readAllBytes());
            }
        }
        List<JsonNode> providers = new ArrayList<>();
        var json = new ObjectMapper();
        for (var entry : glyphsByWidth.entrySet()) {
            var glyphs = entry.getValue();
            for (int start = 0; start < glyphs.size(); start += PAGE_SIZE) {
                int end = Math.min(start + PAGE_SIZE, glyphs.size());
                providers.add(page(root, json, glyphs.subList(start, end), entry.getKey(), providers.size()));
            }
        }
        return providers;
    }

    private static JsonNode page(
        final Path root,
        final ObjectMapper json,
        final List<Glyph> glyphs,
        final int cellWidth,
        final int page
    ) throws IOException {
        int columns = Math.min(PAGE_COLUMNS, glyphs.size());
        int rows = Math.ceilDiv(glyphs.size(), columns);
        var atlas = new BufferedImage(columns * cellWidth, rows * CELL_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        var chars = json.createArrayNode();
        for (int row = 0; row < rows; row++) {
            var characters = new StringBuilder();
            for (int column = 0; column < columns; column++) {
                int index = row * columns + column;
                if (index >= glyphs.size()) {
                    characters.append('\0');
                    continue;
                }
                var glyph = glyphs.get(index);
                characters.appendCodePoint(glyph.point());
                for (int y = 0; y < CELL_HEIGHT; y++) {
                    for (int x = glyph.left(); x <= glyph.right(); x++) {
                        if (filled(glyph.bits(), glyph.width(), x, y)) {
                            atlas.setRGB(column * cellWidth + x - glyph.left(), row * CELL_HEIGHT + y, 0xFFFFFFFF);
                        }
                    }
                }
            }
            chars.add(characters.toString());
        }
        String texture = "vexui/unicode/page_" + page + ".png";
        Path file = root.resolve("assets/vexcore/textures/" + texture);
        Files.createDirectories(file.getParent());
        ImageIO.write(atlas, "PNG", file.toFile());
        var provider = json.createObjectNode().put("type", "bitmap").put("file", "vexcore:" + texture)
            .put("height", 8).put("ascent", 7);
        provider.set("chars", chars);
        return provider;
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
