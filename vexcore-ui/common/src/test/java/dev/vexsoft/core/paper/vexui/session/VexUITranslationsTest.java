package dev.vexsoft.core.paper.vexui.session;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.vexsoft.core.paper.vexui.render.VexUIMetrics;
import dev.vexsoft.core.paper.vexui.render.VexUITextEngine;
import dev.vexsoft.core.paper.vexui.render.VexUITranslations;
import java.util.Locale;
import java.util.stream.IntStream;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

/** Verifies real vanilla dictionaries, nested styled arguments and locale isolation before glyph measurement. */
final class VexUITranslationsTest {

    @Test
    void stockUsesTheRequestedLanguageWithoutChangingItemStyling() {
        var fixture = new VexUISessionTest.Fixture();
        var input = Component.translatable("item.minecraft.stick").color(NamedTextColor.GOLD)
            .hoverEvent(Component.text("Details"));
        var german = fixture.ui.resolveText(input, Locale.GERMANY);
        assertEquals("Stock", plain(german));
        assertEquals(input.color(), german.color());
        assertEquals(input.hoverEvent(), german.hoverEvent());
        assertEquals("Stick", plain(fixture.ui.resolveText(input, Locale.ENGLISH)));
        assertEquals("Stick", plain(fixture.ui.resolveText(input, Locale.FRENCH)));
    }

    @Test
    void nestedArgumentsAndSiblingTextAreResolvedBeforeMeasurement() {
        var input = Component.translatable("chat.type.text", Component.text("Vex"),
            Component.translatable("item.minecraft.stick"));
        assertEquals("<Vex> Stock", plain(VexUITranslations.render(input, Locale.GERMANY)));
        assertEquals("2× Stock!", plain(VexUITranslations.render(Component.text("2× ")
            .append(Component.translatable("item.minecraft.stick")).append(Component.text("!")), Locale.GERMANY)));
        var text = new VexUITextEngine(new VexUIMetrics());
        assertEquals(text.width(Component.text("Stick")), text.width(Component.translatable("item.minecraft.stick")));
    }

    @Test
    void concurrentViewersDoNotShareMutableFormatsOrLanguageState() {
        IntStream.range(0, 200).parallel().forEach(index -> {
            boolean german = index % 2 == 0;
            var result = VexUITranslations.render(Component.translatable("item.minecraft.stick"),
                german ? Locale.GERMANY : Locale.ENGLISH);
            assertEquals(german ? "Stock" : "Stick", plain(result));
        });
    }

    private String plain(final Component text) {
        return PlainTextComponentSerializer.plainText().serialize(text);
    }
}
