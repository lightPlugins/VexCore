package dev.vexsoft.core.vexui.pack;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.awt.AlphaComposite;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;

/** Bakes static GUI previews of JSON item models at build time, never on a server tick. */
public final class VexUIModelPackGenerator implements AutoCloseable {

    private final ObjectMapper json = new ObjectMapper();
    private final List<Path> roots;
    private final ZipFile vanilla;
    private final Map<String, JsonNode> models = new HashMap<>();
    private final Map<String, BufferedImage> images = new HashMap<>();

    /** Opens the pinned client and ordered resource-pack roots used to resolve parents and textures. */
    public VexUIModelPackGenerator(final Path client, final List<Path> roots) throws IOException {
        vanilla = new ZipFile(client.toFile());
        this.roots = List.copyOf(roots);
    }

    /** Generates a pack fragment and matching plugin manifest from item definitions in the supplied roots. */
    public static void main(final String[] args) throws Exception {
        if (args.length < 4) {
            throw new IllegalArgumentException("Expected pack output, manifest output, client JAR and pack roots");
        }
        List<Path> roots = Arrays.stream(args).skip(3).filter(value -> !value.startsWith("--item="))
            .map(Path::of).toList();
        List<String> items = Arrays.stream(args).skip(3).filter(value -> value.startsWith("--item="))
            .map(value -> value.substring(7)).toList();
        try (var generator = new VexUIModelPackGenerator(Path.of(args[2]), roots)) {
            generator.generate(Path.of(args[0]), Path.of(args[1]), items);
        }
    }

    /** Writes reproducible previews and manifests; unsupported dynamic/special item models are listed in a report. */
    public void generate(final Path output, final Path manifest) throws IOException {
        generate(output, manifest, List.of());
    }

    /** Also bakes explicitly requested item definitions resolved from the packs or pinned vanilla client. */
    public void generate(final Path output, final Path manifest, final List<String> items) throws IOException {
        Map<String, JsonNode> definitions = new LinkedHashMap<>();
        for (String item : items) {
            definitions.put(item, json.readTree(bytes(asset(item, "items", ".json"))));
        }
        for (var root : roots) {
            Path assets = root.resolve("assets");
            if (!Files.isDirectory(assets)) {
                continue;
            }
            try (var files = Files.walk(assets)) {
                for (var path : files.filter(Files::isRegularFile).sorted().toList()) {
                    String relative = assets.relativize(path).toString().replace('\\', '/');
                    int separator = relative.indexOf("/items/");
                    if (separator > 0 && relative.endsWith(".json")) {
                        String key = relative.substring(0, separator) + ":"
                            + relative.substring(separator + 7, relative.length() - 5);
                        definitions.put(key, json.readTree(path.toFile()));
                    }
                }
            }
        }
        var entries = new StringBuilder();
        List<String> unsupported = new ArrayList<>();
        int count = 0;
        for (var entry : definitions.entrySet()) {
            String key = entry.getKey();
            BufferedImage image;
            try {
                JsonNode reference = preview(entry.getValue().path("model"), 0);
                image = render(reference.path("model").asText(), 48);
            } catch (UnsupportedOperationException unsupportedModel) {
                unsupported.add(key + ": " + unsupportedModel.getMessage());
                continue;
            }
            String[] parts = key.split(":", 2);
            String texture = parts[0] + ":vexui/models/" + parts[1] + ".png";
            Path png = output.resolve("assets/" + parts[0] + "/textures/vexui/models/" + parts[1] + ".png");
            Files.createDirectories(Objects.requireNonNull(png.getParent()));
            ImageIO.write(image, "PNG", png.toFile());
            RewardCardAssets.icon(output, parts[0] + ":reward/model/" + parts[1], image);
            int ink = inkWidth(image);
            for (int pixels : new int[]{16, 32, 48}) {
                String font = parts[0] + ":vexui/models/" + parts[1] + "/" + pixels;
                for (int offset = 0; offset < 9; offset++) {
                    var provider = json.createObjectNode().put("type", "bitmap").put("file", texture)
                        .put("height", pixels).put("ascent", 7 - offset);
                    provider.putArray("chars").add("\uE001");
                    var definition = json.createObjectNode();
                    definition.putArray("providers").add(provider);
                    write(output.resolve("assets/" + parts[0] + "/font/vexui/models/" + parts[1]
                        + "/" + pixels + "/y_" + offset + ".json"), json.writeValueAsString(definition));
                }
                String prefix = "model." + count++ + ".";
                entries.append(prefix).append("key=").append(key).append('\n')
                    .append(prefix).append("pixels=").append(pixels).append('\n')
                    .append(prefix).append("font=").append(font).append('\n')
                    .append(prefix).append("point=57345\n")
                    .append(prefix).append("advance=").append(Math.round(ink * pixels / 48f) + 1).append('\n');
            }
        }
        write(manifest, "format=1\ncount=" + count + "\n" + entries);
        write(manifest.resolveSibling("vexui-model-unsupported.txt"), String.join("\n", unsupported) + "\n");
        System.out.println("Generated " + count + " VexUI model sizes; " + unsupported.size()
            + " unsupported definitions listed in " + manifest.resolveSibling("vexui-model-unsupported.txt"));
    }

