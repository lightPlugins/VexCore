package dev.vexsoft.core.vexui.pack;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.TreeMap;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;

/** Generates format-3 VexUI glyphs and matching metrics with vanilla text, Unicode symbols and item textures. */
public final class VexUIPackGenerator {

    private static final int[] WIDTHS = {1, 2, 4, 8, 16, 32, 64, 128, 256};
    private static final int[] HIT_WIDTHS = {2, 3, 4, 8, 16, 32, 64, 128, 256};
    private static final List<String> ICONS = List.of(
        "potato", "carrot", "wheat", "sugar_cane", "beetroot", "apple", "bread", "melon_slice",
        "pumpkin_pie", "sweet_berries", "cookie", "golden_carrot", "diamond", "emerald", "iron_ingot",
        "gold_ingot", "copper_ingot", "coal", "redstone", "lapis_lazuli", "amethyst_shard", "quartz",
        "flint", "netherite_ingot", "iron_sword", "diamond_sword", "iron_pickaxe", "diamond_pickaxe",
        "iron_axe", "diamond_axe", "iron_shovel", "diamond_shovel", "iron_hoe", "diamond_hoe",
        "shears", "fishing_rod", "feather", "bone", "string", "rotten_flesh", "spider_eye", "leather",
        "paper", "book", "name_tag", "stick", "blaze_rod", "ender_pearl", "raw_iron", "raw_gold", "raw_copper",
        "wheat_seeds", "ender_eye", "enchanted_book", "experience_bottle", "map", "knowledge_book", "baked_potato",
        "gold_nugget", "iron_nugget", "blaze_powder", "gunpowder", "glass_bottle", "barrier", "compass_16"
    );

    private VexUIPackGenerator() {
    }

    /** Writes deterministic resource-pack assets and runtime measurements to the requested directories. */
    public static void main(final String[] args) throws Exception {
        if (args.length != 4 && args.length != 5) {
            throw new IllegalArgumentException("Expected pack, metrics, client, Unifont and optional German translations");
        }
        generate(Path.of(args[0]), Path.of(args[1]), Path.of(args[2]), Path.of(args[3]),
            args.length == 5 ? Path.of(args[4]) : null);
    }

    /** Generates assets for Minecraft 26.2 using its original default-font bitmaps and built-in icon PNGs. */
    public static void generate(final Path root, final Path metricsRoot, final Path client, final Path unicode)
        throws Exception {
        generate(root, metricsRoot, client, unicode, null);
    }

    /** Also bundles server-side vanilla dictionaries for measuring client-translatable item names. */
    public static void generate(
        final Path root, final Path metricsRoot, final Path client, final Path unicode, final Path german
    ) throws Exception {
        translations(metricsRoot, client, german);
        uiSounds(root);
        write(root, "pack.mcmeta", "{\"pack\":{\"description\":\"VexUI - muted purple / format 3 / 26.2\","
            + "\"min_format\":[88,0],\"max_format\":[88,0]}}\n");
        StringBuilder metrics = new StringBuilder("format=3\ntext.height=12\ntext.source=minecraft:default\n");
        List<String> textFonts = text(root, client, unicode, metrics);
        List<String> tileProviders = tiles(root, metrics);
        List<String> iconProviders = icons(root, metrics, client);
        // All backgrounds share the first text baseline, preserving painter order across multiline foregrounds.
        // White tiles are tinted at runtime, so these fonts share only 27 tiny source images.
        for (int y = 0; y < 900; y++) {
            String tileJson = String.join(",", tileProviders).replace("ASCENT", Integer.toString(7 - y));
            write(root, "assets/vexcore/font/vexui/tiles/y_" + y + ".json",
                "{\"providers\":[" + tileJson + "]}\n");
        }
        for (int offset = 0; offset < 9; offset++) {
            write(root, "assets/vexcore/font/vexui/icons/y_" + offset + ".json",
                "{\"providers\":[" + String.join(",", iconProviders)
                    .replace("ASCENT", Integer.toString(7 - offset)) + "]}\n");
            write(root, "assets/vexcore/font/vexui/text/y_" + offset + ".json",
                textFonts.get(offset));
        }
        List<String> shifts = new ArrayList<>();
        for (int power = 0; power <= 11; power++) {
            shifts.add(quoted(0xE000 + power) + ":" + (1 << power));
            shifts.add(quoted(0xE020 + power) + ":" + -(1 << power));
        }
        write(root, "assets/vexcore/font/vexui/space.json",
            "{\"providers\":[{\"type\":\"space\",\"advances\":{" + String.join(",", shifts) + "}}]}\n");
        write(metricsRoot, "dev/vexsoft/core/paper/vexui/metrics.properties", metrics.toString());
        write(root, "vexui-manifest.properties", metrics.toString());
    }

