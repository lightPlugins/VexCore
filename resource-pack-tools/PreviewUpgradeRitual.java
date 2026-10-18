import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;

/**
 * Previews generated upgrade sprites without a running server or resource-pack shader.
 * Run from the Core checkout with Java 25 source-file mode:
 * {@code java resource-pack-tools/PreviewUpgradeRitual.java <generated-pack-root> <output-directory> [font-directory]}.
 * The optional font directory defaults to {@code resource-pack-tools/fonts} and must contain {@code m5x7.ttf}.
 * Outputs 3x charging, empty, hold and success composites, a 6x bar crop, looping GIFs, and a contact sheet.
 * The GIF samples sixteen charging and sixteen success frames at two ticks per frame, with a brief 99-percent hold;
 * it previews the art and composition rather than reproducing randomized server timing or authoritative outcomes.
 */
public final class PreviewUpgradeRitual {

    private static final int WIDTH = 384;
    private static final int HEIGHT = 320;
    private static final int SCALE = 3;
    private static final int OFFSET_Y = -30;
    private static final int CHARGE_TINT = 0xE8E0ED;
    private static final int SECONDARY = 0x52525B;
    private static final int NORMAL = 0x3F3F46;
    private static final int[] PROGRESS = {0, 12, 12, 12, 28, 28, 28, 47, 47, 47, 68, 68, 85, 85, 99, 99};

    private final Path sprites;
    private final Font font;
    private final Map<String, BufferedImage> images = new HashMap<>();

    private PreviewUpgradeRitual(final Path pack, final Path fonts) throws Exception {
        sprites = pack.resolve("assets/vexcore/textures/ui/upgrade");
        font = Font.createFont(Font.TRUETYPE_FONT, fonts.resolve("m5x7.ttf").toFile()).deriveFont(16.0F);
        if (!font.getFamily().equals("m5x7")) {
            throw new IllegalArgumentException("Expected the owned m5x7 pixel font");
        }
    }

    public static void main(final String[] arguments) throws Exception {
        if (arguments.length < 2 || arguments.length > 3) {
            throw new IllegalArgumentException("Usage: PreviewUpgradeRitual <pack-root> <output-directory> [font-directory]");
        }
        Path output = Path.of(arguments[1]);
        Path fonts = arguments.length == 3 ? Path.of(arguments[2]) : Path.of("resource-pack-tools", "fonts");
        Files.createDirectories(output);
        var preview = new PreviewUpgradeRitual(Path.of(arguments[0]), fonts);
        var frames = new ArrayList<BufferedImage>();
        for (int frame = 0; frame < 16; frame++) {
            frames.add(scale(preview.compose(frame, false, false), SCALE));
        }
        for (int frame = 0; frame < 4; frame++) {
            frames.add(scale(preview.compose(frame + 16, true, false), SCALE));
        }
        for (int frame = 0; frame < 16; frame++) {
            frames.add(scale(preview.compose(frame, false, true), SCALE));
        }
        ImageIO.write(scale(preview.compose(11, false, false), SCALE), "PNG",
            output.resolve("upgrade-ritual-preview.png").toFile());
        ImageIO.write(scale(preview.compose(0, false, false), SCALE), "PNG",
            output.resolve("upgrade-ritual-empty.png").toFile());
        ImageIO.write(scale(preview.compose(16, true, false), SCALE), "PNG",
            output.resolve("upgrade-ritual-hold.png").toFile());
        ImageIO.write(scale(preview.compose(6, false, true), SCALE), "PNG",
            output.resolve("upgrade-ritual-success.png").toFile());
        var fullBar = preview.compose(6, false, true).getSubimage(WIDTH / 2 - 98,
            HEIGHT / 2 + OFFSET_Y + 58, 196, 22);
        ImageIO.write(scale(fullBar, 6), "PNG", output.resolve("upgrade-ritual-bar-alignment.png").toFile());
        ImageIO.write(scale(preview.contactSheet(), SCALE), "PNG",
            output.resolve("upgrade-ritual-contact-sheet.png").toFile());
        writeGif(output.resolve("upgrade-ritual.gif"), frames, 10);
        var successFrames = new ArrayList<BufferedImage>();
        for (int phase = 0; phase < 16; phase++) {
            var panel = preview.compose(phase, false, true).getSubimage(WIDTH / 2 - 112,
                HEIGHT / 2 + OFFSET_Y - 114, 224, 240);
            successFrames.add(scale(panel, SCALE));
        }
        for (int hold = 0; hold < 4; hold++) {
            successFrames.add(successFrames.getLast());
        }
        writeGif(output.resolve("upgrade-ritual-success.gif"), successFrames, 14);
        System.out.println("Wrote upgrade ritual composites, animation, and contact sheet to " + output.toAbsolutePath());
    }