    /** Rasterizes one ordinary or generated item model using its GUI display transform. */
    public BufferedImage render(final String model, final int pixels) throws IOException {
        if (pixels < 1 || pixels > 256) {
            throw new IllegalArgumentException("Preview size must be within 1..256");
        }
        var definition = model(model, 0);
        if (!definition.has("elements")) {
            return generated(definition, pixels);
        }
        int size = pixels * 2;
        var result = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        var depth = new double[size * size];
        Arrays.fill(depth, Double.NEGATIVE_INFINITY);
        var display = definition.path("display").path("gui");
        boolean front = definition.path("gui_light").asText("side").equals("front");
        for (var element : definition.path("elements")) {
            var from = vector(element.path("from"), new double[]{0, 0, 0});
            var to = vector(element.path("to"), new double[]{16, 16, 16});
            for (var face : element.path("faces").properties()) {
                String texture = resolveTexture(definition, face.getValue().path("texture").asText());
                BufferedImage atlas = image(texture);
                double[][] vertices = face(face.getKey(), from, to);
                for (int vertex = 0; vertex < vertices.length; vertex++) {
                    vertices[vertex] = transform(vertices[vertex], element.path("rotation"), display);
                }
                double[] normal = cross(vertices[0], vertices[1], vertices[2]);
                if (normal[2] <= 1e-8) {
                    continue;
                }
                double shade = front || !element.path("shade").asBoolean(true) ? 1
                    : 0.55 + 0.45 * Math.max(0, (normal[0] * -0.2 + normal[1] * 0.8 + normal[2])
                        / (Math.sqrt(normal[0] * normal[0] + normal[1] * normal[1] + normal[2] * normal[2]) * 1.296));
                double[] uv = vector(face.getValue().path("uv"), defaultUv(face.getKey(), from, to));
                double[][] coordinates = {{uv[0], uv[3]}, {uv[2], uv[3]}, {uv[2], uv[1]}, {uv[0], uv[1]}};
                int rotation = Math.floorMod(face.getValue().path("rotation").asInt() / 90, 4);
                for (int triangle = 0; triangle < 2; triangle++) {
                    int[] indices = triangle == 0 ? new int[]{0, 1, 2} : new int[]{0, 2, 3};
                    var points = new double[3][];
                    var mapping = new double[3][];
                    for (int index = 0; index < 3; index++) {
                        var point = vertices[indices[index]];
                        points[index] = new double[]{(8 + point[0]) * size / 16, (8 - point[1]) * size / 16, point[2]};
                        mapping[index] = coordinates[(indices[index] + rotation) % 4];
                    }
                    triangle(result, depth, points, mapping, atlas, shade);
                }
            }
        }
        return scale(result, pixels);
    }

    @Override
    public void close() throws IOException {
        vanilla.close();
    }

