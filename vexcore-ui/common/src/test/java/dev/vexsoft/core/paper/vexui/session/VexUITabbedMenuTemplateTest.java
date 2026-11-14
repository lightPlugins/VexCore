package dev.vexsoft.core.paper.vexui.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.paper.vexui.action.VexUIAction;
import dev.vexsoft.core.paper.vexui.component.VexUIMenuHeader;
import dev.vexsoft.core.paper.vexui.component.VexUIOption;
import dev.vexsoft.core.paper.vexui.component.VexUITabs;
import dev.vexsoft.core.paper.vexui.element.VexUIColumn;
import dev.vexsoft.core.paper.vexui.element.VexUIElement;
import dev.vexsoft.core.paper.vexui.element.VexUISize;
import dev.vexsoft.core.paper.vexui.element.VexUIText;
import dev.vexsoft.core.paper.vexui.layout.VexUIBounds;
import dev.vexsoft.core.paper.vexui.layout.VexUILayout;
import dev.vexsoft.core.paper.vexui.layout.VexUIPlacement;
import dev.vexsoft.core.paper.vexui.render.VexUIMetrics;
import dev.vexsoft.core.paper.vexui.render.VexUITextEngine;
import dev.vexsoft.core.paper.vexui.style.VexUIStyleSheet;
import dev.vexsoft.core.paper.vexui.template.VexUITabbedMenuTemplate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

/** Exercises the shared frame, native tab pixels, navigation, and unchanged caller-owned content. */
final class VexUITabbedMenuTemplateTest {

    private static final int GOLD = 0xC5A164;

    @Test
    void attachedTabsKeepContentGeometryAndUseTheContentSurfaceWithAnOpenBottom() throws Exception {
        var fixture = new VexUISessionTest.Fixture();
        var style = VexUIStyleSheet.defaults();
        var template = new VexUITabbedMenuTemplate(VexUITabbedMenuTemplate.Layout.compact(560, 176),
            new VexUITabbedMenuTemplate.Style(style.selected(), style.normal().lightBorder(), GOLD));
        var body = new VexUIColumn(VexUISize.fixed(552, 176), 0, List.of(new VexUIText(Component.text("Content"))));
        var footer = new VexUIText(VexUISize.fixed(552, 8), Component.text("Footer"), false);
        var selected = new AtomicReference<>("tab.sockets");
        var tabs = List.of(new VexUITabs.Tab("tab.upgrades", Component.text("Upgrades"), -1, true, null),
            new VexUITabs.Tab("tab.sockets", Component.text("Sockel"), 66, true, null),
            new VexUITabs.Tab("tab.rarity", Component.text("Seltenheit"), -1, true, null));
        var header = new VexUIMenuHeader(new VexUIText(VexUISize.flexible(), Component.text("Title"), false),
            List.of(new VexUIOption("section.equipment", Component.text("Ausrüstung")),
                new VexUIOption("section.combat", Component.text("Combat"))), "section.combat", controls(true));
        var session = fixture.ui.open(fixture.player, current -> {
            Map<String, VexUIAction> actions = new LinkedHashMap<>();
            tabs.forEach(tab -> actions.put(tab.id(), ignored -> selected.set(tab.id())));
            header.sections().forEach(section -> actions.put(section.id(), ignored -> { }));
            return template.render(current, Component.text("Title"), header, tabs, selected.get(),
                body, footer, actions);
        });
        fixture.flush();
        var frame = fixture.dialogs.frames.getLast();
        var rendered = fixture.dialogs.shown.getLast();
        assertEquals(560, rendered.width());
        assertEquals(259, rendered.height());
        var layout = new VexUILayout(new VexUITextEngine(new VexUIMetrics())).layout(frame.content(), frame.width());
        assertSame(body, placement(layout, body).element());
        assertEquals(new VexUIBounds(4, 46, 552, 176), placement(layout, body).bounds());
        assertEquals(new VexUIBounds(4, 225, 552, 8), placement(layout, footer).bounds());
        var tab = rendered.regions().stream().filter(region -> region.action().equals("tab.sockets"))
            .findFirst().orElseThrow().bounds();
        var image = new VexUIPackPreview().render(rendered);
        assertEquals(0xFF000000 | GOLD, image.getRGB(tab.x() + 2, tab.y()));
        assertEquals(0xFF000000 | style.panel().background(), image.getRGB(tab.x() + 3, tab.y() + 17));
        assertEquals(VexUIMenuHeader.BACK_WIDTH, rendered.regions().stream()
            .filter(region -> region.action().equals("navigation.back")).findFirst().orElseThrow().bounds().width());
        fixture.invoke(session, "tab.upgrades");
        assertEquals("tab.upgrades", selected.get());
        var refreshed = fixture.dialogs.frames.getLast();
        var refreshedLayout = new VexUILayout(new VexUITextEngine(new VexUIMetrics()))
            .layout(refreshed.content(), refreshed.width());
        assertEquals(placement(layout, body).bounds(), placement(refreshedLayout, body).bounds());
        fixture.invoke(session, "navigation.back");
        assertTrue(session.isClosed());
    }

