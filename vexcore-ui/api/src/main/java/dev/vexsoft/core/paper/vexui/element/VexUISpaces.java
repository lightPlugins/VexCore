package dev.vexsoft.core.paper.vexui.element;

import java.util.stream.IntStream;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;

/** Signed advances in the resource pack's space font, shared by native titles and dialog bodies. */
public final class VexUISpaces {
    private static final Key FONT = Key.key("vexcore:vexui/space");
    private static final Component[] COMMON = IntStream.rangeClosed(-1024, 1024)
        .mapToObj(VexUISpaces::create).toArray(Component[]::new);

    private VexUISpaces() {
    }

    /** Returns a component advancing the text cursor by the supplied logical pixel distance. */
    public static Component shift(final int pixels) {
        return pixels >= -1024 && pixels <= 1024 ? COMMON[pixels + 1024] : create(pixels);
    }

    private static Component create(final int pixels) {
        if (pixels == 0) {
            return Component.empty();
        }
        var text = new StringBuilder();
        long remaining = Math.abs((long) pixels);
        int offset = pixels < 0 ? 0xE020 : 0xE000;
        for (int power = 11; power >= 0; power--) {
            while (remaining >= (1 << power)) {
                text.appendCodePoint(offset + power);
                remaining -= 1 << power;
            }
        }
        return Component.text(text.toString()).font(FONT);
    }
}
