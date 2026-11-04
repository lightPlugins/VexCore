package dev.vexsoft.core.paper.screenui.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.paper.screenui.render.RewardCardRenderer;
import dev.vexsoft.core.paper.screenui.reward.RewardCard;
import dev.vexsoft.core.paper.screenui.reward.RewardCardLayout;
import dev.vexsoft.core.reward.notification.RewardNotification;
import java.math.BigDecimal;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import org.junit.jupiter.api.Test;

/** Guards shader clock wrapping, carrier precision and independent cards in the shared zero-advance bossbar. */
public final class RewardCardProtocolTest {

    @Test
    void bothClocksAndPulseSurviveEveryClockWrapAndTintPayload() {
        for (int birth = 23900; birth < 24120; birth++) {
            var layout = new RewardCardLayout(3, 2, birth, birth + 8, true);
            int payload = layout.payload(TextColor.color(0xBEA3D1)).value();
            assertEquals(Math.floorMod(birth, 120), payload >> 9 & 127);
            assertEquals(Math.floorMod(birth + 8, 120), payload >> 16 & 127);
            assertEquals(1, payload >> 23);
        }
        for (int slot = 0; slot < 4; slot++) {
            for (int previous = 0; previous < 4; previous++) {
                var layout = new RewardCardLayout(slot, previous, 0, 0, false);
                for (int primitive = 0; primitive < 4; primitive++) {
                    for (int carrierOffset = 0; carrierOffset < 300; carrierOffset++) {
                        float position = 8 - layout.ascent(primitive) + carrierOffset;
                        assertEquals(slot * 16 + previous * 4 + primitive,
                            (int) Math.floor((position - 65536.0) / 512.0));
                    }
                }
            }
        }
    }

    @Test
    void longNamesAndLargeCompactAmountsKeepZeroNetAdvanceAndDistinctQuantityFonts() {
        for (String amount : new String[]{"3", "500", "52000000000000000"}) {
            var reward = new RewardNotification("item:test", Component.text("Überarbeitete Bergarbeiter-Spitzhacke +9"),
                "minecraft:diamond", new BigDecimal(amount));
            Component rendered = RewardCardRenderer.render(new RewardCard(reward,
                Key.key("vexcore:reward/icon/vanilla/diamond")), Component.text("Erhalten"),
                new RewardCardLayout(0, 0, 23998, 24001, false));
            assertEquals(0, advance(rendered));
            assertTrue(fonts(rendered).contains("reward/quantity/0_0"));
            assertFalse(fonts(rendered).contains("minecraft:default"));
        }
    }

    private static int advance(Component component) {
        int result = 0;
        if (component instanceof TextComponent text && !text.content().isEmpty()) {
            var key = component.font();
            String font = key == null ? "" : key.value();
            for (int point : text.content().codePoints().toArray()) {
                if (font.equals("ui/space")) {
                    result += point >= 0xE020 ? -(1 << (point - 0xE020)) : 1 << (point - 0xE000);
                } else {
                    result += font.startsWith("reward/frame/") ? 225 : font.contains("/icon/") ? 35 : 19;
                }
            }
        }
        return result + component.children().stream().mapToInt(RewardCardProtocolTest::advance).sum();
    }

    private static String fonts(Component component) {
        var font = component.font();
        return (font == null ? "" : font.asString())
            + component.children().stream().map(RewardCardProtocolTest::fonts).reduce("", String::concat);
    }
}
