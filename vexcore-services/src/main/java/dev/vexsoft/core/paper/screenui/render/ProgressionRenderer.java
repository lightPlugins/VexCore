package dev.vexsoft.core.paper.screenui.render;

import dev.vexsoft.core.paper.screenui.progression.ProgressionLayout;
import dev.vexsoft.core.paper.screenui.progression.ProgressionNotice;
import dev.vexsoft.core.paper.vexui.render.VexUIMetrics;
import dev.vexsoft.core.paper.vexui.render.VexUITextEngine;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** Centers marked glyphs on a shared animation pivot without reserving bossbar width. */
public final class ProgressionRenderer {

    private static final VexUIMetrics METRICS = new VexUIMetrics();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final int WIDTH = 188;
    private static final int GOLD = 1 << 7;
    private static final int TWINKLE = 1 << 10;
    private static final int[][] SPARKS = {
        {-12, -9}, {9, -12}, {-17, -2}, {15, -4}, {-9, 10}, {8, 8},
        {-12, 5}, {13, 3}, {-5, -14}, {4, 13}, {-18, 3}, {18, 1}
    };
    private static final int[][] TWINKLES = {
        {-69, -18}, {50, -29}, {-40, 18}, {69, 13}, {-55, -32}, {36, 30}, {-79, 7}, {80, -4}
    };

    private ProgressionRenderer() {
    }

    public static Component render(ProgressionNotice notice, long started) {
        var output = Component.text();
        var engine = new VexUITextEngine(METRICS);
        int clock = Math.floorMod(started, ProgressionLayout.CLOCK_PERIOD);
        line(output, fit(engine, notice.title()), -36, 0, clock, 0);
        line(output, fit(engine, notice.subtitle()), 23, 0, clock, 1);
        line(output, fit(engine, notice.source()), 36, 0, clock, 2);
        line(output, Integer.toString(notice.level()), -7, 1, clock, 1);
        String old = notice.previousLevel() == 0 ? "◇" : Integer.toString(notice.previousLevel());
        for (int part = 2; part < 6; part++) {
            line(output, old, -7, part, clock, 0);
        }
        for (int[] spark : SPARKS) {
            glyph(output, '✦', spark[0], spark[1], 6, clock | GOLD);
        }
        for (int index = 0; index < TWINKLES.length; index++) {
            int[] star = TWINKLES[index];
            glyph(output, '✦', star[0], star[1], 6, clock | GOLD | TWINKLE | (index << 11));
        }
        return output.build();
    }

    private static String fit(VexUITextEngine engine, Component line) {
        String text = PLAIN.serialize(line).replace('\n', ' ').replace('\r', ' ');
        return PLAIN.serialize(engine.fit(Component.text(text), WIDTH, 0));
    }

    private static void line(TextComponent.Builder output, String text, int y, int part, int clock, int tone) {
        int[] points = text.codePoints().map(METRICS::character).toArray();
        int width = 0;
        for (int point : points) {
            width += METRICS.advance(point, false);
        }
        int x = -Math.floorDiv(width, 2);
        int payload = clock | (tone << 7) | ((part != 0 && points.length > 4 ? 1 : 0) << 9);
        for (int point : points) {
            if (point != ' ' && point != 160 && point != 0x200C) {
                glyph(output, point, x, y, part, payload);
            }
            x += METRICS.advance(point, false);
        }
    }

    private static void glyph(TextComponent.Builder output, int point, int x, int y, int part, int payload) {
        int offset = y * 4096 + x;
        output.append(ScreenUiRenderer.space(offset));
        output.append(Component.text(Character.toString(point), TextColor.color(payload))
            .font(Key.key(ProgressionLayout.font(part))).shadowColor(ShadowColor.none()));
        output.append(ScreenUiRenderer.space(-offset - 19));
    }
}