    private JsonNode preview(final JsonNode definition, final int depth) {
        if (depth > 32) {
            throw new UnsupportedOperationException("Nested item definition exceeds preview depth");
        }
        // UI snapshots have no held-item/world state: use the declared neutral branch or first animation frame.
        return switch (definition.path("type").asText()) {
            case "minecraft:model" -> definition;
            case "minecraft:select" -> preview(definition.path("fallback"), depth + 1);
            case "minecraft:condition" -> preview(definition.path("on_false"), depth + 1);
            case "minecraft:range_dispatch" -> preview(definition.has("fallback") ? definition.path("fallback")
                : definition.path("entries").path(0).path("model"), depth + 1);
            default -> throw new UnsupportedOperationException("dynamic/special item definition");
        };
    }

    private JsonNode model(final String key, final int depth) throws IOException {
        if (depth > 32) {
            throw new IOException("Cyclic model parent: " + key);
        }
        var cached = models.get(key);
        if (cached != null) {
            return cached;
        }
        var own = json.readTree(bytes(asset(key, "models", ".json")));
        ObjectNode result = json.createObjectNode();
        String parent = own.path("parent").asText();
        if (parent.equals("builtin/entity") || parent.equals("minecraft:builtin/entity")) {
            throw new UnsupportedOperationException("Built-in entity model");
        }
        if (!parent.isEmpty() && !parent.equals("builtin/generated") && !parent.equals("minecraft:builtin/generated")) {
            result = (ObjectNode) model(parent, depth + 1).deepCopy();
        }
        for (var entry : own.properties()) {
            if ((entry.getKey().equals("textures") || entry.getKey().equals("display")) && result.has(entry.getKey())) {
                var merged = (ObjectNode) result.get(entry.getKey()).deepCopy();
                entry.getValue().properties().forEach(value -> merged.set(value.getKey(), value.getValue()));
                result.set(entry.getKey(), merged);
            } else {
                result.set(entry.getKey(), entry.getValue());
            }
        }
        models.put(key, result);
        return result;
    }

    private BufferedImage generated(final JsonNode model, final int pixels) throws IOException {
        var result = new BufferedImage(pixels, pixels, BufferedImage.TYPE_INT_ARGB);
        var graphics = result.createGraphics();
        graphics.setComposite(AlphaComposite.SrcOver);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        boolean any = false;
        for (int layer = 0; layer < 8; layer++) {
            String texture = model.path("textures").path("layer" + layer).asText();
            if (!texture.isEmpty()) {
                BufferedImage image = image(resolveTexture(model, texture));
                graphics.drawImage(image, 0, 0, pixels, pixels, null);
                any = true;
            }
        }
        graphics.dispose();
        if (!any) {
            throw new UnsupportedOperationException("No elements or generated texture layers");
        }
        return result;
    }

