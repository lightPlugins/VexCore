package dev.vexsoft.core.paper.vexui.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.paper.vexui.VexUIFrame;
import dev.vexsoft.core.paper.vexui.element.VexUIButton;
import dev.vexsoft.core.paper.vexui.element.VexUIText;
import dev.vexsoft.core.paper.vexui.layout.VexUILayout;
import dev.vexsoft.core.paper.vexui.layout.VexUIPlacement;
import dev.vexsoft.core.paper.vexui.render.VexUIMetrics;
import dev.vexsoft.core.paper.vexui.render.VexUITextEngine;
import dev.vexsoft.core.paper.vexui.state.VexUIState;
import dev.vexsoft.core.paper.vexui.state.VexUISubscription;
import dev.vexsoft.core.paper.vexui.template.VexUIIcon;
import dev.vexsoft.core.paper.vexui.template.VexUITemplateLayout;
import dev.vexsoft.core.paper.vexui.template.travel.VexUITravelProvider;
import dev.vexsoft.core.paper.vexui.template.travel.VexUITravelSnapshot;
import dev.vexsoft.core.paper.vexui.template.travel.VexUITravelTemplate;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

/** Exercises production sessions with two independent pagers, state refresh, disabled travel and compact geometry. */
public final class VexUITravelTemplateTest {

    @Test
    public void independentPagesRetainTheirHeightAndSelectionAndShareThePagerBaseline() throws Exception {
        var fixture = new VexUISessionTest.Fixture();
        var provider = new Provider();
        var session = fixture.ui.open(fixture.player, new VexUITravelTemplate().createView(provider, "area_0"));
        fixture.flush();
        int height = fixture.dialogs.shown.getLast().height();
        assertTrue(height <= 300);
        assertPagers(fixture.dialogs.frames.getLast());
        assertFalse(fixture.dialogs.frames.getLast().actions().containsKey("travel.point_0"));
        fixture.invoke(session, "destinations.next");
        assertTrue(fixture.dialogs.frames.getLast().actions().containsKey("travel.point_6"));
        assertFalse(fixture.dialogs.frames.getLast().actions().containsKey("travel.point_7"));
        fixture.invoke(session, "categories.next");
        fixture.invoke(session, "categories.next");
        assertEquals(2, fixture.dialogs.frames.getLast().actions().keySet().stream()
            .filter(value -> value.startsWith("category.")).count());
        assertTrue(fixture.dialogs.frames.getLast().actions().containsKey("travel.point_6"));
        assertEquals(height, fixture.dialogs.shown.getLast().height());
        assertPagers(fixture.dialogs.frames.getLast());
        fixture.invoke(session, "category.area_10");
        assertTrue(fixture.dialogs.frames.getLast().actions().containsKey("travel.point_1"));
        fixture.invoke(session, "categories.previous");
        fixture.invoke(session, "categories.previous");
        fixture.invoke(session, "category.area_0");
        assertTrue(fixture.dialogs.frames.getLast().actions().containsKey("travel.point_6"));
        fixture.invoke(session, "destinations.previous");
        new VexUIPackPreview().save(fixture.dialogs.shown.getLast(),
            Path.of(System.getProperty("vexui.preview")).resolveSibling("travel.png"));
        session.close();
        fixture.flush();
        assertTrue(provider.closed);
        assertEquals(0, provider.cancellations);
    }

    @Test
    public void activeJourneyDisablesEveryTravelActionAndExposesOnlyAuthoritativeCancellation() {
        var fixture = new VexUISessionTest.Fixture();
        var provider = new Provider();
        var session = fixture.ui.open(fixture.player, new VexUITravelTemplate().createView(provider, "area_0"));
        fixture.flush();
        fixture.invoke(session, "travel.point_1");
        assertEquals(List.of("point_1"), provider.travelled);
        assertFalse(fixture.dialogs.frames.getLast().actions().keySet().stream()
            .anyMatch(value -> value.startsWith("travel.point_")));
        assertTrue(fixture.dialogs.frames.getLast().actions().containsKey("travel.cancel"));
        fixture.invoke(session, "travel.cancel");
        assertEquals(1, provider.cancellations);
        assertTrue(fixture.dialogs.frames.getLast().actions().containsKey("travel.point_1"));
        int height = fixture.dialogs.shown.getLast().height();
        provider.state.set(new VexUITravelSnapshot(true, List.of(), Component.text("Keine Ziele"), false, false));
        fixture.flush();
        assertEquals(height, fixture.dialogs.shown.getLast().height());
        assertFalse(fixture.dialogs.frames.getLast().actions().keySet().stream()
            .anyMatch(value -> value.startsWith("category.")));
        var current = provider.state.get();
        provider.state.set(new VexUITravelSnapshot(false, current.categories(), Component.empty(), false, false));
        fixture.flush();
        assertTrue(session.isClosed());
    }

