package dev.vexsoft.core.api.localization;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Covers section placement, repeat formatting, and empty sections. */
public final class LocalizedStringListsTest {

    @Test
    public void expandsRepeatedEntriesOnlyAtTheMarker() {
        List<String> rendered = LocalizedStringLists.expand(
            List.of("", "Item %name%", "%stats%", "Ende"),
            Map.of("stats", List.of("%icon% %label% +%value%")),
            Map.of("stats", List.of(
                Map.of("icon", "H", "label", "Leben", "value", "30"),
                Map.of("icon", "D", "label", "Verteidigung", "value", "12")
            )),
            Map.of("name", "Brustplatte")
        );
        assertEquals(List.of("", "Item Brustplatte", "H Leben +30", "D Verteidigung +12", "Ende"), rendered);
    }

    @Test
    public void emptySectionAddsNoLines() {
        assertEquals(List.of("Anfang", "Ende"), LocalizedStringLists.expand(
            List.of("Anfang", "%stats%", "Ende"),
            Map.of("stats", List.of("%value%")), Map.of("stats", List.of()), Map.of()));
    }
}