    private BufferedImage image(final String texture) throws IOException {
        var cached = images.get(texture);
        if (cached != null) {
            return cached;
        }
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes(asset(texture, "textures", ".png"))));
        if (image == null) {
            throw new IOException("Invalid model texture " + texture);
        }
        if (image.getHeight() > image.getWidth()) {
            image = image.getSubimage(0, 0, image.getWidth(), image.getWidth());
        }
        images.put(texture, image);
        return image;
    }

    private byte[] bytes(final String path) throws IOException {
        for (var root : roots.reversed()) {
            Path file = root.resolve(path).normalize();
            if (!file.startsWith(root.normalize())) {
                throw new IOException("Asset path escapes pack root");
            }
            if (Files.isRegularFile(file)) {
                return Files.readAllBytes(file);
            }
        }
        var entry = vanilla.getEntry(path);
        if (entry == null) {
            throw new IOException("Missing model asset: " + path);
        }
        try (var input = vanilla.getInputStream(entry)) {
            return input.readAllBytes();
        }
    }

    private static String asset(final String key, final String folder, final String suffix) {
        String[] parts = key.contains(":") ? key.split(":", 2) : new String[]{"minecraft", key};
        if (!parts[0].matches("[a-z0-9_.-]+") || !parts[1].matches("[a-z0-9/_.-]+") || parts[1].contains("..")) {
            throw new IllegalArgumentException("Invalid model resource " + key);
        }
        return "assets/" + parts[0] + "/" + folder + "/" + parts[1] + suffix;
    }

    private static String resolveTexture(final JsonNode model, final String reference) {
        String value = reference;
        for (int depth = 0; value.startsWith("#") && depth < 32; depth++) {
            value = model.path("textures").path(value.substring(1)).asText();
        }
        if (value.isEmpty() || value.startsWith("#")) {
            throw new IllegalArgumentException("Unresolved model texture " + reference);
        }
        return value;
    }

    private static double[][] face(final String direction, final double[] a, final double[] b) {
        double x = a[0];
        double y = a[1];
        double z = a[2];
        double u = b[0];
        double v = b[1];
        double w = b[2];
        return switch (direction) {
            case "north" -> new double[][]{{u, y, z}, {x, y, z}, {x, v, z}, {u, v, z}};
            case "south" -> new double[][]{{x, y, w}, {u, y, w}, {u, v, w}, {x, v, w}};
            case "east" -> new double[][]{{u, y, w}, {u, y, z}, {u, v, z}, {u, v, w}};
            case "west" -> new double[][]{{x, y, z}, {x, y, w}, {x, v, w}, {x, v, z}};
            case "up" -> new double[][]{{x, v, w}, {u, v, w}, {u, v, z}, {x, v, z}};
            case "down" -> new double[][]{{x, y, z}, {u, y, z}, {u, y, w}, {x, y, w}};
            default -> throw new IllegalArgumentException("Unknown model face " + direction);
        };
    }

    private static double[] transform(final double[] original, final JsonNode element, final JsonNode gui) {
        double[] point = original.clone();
        if (!element.isMissingNode()) {
            var origin = vector(element.path("origin"), new double[]{8, 8, 8});
            for (int axis = 0; axis < 3; axis++) {
                point[axis] -= origin[axis];
            }
            String axis = element.path("axis").asText("y");
            double angle = Math.toRadians(element.path("angle").asDouble());
            point = rotate(point, axis, angle);
            for (int index = 0; index < 3; index++) {
                if (element.path("rescale").asBoolean() && "xyz".charAt(index) != axis.charAt(0)) {
                    point[index] /= Math.cos(angle);
                }
                point[index] += origin[index];
            }
        }
        var scale = vector(gui.path("scale"), new double[]{1, 1, 1});
        for (int axis = 0; axis < 3; axis++) {
            point[axis] = (point[axis] - 8) * scale[axis];
        }
        var angles = vector(gui.path("rotation"), new double[]{0, 0, 0});
        for (int axis = 2; axis >= 0; axis--) {
            point = rotate(point, Character.toString("xyz".charAt(axis)), Math.toRadians(angles[axis]));
        }
        var translation = vector(gui.path("translation"), new double[]{0, 0, 0});
        for (int axis = 0; axis < 3; axis++) {
            point[axis] += translation[axis];
        }
        return point;
    }

    private static double[] defaultUv(final String direction, final double[] from, final double[] to) {
        return switch (direction) {
            case "down" -> new double[]{from[0], 16 - to[2], to[0], 16 - from[2]};
            case "up" -> new double[]{from[0], from[2], to[0], to[2]};
            case "north" -> new double[]{16 - to[0], 16 - to[1], 16 - from[0], 16 - from[1]};
            case "south" -> new double[]{from[0], 16 - to[1], to[0], 16 - from[1]};
            case "west" -> new double[]{from[2], 16 - to[1], to[2], 16 - from[1]};
            case "east" -> new double[]{16 - to[2], 16 - to[1], 16 - from[2], 16 - from[1]};
            default -> throw new IllegalArgumentException("Unknown model face " + direction);
        };
    }

    private static double[] rotate(final double[] point, final String axis, final double angle) {
        int first = axis.equals("x") ? 1 : axis.equals("y") ? 2 : 0;
        int second = (first + 1) % 3;
        var result = point.clone();
        result[first] = point[first] * Math.cos(angle) - point[second] * Math.sin(angle);
        result[second] = point[first] * Math.sin(angle) + point[second] * Math.cos(angle);
        return result;
    }

    private static double[] vector(final JsonNode node, final double[] fallback) {
        if (!node.isArray()) {
            return fallback;
        }
        return IntStream.range(0, node.size()).mapToDouble(index -> node.get(index).asDouble()).toArray();
    }

    private static double[] cross(final double[] a, final double[] b, final double[] c) {
        double x = b[0] - a[0];
        double y = b[1] - a[1];
        double z = b[2] - a[2];
        double u = c[0] - a[0];
        double v = c[1] - a[1];
        double w = c[2] - a[2];
        return new double[]{y * w - z * v, z * u - x * w, x * v - y * u};
    }

    private static void triangle(final BufferedImage output, final double[] depth, final double[][] p,
                                 final double[][] uv, final BufferedImage texture, final double shade) {
        double area = edge(p[0], p[1], p[2][0], p[2][1]);
        if (Math.abs(area) < 1e-8) {
            return;
        }
        int x0 = Math.max(0, (int) Math.floor(Math.min(p[0][0], Math.min(p[1][0], p[2][0]))));
        int y0 = Math.max(0, (int) Math.floor(Math.min(p[0][1], Math.min(p[1][1], p[2][1]))));
        int x1 = Math.min(output.getWidth() - 1, (int) Math.ceil(Math.max(p[0][0], Math.max(p[1][0], p[2][0]))));
        int y1 = Math.min(output.getHeight() - 1, (int) Math.ceil(Math.max(p[0][1], Math.max(p[1][1], p[2][1]))));
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                double a = edge(p[1], p[2], x + 0.5, y + 0.5) / area;
                double b = edge(p[2], p[0], x + 0.5, y + 0.5) / area;
                double c = 1 - a - b;
                double z = a * p[0][2] + b * p[1][2] + c * p[2][2];
                int slot = y * output.getWidth() + x;
                if (a < -1e-8 || b < -1e-8 || c < -1e-8 || z < depth[slot]) {
                    continue;
                }
                double u = a * uv[0][0] + b * uv[1][0] + c * uv[2][0];
                double v = a * uv[0][1] + b * uv[1][1] + c * uv[2][1];
                int pixel = texture.getRGB(Math.clamp((int) (u * texture.getWidth() / 16), 0, texture.getWidth() - 1),
                    Math.clamp((int) (v * texture.getHeight() / 16), 0, texture.getHeight() - 1));
                if ((pixel >>> 24) < 128) {
                    continue;
                }
                int red = (int) (((pixel >>> 16) & 255) * shade);
                int green = (int) (((pixel >>> 8) & 255) * shade);
                int blue = (int) ((pixel & 255) * shade);
                output.setRGB(x, y, 0xFF000000 | red << 16 | green << 8 | blue);
                depth[slot] = z;
            }
        }
    }

    private static double edge(final double[] a, final double[] b, final double x, final double y) {
        return (x - a[0]) * (b[1] - a[1]) - (y - a[1]) * (b[0] - a[0]);
    }

    private static int inkWidth(final BufferedImage image) {
        int width = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) != 0) {
                    width = Math.max(width, x + 1);
                }
            }
        }
        return width;
    }

    private static BufferedImage scale(final BufferedImage source, final int pixels) {
        var output = new BufferedImage(pixels, pixels, BufferedImage.TYPE_INT_ARGB);
        var graphics = output.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(source, 0, 0, pixels, pixels, null);
        graphics.dispose();
        return output;
    }

    private static void write(final Path path, final String value) throws IOException {
        Files.createDirectories(Objects.requireNonNull(path.toAbsolutePath().getParent()));
        Files.writeString(path, value, StandardCharsets.UTF_8);
    }
}
