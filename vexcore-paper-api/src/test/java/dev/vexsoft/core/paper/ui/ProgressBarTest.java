package dev.vexsoft.core.paper.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.junit.jupiter.api.Test;

public final class ProgressBarTest {

    @Test
    void preservesIndependentFilledAndEmptyColorsAndCustomSymbols() {
        TextColor filled = TextColor.color(0x3366FF);
        TextColor empty = TextColor.color(0x222222);
        assertEquals(
            Component.text("###", filled).append(Component.text("--", empty)),
            ProgressBar.render(0.6, 5, "#", "-", filled, empty)
        );
        assertEquals(
            Component.text("#####", filled).append(Component.text("", empty)),
            ProgressBar.render(1.2, 5, "#", "-", filled, empty)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> ProgressBar.render(Double.NaN, 5, "#", "-", filled, empty)
        );
    }
}