    private BufferedImage compose(final int frame, final boolean holding, final boolean success) throws Exception {
        var image = background(WIDTH, HEIGHT);
        var graphics = graphics(image);
        int progress = success ? 100 : holding ? 99 : PROGRESS[frame];
        int lastStep = Math.min(frame, PROGRESS.length - 1);
        while (lastStep > 0 && PROGRESS[lastStep - 1] == progress) {
            lastStep--;
        }
        double flash = success || holding ? 0 : Math.max(0, 1 - (frame - lastStep) / 3.0);
        double pulse = 0.5 - Math.cos(frame * Math.PI / 8) * 0.5;
        int tint = blend(CHARGE_TINT, 0xFFFFFF, Math.max(flash, pulse));
        sprite(graphics, "frame", 224, 240, -112, -114, 0xFFFFFF);
        if (success) {
            sprite(graphics, "success_" + frame % 16, 184, 104, -92, -45, 0xFFFFFF);
        }
        sprite(graphics, (success ? "burst_" : "core_") + frame % 16, 72, 72, -36, -45, tint);
        int activeRunes = success ? 3 : progress >= 65 ? 2 : progress >= 30 ? 1 : 0;
        for (int rune = 0; rune < 3; rune++) {
            int runeTint = holding && rune == 2 ? blend(0xFFFFFF, 0xFFD090, 0.25 + pulse * 0.5) : 0xFFFFFF;
            String asset = "rune_" + (rune + 1) + (rune < activeRunes ? "" : "_inactive");
            double reveal = Math.clamp((frame - (rune == 1 ? 0 : 2)) / 7.0, 0, 1);
            int lift = success ? (int) Math.round(2 * Math.sin(reveal * Math.PI)) : 0;
            sprite(graphics, asset, 22, 26, -51 + rune * 40, 25 - lift, runeTint);
        }
        double visibleProgress = progress;
        if (!success && !holding) {
            int nextStep = lastStep + 1;
            while (nextStep < PROGRESS.length && PROGRESS[nextStep] == progress) {
                nextStep++;
            }
            int fillFrames = Math.min(6, nextStep - lastStep);
            int previous = lastStep == 0 ? 0 : PROGRESS[lastStep - 1];
            double filled = Math.min(1, (frame - lastStep + 1) / (double) fillFrames);
            filled = filled * (2 - filled);
            visibleProgress = previous + (progress - previous) * filled;
        }
        int pixels = (int) Math.round(visibleProgress * 1.8);
        sprite(graphics, "bar_" + pixels, 180, 12, -90, 64, blend(CHARGE_TINT, 0xFFFFFF, flash));
        centered(graphics, "RESONANZSCHMIEDE", -102, 0x633A80);
        centered(graphics, "Tumblebud", -83, 0x513675);
        centered(graphics, "+4 -> +5  |  60 % Erfolgschance", -66, NORMAL);
        centered(graphics, progress + "%", -1, 0xB9BAC3);
        centered(graphics, success ? "RESONANZ ENTFESSELT"
            : holding ? "Die Resonanz sucht ihr Gleichgewicht ..." : "Die Resonanz verdichtet sich ...",
            92, success ? 0x3C6301 : holding ? 0x973C08 : NORMAL);
        centered(graphics, "Shift - Ergebnis sofort anzeigen", 110, SECONDARY);
        graphics.dispose();
        return image;
    }

    private BufferedImage contactSheet() throws Exception {
        int cellWidth = 90;
        int cellHeight = 100;
        var image = background(cellWidth * 8 + 16, cellHeight * 4 + 16);
        var graphics = graphics(image);
        for (int index = 0; index < 32; index++) {
            String name = (index < 16 ? "core_" : "burst_") + index % 16;
            int x = 8 + index % 8 * cellWidth;
            int y = 8 + index / 8 * cellHeight;
            for (int tileY = 0; tileY < 80; tileY += 8) {
                for (int tileX = 0; tileX < 80; tileX += 8) {
                    graphics.setColor(new Color((tileX / 8 + tileY / 8) % 2 == 0 ? 0x17151D : 0x25212F));
                    graphics.fillRect(x + tileX, y + tileY, 8, 8);
                }
            }
            graphics.drawImage(read(name, 72, 72), x + 4, y + 4, null);
            graphics.setColor(new Color(SECONDARY));
            graphics.drawRect(x, y, 79, 79);
            graphics.setColor(new Color(NORMAL));
            graphics.drawString(name, x + (80 - graphics.getFontMetrics().stringWidth(name)) / 2, y + 92);
        }
        graphics.dispose();
        return image;
    }

    private void sprite(final Graphics2D graphics, final String name, final int width, final int height,
                        final int x, final int y, final int color) throws Exception {
        graphics.drawImage(tint(read(name, width, height), color), WIDTH / 2 + x, HEIGHT / 2 + y + OFFSET_Y, null);
    }

