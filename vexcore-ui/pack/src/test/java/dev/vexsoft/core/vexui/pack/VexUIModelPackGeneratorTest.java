package dev.vexsoft.core.vexui.pack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.stream.IntStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercises inherited vanilla cube geometry, generated layers, special-model reporting and manifest metrics. */
public final class VexUIModelPackGeneratorTest {

    @TempDir
    Path temporary;

    @Test
    public void neutralVanillaEquipmentAndAnimatedItemsResolveTheirActualModel() throws Exception {
        Path output = temporary.resolve("pack");
        Path manifest = temporary.resolve("plugin/models.properties");
        try (var generator = new VexUIModelPackGenerator(Path.of(System.getProperty("vexui.client")), List.of())) {
            generator.generate(output, manifest,
                List.of("minecraft:iron_chestplate", "minecraft:fishing_rod", "minecraft:compass"));
        }
        var properties = new Properties();
        try (var input = Files.newInputStream(manifest)) {
            properties.load(input);
        }
        assertEquals("9", properties.getProperty("count"));
        assertTrue(Files.readString(manifest.resolveSibling("vexui-model-unsupported.txt")).isBlank());
        for (String item : List.of("iron_chestplate", "fishing_rod", "compass")) {
            var image = ImageIO.read(output.resolve("assets/minecraft/textures/vexui/models/" + item + ".png").toFile());
            assertTrue(IntStream.range(0, 48).anyMatch(y ->
                IntStream.range(0, 48).anyMatch(x -> (image.getRGB(x, y) >>> 24) > 0)));
        }
    }

    @Test
    public void resolvesInheritedModelsAndBakesNestedPreviewsAtAllSupportedSizes() throws Exception {
        Path definitions = temporary.resolve("source/assets/test/items");
        Files.createDirectories(definitions);
        Files.writeString(definitions.resolve("block.json"),
            """
            {"model":{"type":"minecraft:model","model":"minecraft:block/stone"}}
            """);
        Files.writeString(definitions.resolve("flat.json"),
            """
            {"model":{"type":"minecraft:model","model":"minecraft:item/potato"}}
            """);
        Files.writeString(definitions.resolve("dynamic.json"),
            """
            {"model":{"type":"minecraft:select"}}
            """);
        Path output = temporary.resolve("pack");
        Path manifest = temporary.resolve("plugin/models.properties");
        try (var generator = new VexUIModelPackGenerator(Path.of(System.getProperty("vexui.client")),
            List.of(temporary.resolve("source")))) {
            generator.generate(output, manifest);
        }
        var properties = new Properties();
        try (var input = Files.newInputStream(manifest)) {
            properties.load(input);
        }
        assertEquals("6", properties.getProperty("count"));
        var cube = ImageIO.read(output.resolve("assets/test/textures/vexui/models/block.png").toFile());
        assertEquals(48, cube.getWidth());
        assertNotEquals(0, cube.getRGB(24, 24) >>> 24);
        assertEquals(0, cube.getRGB(0, 0) >>> 24);
        assertNotEquals(cube.getRGB(14, 26), cube.getRGB(34, 26), "Cube faces retain directional shading");
        for (int pixels : List.of(16, 32, 48)) {
            for (int offset = 0; offset < 9; offset++) {
                assertTrue(Files.readString(output.resolve("assets/test/font/vexui/models/block/" + pixels
                    + "/y_" + offset + ".json")).contains("\"ascent\":" + (7 - offset)));
            }
        }
        assertTrue(Files.readString(manifest.resolveSibling("vexui-model-unsupported.txt")).contains("test:dynamic"));
        assertTrue(Integer.parseInt(properties.getProperty("model.0.advance")) > 1);
    }
}