    private static void uiSounds(final Path root) throws IOException {
        for (String name : List.of("collect", "pick-up", "level-up", "upgrade-success", "upgrade-failed")) {
            String resource = "assets/vexcore/sounds/ui/" + name + ".ogg";
            Path target = root.resolve(resource);
            Files.createDirectories(target.getParent());
            try (var input = VexUIPackGenerator.class.getResourceAsStream("/pack/" + resource)) {
                if (input == null) {
                    throw new IOException("Missing shared UI sound: " + name);
                }
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        write(root, "assets/vexcore/sounds.json",
            "{\"ui.collect\":{\"sounds\":[{\"name\":\"vexcore:ui/collect\",\"stream\":false}]},"
                + "\"ui.pick-up\":{\"sounds\":[{\"name\":\"vexcore:ui/pick-up\",\"stream\":false}]},"
                + "\"ui.level-up\":{\"sounds\":[{\"name\":\"vexcore:ui/level-up\",\"stream\":false}]},"
                + "\"ui.upgrade-success\":{\"sounds\":[{\"name\":\"vexcore:ui/upgrade-success\",\"stream\":false}]},"
                + "\"ui.upgrade-failed\":{\"sounds\":[{\"name\":\"vexcore:ui/upgrade-failed\",\"stream\":false}]}}\n");
    }

    private static List<String> text(final Path root, final Path client, final Path unicode, final StringBuilder metrics)
        throws IOException {
        var json = new ObjectMapper();
        List<JsonNode> providers = new ArrayList<>();
        var advances = new LinkedHashMap<Integer, Integer>();
        advances.put(32, 4);
        advances.put(160, 4);
        advances.put(0x200C, 0);
        try (var zip = new ZipFile(client.toFile());
             var input = zip.getInputStream(zip.getEntry("assets/minecraft/font/include/default.json"))) {
            for (var provider : json.readTree(input).get("providers")) {
                if (!provider.path("type").asText().equals("bitmap")) {
                    throw new IOException("Unexpected vanilla default-font provider");
                }
                providers.add(provider);
                String texture = "assets/minecraft/textures/" + provider.get("file").asText().substring("minecraft:".length());
                BufferedImage atlas;
                try (var image = zip.getInputStream(zip.getEntry(texture))) {
                    atlas = ImageIO.read(image);
                }
                var rows = provider.get("chars");
                int columns = rows.get(0).asText().codePointCount(0, rows.get(0).asText().length());
                int cellWidth = atlas.getWidth() / columns;
                int cellHeight = atlas.getHeight() / rows.size();
                float scale = provider.path("height").asInt(8) / (float) cellHeight;
                for (int row = 0; row < rows.size(); row++) {
                    int[] points = rows.get(row).asText().codePoints().toArray();
                    for (int column = 0; column < points.length; column++) {
                        if (points[column] == 0) {
                            continue;
                        }
                        int ink = 0;
                        for (int x = 0; x < cellWidth; x++) {
                            for (int y = 0; y < cellHeight; y++) {
                                if ((atlas.getRGB(column * cellWidth + x, row * cellHeight + y) >>> 24) != 0) {
                                    ink = Math.max(ink, x + 1);
                                }
                            }
                        }
                        advances.putIfAbsent(points[column], (int) (0.5 + ink * scale) + 1);
                    }
                }
            }
        }
        advances.forEach((point, advance) -> metrics.append("text.").append(point).append('=').append(advance).append('\n'));
        providers.addAll(VexUIUnicodeFont.generate(root, unicode, advances.keySet(), metrics));
        RewardCardAssets.text(root, client, providers);
        List<String> fonts = new ArrayList<>();
        for (int offset = 0; offset < 9; offset++) {
            List<String> shifted = new ArrayList<>();
            shifted.add("{\"type\":\"space\",\"advances\":{\" \":4,\"\\u00a0\":4,\"\\u200c\":0}}");
            for (var provider : providers) {
                ObjectNode copy = provider.deepCopy();
                copy.put("height", provider.path("height").asInt(8));
                // Retain vanilla glyph shapes and relative baselines; two pixels reserve room for upper accents.
                copy.put("ascent", provider.get("ascent").asInt() - 2 - offset);
                shifted.add(json.writeValueAsString(copy));
            }
            fonts.add("{\"providers\":[" + String.join(",", shifted) + "]}\n");
        }
        return fonts;
    }

    private static List<String> tiles(final Path root, final StringBuilder metrics) throws IOException {
        List<String> providers = new ArrayList<>();
        int point = 0xE100;
        for (int kind = 0; kind < 3; kind++) {
            int[] widths = kind == 2 ? HIT_WIDTHS : WIDTHS;
            for (int width : widths) {
                int imageWidth = kind == 2 ? width - 1 : width;
                BufferedImage image = new BufferedImage(imageWidth, 9, BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < (kind == 1 ? 1 : 9); y++) {
                    for (int x = 0; x < imageWidth; x++) {
                        image.setRGB(x, y, 0xFFFFFFFF);
                    }
                }
                String id = kind + "_" + width;
                png(root, "assets/vexcore/textures/vexui/tiles/" + id + ".png", image);
                providers.add(bitmap("vexcore:vexui/tiles/" + id + ".png", 9, point));
                metrics.append("tile.0_").append(id).append('=').append(point).append('\n');
                point++;
            }
        }
        return providers;
    }

    private static List<String> icons(final Path root, final StringBuilder metrics, final Path client) throws IOException {
        List<String> providers = new ArrayList<>();
        int point = 0xE800;
        try (var zip = new ZipFile(client.toFile())) {
            for (String icon : ICONS) {
                var entry = zip.getEntry("assets/minecraft/textures/item/" + icon + ".png");
                if (entry == null) {
                    throw new IOException("Missing vanilla icon " + icon);
                }
                BufferedImage image;
                try (var input = zip.getInputStream(entry)) {
                    image = ImageIO.read(input);
                }
                RewardCardAssets.icon(root, "vexcore:reward/icon/vanilla/" + icon, image);
                int ink = 0;
                for (int x = 0; x < image.getWidth(); x++) {
                    for (int y = 0; y < image.getHeight(); y++) {
                        if ((image.getRGB(x, y) >>> 24) != 0) {
                            ink = Math.max(ink, x + 1);
                        }
                    }
                }
                for (int size : new int[]{16, 32, 48}) {
                    providers.add(bitmap("minecraft:item/" + icon + ".png", size, point));
                    String key = "icon.minecraft\\:item/" + icon + "." + size;
                    metrics.append(key).append('=').append(point).append('\n');
                    metrics.append(key).append(".advance=").append((int) (0.5 + ink * size / (float) image.getHeight()) + 1)
                        .append('\n');
                    point++;
                }
            }
        }
        return providers;
    }

    private static String bitmap(final String file, final int height, final int point) {
        return "{\"type\":\"bitmap\",\"file\":\"" + file + "\",\"height\":" + height
            + ",\"ascent\":ASCENT,\"chars\":[" + quoted(point) + "]}";
    }

    private static void translations(final Path metricsRoot, final Path client, final Path german) throws IOException {
        var json = new ObjectMapper();
        try (var vanilla = new ZipFile(client.toFile())) {
            byte[] english = vanilla.getInputStream(vanilla.getEntry("assets/minecraft/lang/en_us.json")).readAllBytes();
            for (String locale : List.of("en_us", "de_de")) {
                byte[] bytes = locale.equals("de_de") && german != null ? Files.readAllBytes(german) : english;
                var entries = new TreeMap<String, String>();
                json.readTree(bytes).properties().forEach(entry -> entries.put(entry.getKey(), entry.getValue().asText()));
                var properties = new StringBuilder();
                entries.forEach((key, value) -> properties.append(property(key)).append('=')
                    .append(property(value)).append('\n'));
                write(metricsRoot, "dev/vexsoft/core/paper/vexui/runtime/vanilla/" + locale + ".properties",
                    properties.toString());
            }
        }
    }

    private static String property(final String value) {
        String escaped = value.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r")
            .replace("\t", "\\t").replace("=", "\\=").replace(":", "\\:");
        return escaped.startsWith(" ") ? "\\" + escaped : escaped;
    }

    private static String quoted(final int point) {
        return "\"" + escape(point) + "\"";
    }

    private static String escape(final int point) {
        return String.format(Locale.ROOT, "\\u%04x", point);
    }

    private static void png(final Path root, final String name, final BufferedImage image) throws IOException {
        Path file = root.resolve(name);
        Files.createDirectories(Objects.requireNonNull(file.toAbsolutePath().getParent()));
        ImageIO.write(image, "PNG", file.toFile());
    }

    private static void write(final Path root, final String name, final String value) throws IOException {
        Path file = root.resolve(name);
        Files.createDirectories(Objects.requireNonNull(file.toAbsolutePath().getParent()));
        Files.writeString(file, value, StandardCharsets.UTF_8);
    }
}