    private BufferedImage read(final String name, final int width, final int height) throws Exception {
        if (images.containsKey(name)) {
            return images.get(name);
        }
        Path path = sprites.resolve(name + ".png");
        var image = ImageIO.read(path.toFile());
        if (image == null || image.getWidth() > 256 || image.getHeight() > 256
            || width > image.getWidth() || height + 2 > image.getHeight()) {
            throw new IllegalArgumentException("Invalid bounded upgrade sprite: " + path);
        }
        // The bitmap font's first two rows contain protocol metadata and the transparent advance sentinel.
        var content = image.getSubimage(0, 2, width, height);
        images.put(name, content);
        return content;
    }

    private void centered(final Graphics2D graphics, final String content, final int y, final int color) {
        var metrics = graphics.getFontMetrics();
        String line = content;
        while (metrics.stringWidth(line) > 208 && !line.isEmpty()) {
            line = line.substring(0, line.length() - 1);
        }
        graphics.setColor(new Color(color));
        int x = (WIDTH - metrics.stringWidth(line)) / 2;
        int baseline = HEIGHT / 2 + y + OFFSET_Y + 10;
        graphics.setColor(new Color(y == -1 ? 0x777780 : 0x9B969F));
        graphics.drawString(line, x + 1, baseline + 1);
        graphics.setColor(new Color(color));
        graphics.drawString(line, x, baseline);
    }

    private Graphics2D graphics(final BufferedImage image) {
        var graphics = image.createGraphics();
        graphics.setFont(font);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        return graphics;
    }

    private static BufferedImage background(final int width, final int height) {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(new Color(0x0B0B12));
        graphics.fillRect(0, 0, width, height);
        for (int y = 0; y < height; y += 16) {
            for (int x = 0; x < width; x += 16) {
                graphics.setColor(new Color((x / 16 + y / 16) % 2 == 0 ? 0x101019 : 0x0E0E17));
                graphics.fillRect(x, y, 15, 15);
            }
        }
        graphics.dispose();
        return image;
    }

    private static BufferedImage tint(final BufferedImage source, final int color) {
        var target = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int pixel = source.getRGB(x, y);
                int red = (pixel >> 16 & 255) * (color >> 16 & 255) / 255;
                int green = (pixel >> 8 & 255) * (color >> 8 & 255) / 255;
                int blue = (pixel & 255) * (color & 255) / 255;
                target.setRGB(x, y, pixel & 0xFF000000 | red << 16 | green << 8 | blue);
            }
        }
        return target;
    }

    private static BufferedImage scale(final BufferedImage image, final int factor) {
        var scaled = new BufferedImage(image.getWidth() * factor, image.getHeight() * factor, BufferedImage.TYPE_INT_RGB);
        var graphics = scaled.createGraphics();
        graphics.setComposite(AlphaComposite.Src);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        graphics.drawImage(image, 0, 0, scaled.getWidth(), scaled.getHeight(), null);
        graphics.dispose();
        return scaled;
    }

    private static int blend(final int first, final int second, final double amount) {
        int red = (int) (((first >> 16) & 255) * (1 - amount) + ((second >> 16) & 255) * amount);
        int green = (int) (((first >> 8) & 255) * (1 - amount) + ((second >> 8) & 255) * amount);
        int blue = (int) ((first & 255) * (1 - amount) + (second & 255) * amount);
        return red << 16 | green << 8 | blue;
    }

    private static void writeGif(final Path path, final List<BufferedImage> frames, final int delay) throws Exception {
        var writer = ImageIO.getImageWritersByFormatName("gif").next();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(path.toFile())) {
            writer.setOutput(output);
            writer.prepareWriteSequence(null);
            for (int index = 0; index < frames.size(); index++) {
                var frame = frames.get(index);
                var metadata = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(frame), null);
                String format = metadata.getNativeMetadataFormatName();
                var tree = (IIOMetadataNode) metadata.getAsTree(format);
                var control = child(tree, "GraphicControlExtension");
                control.setAttribute("disposalMethod", "none");
                control.setAttribute("userInputFlag", "FALSE");
                control.setAttribute("transparentColorFlag", "FALSE");
                control.setAttribute("delayTime", Integer.toString(delay));
                control.setAttribute("transparentColorIndex", "0");
                if (index == 0) {
                    var extensions = child(tree, "ApplicationExtensions");
                    var loop = new IIOMetadataNode("ApplicationExtension");
                    loop.setAttribute("applicationID", "NETSCAPE");
                    loop.setAttribute("authenticationCode", "2.0");
                    loop.setUserObject(new byte[]{1, 0, 0});
                    extensions.appendChild(loop);
                }
                metadata.setFromTree(format, tree);
                writer.writeToSequence(new IIOImage(frame, null, metadata), null);
            }
            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }
    }

    private static IIOMetadataNode child(final IIOMetadataNode root, final String name) {
        for (int index = 0; index < root.getLength(); index++) {
            if (root.item(index).getNodeName().equals(name)) {
                return (IIOMetadataNode) root.item(index);
            }
        }
        var child = new IIOMetadataNode(name);
        root.appendChild(child);
        return child;
    }
}