    private void assertPagers(final VexUIFrame frame) {
        var root = new VexUILayout(new VexUITextEngine(new VexUIMetrics())).layout(frame.content(), frame.width());
        var pagers = placements(root).stream().filter(value -> value.element() instanceof VexUIButton button
            && (button.action().endsWith(".next") || button.action().endsWith(".previous"))).toList();
        assertEquals(4, pagers.size());
        assertEquals(1, pagers.stream().map(value -> value.bounds().y()).distinct().count());
        assertTrue(pagers.stream().allMatch(value -> ((VexUIText) ((VexUIButton) value.element()).content()).centered()));
    }

    private List<VexUIPlacement> placements(final VexUIPlacement parent) {
        List<VexUIPlacement> result = new ArrayList<>();
        result.add(parent);
        parent.children().forEach(child -> result.addAll(placements(child)));
        return result;
    }

    private static final class Provider implements VexUITravelProvider {

        private final VexUIState<VexUITravelSnapshot> state;
        private final List<String> travelled = new ArrayList<>();
        private int cancellations;
        private boolean closed;

        private Provider() {
            var entries = IntStream.range(0, 9).mapToObj(index -> new VexUITravelSnapshot.Destination("point_" + index,
                Component.text(index < 7 ? "Eisenstollen " + index : "???"),
                Component.text(index == 0 ? "Du bist hier" : index < 7 ? "Entdeckt" : "Noch nicht entdeckt"),
                Component.text("Details"), VexUIIcon.item("minecraft:item/raw_iron"), index < 7, index == 0,
                index > 0 && index < 7)).toList();
            var categories = IntStream.range(0, 12).mapToObj(index -> new VexUITravelSnapshot.Category("area_" + index,
                Component.text("Gebiet " + index), Component.text("Reisegebiet"), entries)).toList();
            state = new VexUIState<>(new VexUITravelSnapshot(true, categories, Component.text("Bereit zur Reise"), false, false));
        }

        @Override
        public VexUITravelSnapshot get() {
            return state.get();
        }

        @Override
        public VexUISubscription subscribe(final Runnable changed) {
            return state.subscribe(changed);
        }

        @Override
        public VexUITemplateLayout layout(final String section) {
            return section.equals("categories") ? new VexUITemplateLayout(560, 1, 5, 34, 3)
                : new VexUITemplateLayout(560, 2, 3, 46, 3);
        }

        @Override
        public Component text(final String key, final Map<String, String> values) {
            return Component.text(switch (key) {
                case "title" -> "Wegsteine";
                case "subtitle" -> "Dein Reisenetz";
                case "categories" -> "Kategorien";
                case "travel" -> "Reisen";
                case "cancel" -> "Abbrechen";
                case "back" -> "Zurück";
                case "current_button" -> "Hier";
                case "locked_button" -> "Gesperrt";
                case "previous" -> "«";
                case "next" -> "»";
                case "page" -> values.get("page") + " / " + values.get("pages");
                case "discovered" -> values.get("discovered") + " / " + values.get("total");
                default -> key;
            });
        }

        @Override
        public void travel(final String destination) {
            travelled.add(destination);
            state.set(new VexUITravelSnapshot(true, state.get().categories(), Component.text("Warteschlange: Platz 3"), true, true));
        }

        @Override
        public void cancel() {
            cancellations++;
            state.set(new VexUITravelSnapshot(true, state.get().categories(), Component.text("Bereit"), false, false));
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
