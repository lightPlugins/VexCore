package dev.vexsoft.core.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;

final class ComponentLinesTest {

    @Test
    void splitsDynamicRowsAndInheritsStyles() {
        List<Component> lines =
            ComponentLines.split(Component.text("Header\n", NamedTextColor.RED).append(Component.text("One\nTwo")));

        assertEquals(3, lines.size());
        assertEquals(List.of("Header", "One", "Two"), lines.stream().map(ComponentLinesTest::plain).toList());
        assertTrue(lines.get(2)
            .children()
            .stream()
            .anyMatch(component -> NamedTextColor.RED.equals(component.color())));
    }

    @Test
    void preservesBlankLinesAndChildOverrides() {
        List<Component> lines = ComponentLines.split(Component.text("\n", NamedTextColor.RED)
            .append(Component.text("Blue", NamedTextColor.BLUE)));

        assertEquals(List.of("", "Blue"), lines.stream().map(ComponentLinesTest::plain).toList());
        assertTrue(lines.get(1)
            .children()
            .stream()
            .anyMatch(component -> NamedTextColor.BLUE.equals(component.color())));
    }

    private static String plain(final Component component) {
        return (component instanceof TextComponent text ? text.content() : "") + component.children()
            .stream()
            .map(ComponentLinesTest::plain)
            .reduce(
                "",
                String::concat
            );
    }
}
