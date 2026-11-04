package dev.vexsoft.core.vexui.pack;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vexsoft.core.paper.screenui.reward.RewardCardLayout;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import javax.imageio.ImageIO;

/** Offline art review of actual generated card assets; does not replace Minecraft shader acceptance. */
public final class PreviewRewardCards {

    private PreviewRewardCards() {
    }

    /** Writes an enlarged pixel-exact PNG from the generated pack and its original menu measurements. */
    public static void main(String[] args) throws Exception {
        Path pack = Path.of(args[0]);
        Path output = Path.of(args[1]);
        var json = new ObjectMapper();
        var font = json.readTree(pack.resolve("assets/vexcore/font/reward/text/0_0.json").toFile());
        Map<Integer, BufferedImage> glyphs = new HashMap<>();
        for (var provider : font.get("providers")) {
            var location = provider.get("file").asText().split(":", 2);
            var atlas = ImageIO.read(pack.resolve("assets/" + location[0] + "/textures/" + location[1]).toFile());
            var rows = provider.get("chars");
            for (int row = 0; row < rows.size(); row++) {
                int[] points = rows.get(row).asText().codePoints().toArray();
                for (int column = 0; column < points.length; column++) {
                    if (points[column] != 0) {
                        glyphs.putIfAbsent(points[column], atlas.getSubimage(column * 18, row * 22 + 2, 18, 20));
                    }
                }
            }
        }
        var metrics = new Properties();
        try (var reader = Files.newBufferedReader(pack.resolve("vexui-manifest.properties"))) {
            metrics.load(reader);
        }
        int previewWidth = 224;
        int previewHeight = 120;
        var image = new BufferedImage(previewWidth * 4, previewHeight * 4, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        // Apply GUI scaling before rasterization, as Minecraft does, to retain small-font details.
        graphics.scale(4, 4);
        graphics.setColor(new Color(0x17141F));
        graphics.fillRect(0, 0, previewWidth, previewHeight);
        var frame = ImageIO.read(pack.resolve("assets/vexcore/textures/reward/frame.png").toFile());
        String[] icons = {"diamond", "wheat", "gold_nugget"};
        String[] names = {"Diamant", "Weizen", "Münzen"};
        String[] quantities = {"+3", "+500", "+52aa"};
        for (int index = 0; index < icons.length; index++) {
            var card = new BufferedImage(RewardCardLayout.WIDTH, RewardCardLayout.HEIGHT, BufferedImage.TYPE_INT_ARGB);
            var content = card.createGraphics();
            content.drawImage(frame.getSubimage(0, 2, 224, 40), 0, 0, null);
            var icon = ImageIO.read(pack.resolve("assets/vexcore/textures/reward/icon/vanilla/"
                + icons[index] + ".png").toFile());
            content.drawImage(icon.getSubimage(0, 2, 32, 32), 5, 4, null);
            content.dispose();
            draw(card, names[index], 44, 10, index == 0 ? 0x55FFFF : 0xDED6EA, glyphs, metrics);
            draw(card, "Erhalten", 44, 23, 0xA89BB9, glyphs, metrics);
            int width = quantities[index].codePoints().map(point -> Integer.parseInt(metrics.getProperty("text." + point)))
                .sum();
            double quantityScale = RewardCardLayout.QUANTITY_SCALE;
            draw(card, quantities[index], 216 - width * quantityScale, 20 + (13 - 20) * quantityScale,
                0xDED6EA, glyphs, metrics, quantityScale);
            int widthOnScreen = (int) Math.round(RewardCardLayout.WIDTH * RewardCardLayout.SCALE);
            int heightOnScreen = (int) Math.round(RewardCardLayout.HEIGHT * RewardCardLayout.SCALE);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            graphics.drawImage(card, previewWidth - 16 - widthOnScreen,
                14 + index * (heightOnScreen + 4), widthOnScreen, heightOnScreen, null);
        }
        graphics.dispose();
        Path directory = output.toAbsolutePath().getParent();
        if (directory != null) {
            Files.createDirectories(directory);
        }
        ImageIO.write(image, "PNG", output.toFile());
    }

    private static void draw(BufferedImage image, String text, int x, int y, int color,
                             Map<Integer, BufferedImage> glyphs, Properties metrics) {
        draw(image, text, x, y, color, glyphs, metrics, 1.0);
    }

    private static void draw(BufferedImage image, String text, double x, double y, int color,
                             Map<Integer, BufferedImage> glyphs, Properties metrics, double scale) {
        var graphics = image.createGraphics();
        graphics.translate(x, y);
        graphics.scale(scale, scale);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        int cursor = 0;
        for (int point : text.codePoints().toArray()) {
            if (glyphs.containsKey(point)) {
                var glyph = new BufferedImage(18, 20, BufferedImage.TYPE_INT_ARGB);
                var tinted = glyph.createGraphics();
                tinted.drawImage(glyphs.get(point), 0, 0, null);
                tinted.setComposite(AlphaComposite.SrcIn);
                tinted.setColor(new Color(color));
                tinted.fillRect(0, 0, 18, 20);
                tinted.dispose();
                graphics.drawImage(glyph, cursor, 0, null);
            }
            cursor += Integer.parseInt(metrics.getProperty("text." + point));
        }
        graphics.dispose();
    }
}