    @Test
    void disabledNavigationHasNoExecutableHitAreasButCloseCanRemainAvailable() {
        var fixture = new VexUISessionTest.Fixture();
        var style = VexUIStyleSheet.defaults();
        var template = new VexUITabbedMenuTemplate(VexUITabbedMenuTemplate.Layout.compact(560, 176),
            new VexUITabbedMenuTemplate.Style(style.selected(), style.normal().lightBorder(), GOLD));
        var session = fixture.ui.open(fixture.player, current -> template.render(current, Component.text("Title"),
            new VexUIMenuHeader(new VexUIText(VexUISize.flexible(), Component.text("Title"), false),
                List.of(), null, controls(false)),
            List.of(new VexUITabs.Tab("tab.busy", Component.text("Busy"), -1, false, null)), "tab.busy",
            new VexUIText(Component.text("Content")), new VexUIText(Component.text("Footer")), new LinkedHashMap<>()));
        fixture.flush();
        assertFalse(fixture.dialogs.frames.getLast().actions().containsKey("navigation.back"));
        assertTrue(fixture.dialogs.shown.getLast().regions().stream()
            .noneMatch(region -> region.action().equals("tab.busy") || region.action().equals("navigation.back")));
        fixture.invoke(session, "navigation.close");
        assertTrue(session.isClosed());
    }

    @Test
    void budgetsAndAmbiguousOrOversizedTabsAreRejected() {
        var style = VexUIStyleSheet.defaults();
        assertThrows(IllegalArgumentException.class, () -> VexUITabbedMenuTemplate.Layout.compact(560, 177));
        var tab = new VexUITabs.Tab("tab.one", Component.text("One"), 80, true, null);
        assertThrows(IllegalArgumentException.class, () -> VexUITabs.attached(List.of(tab, tab), tab.id(),
            552, 18, 3, style.normal(), style.panel(), 0, GOLD));
        assertThrows(IllegalArgumentException.class, () -> VexUITabs.attached(List.of(tab), "missing",
            552, 18, 3, style.normal(), style.panel(), 0, GOLD));
        assertThrows(IllegalArgumentException.class, () -> VexUITabs.attached(List.of(tab), tab.id(),
            70, 18, 3, style.normal(), style.panel(), 0, GOLD));
        var sections = List.of(new VexUIOption("section.equipment", Component.text("Equipment")));
        assertDoesNotThrow(() -> new VexUIMenuHeader(new VexUIText(Component.text("Retained page")),
            sections, null, controls(true)), "A reload can hide the retained page's section");
        assertThrows(IllegalArgumentException.class, () -> new VexUIMenuHeader(new VexUIText(Component.text("Title")),
            sections, "unknown", controls(true)));
    }

    private static VexUIMenuHeader.Controls controls(final boolean backEnabled) {
        return new VexUIMenuHeader.Controls(Component.text("← Zurück"), Component.text("×"),
            Component.text("Close"), backEnabled, true, null, null);
    }

    private static VexUIPlacement placement(final VexUIPlacement root, final VexUIElement target) {
        if (root.element() == target) {
            return root;
        }
        for (var child : root.children()) {
            var found = placement(child, target);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
