package dev.vexsoft.core.paper.service.screenui;

import dev.vexsoft.core.number.WholeAmountFormatter;
import dev.vexsoft.core.paper.screenui.RewardCard;
import dev.vexsoft.core.paper.screenui.RewardCardLayout;
import dev.vexsoft.core.paper.vexui.runtime.VexUIMetrics;
import dev.vexsoft.core.paper.vexui.runtime.VexUITextEngine;
import java.util.ArrayDeque;
import java.util.List;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.flattener.ComponentFlattener;
import net.kyori.adventure.text.flattener.FlattenerListener;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/** Compiles a complete pixel-framed card into one zero-advance element with shared shader timing and pivot. */
public final class RewardCardRenderer {

    private static final TextColor TEXT = TextColor.color(0xDED6EA);
    private static final TextColor SECONDARY = TextColor.color(0xA89BB9);
    private static final VexUIMetrics METRICS = new VexUIMetrics();

    private RewardCardRenderer() {
    }

    public static Component render(RewardCard card, Component subtitle, RewardCardLayout layout) {
        var output = Component.text();
        glyph(output, "\uE001", Key.key("vexcore:reward/frame/" + layout.variant()),
            225, 0, 0, layout.payload(TextColor.color(0xFFFFFF)));
        glyph(output, "\uE001", Key.key(card.icon().namespace(), card.icon().value() + "/" + layout.variant()),
            35, 5, 4, layout.payload(TextColor.color(0xFFFFFF)));
        var engine = new VexUITextEngine(METRICS);
        String quantity = card.reward().amount() == null ? "" : "+" + WholeAmountFormatter.formatDecimal(card.reward().amount());
        int quantityWidth = engine.width(Component.text(quantity));
        // Reserve the pulse's peak width so the enlarged amount never crosses the name column.
        int reserved = Math.max(48, (int) Math.ceil(quantityWidth
            * RewardCardLayout.QUANTITY_SCALE * RewardCardLayout.QUANTITY_PULSE_SCALE) + 8);
        int nameWidth = RewardCardLayout.WIDTH - 44 - reserved - 8;
        if (nameWidth < 24) {
            throw new IllegalArgumentException("Reward quantity exceeds card capacity");
        }
        var lines = engine.wrap(card.reward().name(), nameWidth, 2);
        text(output, lines.getFirst(), 44, lines.size() == 1 ? 10 : 6, layout, false, TEXT);
        if (lines.size() > 1) {
            text(output, lines.get(1), 44, 17, layout, false, TEXT);
        } else {
            text(output, engine.fit(subtitle, nameWidth, 0), 44, 23, layout, false, SECONDARY);
        }
        text(output, Component.text(quantity), RewardCardLayout.WIDTH - 8 - quantityWidth,
            13, layout, true, TEXT);
        return output.build();
    }

    private static void text(TextComponent.Builder output, Component text, int x, int y,
                             RewardCardLayout layout, boolean quantity, TextColor fallback) {
        Key font = Key.key("vexcore:reward/" + (quantity ? "quantity/" : "text/") + layout.variant());
        ComponentFlattener.basic().flatten(text, new FlattenerListener() {
            private final ArrayDeque<Style> styles = new ArrayDeque<>(List.of(Style.empty()));
            private int cursor = x;

            @Override
            public void pushStyle(Style style) {
                styles.push(styles.peek().merge(style));
            }

            @Override
            public void component(String value) {
                TextColor color = styles.peek().color() == null ? fallback : styles.peek().color();
                boolean bold = styles.peek().decoration(TextDecoration.BOLD) == TextDecoration.State.TRUE;
                for (int point : value.codePoints().toArray()) {
                    int character = METRICS.character(point);
                    // Each glyph's fixed marked cell is cancelled; visible advances use vanilla menu metrics.
                    if (character != ' ' && character != 160 && character != 0x200C) {
                        glyph(output, Character.toString(character), font, 19, cursor, y, layout.payload(color));
                        if (bold) {
                            glyph(output, Character.toString(character), font, 19, cursor + 1, y, layout.payload(color));
                        }
                    }
                    cursor += METRICS.advance(character, bold);
                }
            }

            @Override
            public void popStyle(Style style) {
                styles.pop();
            }
        });
    }

    private static void glyph(TextComponent.Builder output, String value, Key font, int advance,
                              int x, int y, TextColor payload) {
        int offset = y * 4096 + x;
        output.append(ScreenUiRenderer.space(offset));
        output.append(Component.text(value, payload).font(font).shadowColor(ShadowColor.none()));
        output.append(ScreenUiRenderer.space(-offset - advance));
    }
}
