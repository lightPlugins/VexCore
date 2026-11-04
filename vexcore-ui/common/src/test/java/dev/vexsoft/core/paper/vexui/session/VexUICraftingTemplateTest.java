package dev.vexsoft.core.paper.vexui.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.number.WholeAmount;
import dev.vexsoft.core.paper.vexui.VexUIFrame;
import dev.vexsoft.core.paper.vexui.element.VexUIButton;
import dev.vexsoft.core.paper.vexui.element.VexUIRow;
import dev.vexsoft.core.paper.vexui.element.VexUIText;
import dev.vexsoft.core.paper.vexui.layout.VexUILayout;
import dev.vexsoft.core.paper.vexui.layout.VexUIPlacement;
import dev.vexsoft.core.paper.vexui.render.VexUIMetrics;
import dev.vexsoft.core.paper.vexui.render.VexUITextEngine;
import dev.vexsoft.core.paper.vexui.state.VexUIState;
import dev.vexsoft.core.paper.vexui.state.VexUISubscription;
import dev.vexsoft.core.paper.vexui.template.VexUIIcon;
import dev.vexsoft.core.paper.vexui.template.VexUITemplateLayout;
import dev.vexsoft.core.paper.vexui.template.crafting.VexUICraftingProvider;
import dev.vexsoft.core.paper.vexui.template.crafting.VexUICraftingQuote;
import dev.vexsoft.core.paper.vexui.template.crafting.VexUICraftingSnapshot;
import dev.vexsoft.core.paper.vexui.template.crafting.VexUICraftingTemplate;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.IntStream;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

/** Exercises the production renderer, scoped sessions and async actions for reusable station workspaces. */
public final class VexUICraftingTemplateTest {

    @Test
    public void sparseCostsStayPackedAtTheTopEvenWithLegacyPublishedCardGeometry() {
        var fixture = new VexUISessionTest.Fixture();
        var provider = new Provider();
        provider.legacyCosts = true;
        provider.costCount = 3;
        var session = fixture.ui.open(fixture.player, new VexUICraftingTemplate().createView(provider));
        fixture.flush();
        fixture.invoke(session, "recipe.recipe_0");
        var frame = fixture.dialogs.frames.getLast();
        assertCostRows(frame, 3);
        var root = new VexUILayout(new VexUITextEngine(new VexUIMetrics())).layout(frame.content(), frame.width());
        var placed = placements(root);
        var heading = placed.stream().filter(value -> value.element() instanceof VexUIText label
            && label.text().equals(Component.text("Kosten gesamt"))).findFirst().orElseThrow();
        var rows = placed.stream().filter(value -> value.element() instanceof VexUIRow row
            && row.size().height() == 16).toList();
        assertEquals(heading.bounds().y() + 15, rows.getFirst().bounds().y());
        assertEquals(17, rows.get(1).bounds().y() - rows.getFirst().bounds().y());
        assertEquals(17, rows.get(2).bounds().y() - rows.get(1).bounds().y());
        session.close();
    }

    @Test
    public void pagersStayAlignedAcrossPartialPagesAndDetailsAndOnlyVisibleRecipesAreResolved() throws Exception {
        var fixture = new VexUISessionTest.Fixture();
        var provider = new Provider();
        var session = fixture.ui.open(fixture.player, new VexUICraftingTemplate().createView(provider));
        fixture.flush();
        int height = fixture.dialogs.shown.getLast().height();
        assertTrue(height <= 300, "Dialog height: " + height);
        assertEquals(6, provider.resolved.size());
        assertEquals(0, provider.maximumCalls);
        assertPagers(fixture.dialogs.frames.getLast());
        fixture.invoke(session, "recipes.next");
        assertEquals(2, fixture.dialogs.frames.getLast().actions().keySet().stream()
            .filter(id -> id.startsWith("recipe.")).count());
        assertEquals(height, fixture.dialogs.shown.getLast().height());
        fixture.invoke(session, "categories.next");
        assertEquals(2, fixture.dialogs.frames.getLast().actions().keySet().stream()
            .filter(id -> id.startsWith("category.")).count());
        fixture.invoke(session, "category.category_5");
        assertTrue(fixture.dialogs.frames.getLast().actions().containsKey("recipe.recipe_0"));
        fixture.invoke(session, "categories.previous");
        fixture.invoke(session, "category.category_0");
        assertTrue(fixture.dialogs.frames.getLast().actions().containsKey("recipe.recipe_6"));
        fixture.invoke(session, "recipes.previous");
        new VexUIPackPreview().save(fixture.dialogs.shown.getLast(),
            Path.of(System.getProperty("vexui.preview")).resolveSibling("crafting-overview.png"));
        fixture.invoke(session, "recipe.recipe_0");
        assertEquals(height, fixture.dialogs.shown.getLast().height());
        assertPagers(fixture.dialogs.frames.getLast());
        assertCostRows(fixture.dialogs.frames.getLast(), 8);
        fixture.invoke(session, "costs.next");
        assertCostRows(fixture.dialogs.frames.getLast(), 3);
        assertEquals(height, fixture.dialogs.shown.getLast().height());
        fixture.invoke(session, "back");
        assertTrue(fixture.dialogs.frames.getLast().actions().containsKey("recipe.recipe_0"));
        provider.state.set(new VexUICraftingSnapshot(true, Component.text("Amboss"), List.of()));
        fixture.flush();
        assertEquals(height, fixture.dialogs.shown.getLast().height());
        assertFalse(fixture.dialogs.frames.getLast().actions().containsKey("craft"));
        session.close();
        assertTrue(provider.closed);
    }

    @Test
    public void exactQuantitiesScaleCostsAndBundledOutputAndBlockDuplicateOrUnaffordableActions() throws Exception {
        var fixture = new VexUISessionTest.Fixture();
        var provider = new Provider();
        var session = fixture.ui.open(fixture.player, new VexUICraftingTemplate().createView(provider));
        fixture.flush();
        fixture.invoke(session, "recipe.recipe_0");
        fixture.invoke(session, "quantity.plus_many");
        assertEquals(WholeAmount.of(17), provider.lastQuote.executions());
        assertEquals(WholeAmount.of(153), provider.lastQuote.resultAmount());
        assertEquals(WholeAmount.of(51), provider.lastQuote.costs().getFirst().required());
        fixture.invoke(session, "quantity.minus_many");
        assertEquals(WholeAmount.ONE, provider.lastQuote.executions());
        fixture.invoke(session, "quantity.plus_many");
        assertFalse(fixture.dialogs.frames.getLast().actions().containsKey("craft"));
        fixture.invoke(session, "quantity.maximum");
        assertEquals(1, provider.maximumCalls);
        assertEquals(WholeAmount.of(3), provider.lastQuote.executions());
        new VexUIPackPreview().save(fixture.dialogs.shown.getLast(),
            Path.of(System.getProperty("vexui.preview")).resolveSibling("crafting-detail.png"));
        fixture.invoke(session, "craft");
        assertEquals(1, provider.crafted.size());
        assertEquals(WholeAmount.of(3), provider.crafted.getFirst().executions());
        assertFalse(fixture.dialogs.frames.getLast().actions().containsKey("craft"));
        provider.completion.complete(Component.text("27 Stück hergestellt"));
        fixture.flush();
        assertTrue(fixture.dialogs.frames.getLast().actions().containsKey("craft"));
        fixture.invoke(session, "quantity.minus_many");
        assertEquals(WholeAmount.ONE, provider.lastQuote.executions());
        session.close();
    }

    @Test
    public void invalidatedStationClosesWithoutCraftingAndAsyncCompletionCannotReopenIt() {
        var fixture = new VexUISessionTest.Fixture();
        var provider = new Provider();
        var session = fixture.ui.open(fixture.player, new VexUICraftingTemplate().createView(provider));
        fixture.flush();
        fixture.invoke(session, "recipe.recipe_0");
        fixture.invoke(session, "craft");
        provider.state.set(new VexUICraftingSnapshot(false, Component.text("Amboss"), List.of()));
        fixture.flush();
        int shown = fixture.dialogs.shown.size();
        assertTrue(session.isClosed());
        provider.completion.complete(Component.text("Fertig"));
        fixture.flush();
        assertEquals(shown, fixture.dialogs.shown.size());
        assertTrue(provider.closed);
    }

    private void assertPagers(final VexUIFrame frame) {
        var root = new VexUILayout(new VexUITextEngine(new VexUIMetrics())).layout(frame.content(), frame.width());
        var pagers = placements(root).stream().filter(value -> value.element() instanceof VexUIButton button
            && (button.action().endsWith(".next") || button.action().endsWith(".previous"))).toList();
        assertEquals(4, pagers.size());
        assertEquals(1, pagers.stream().map(value -> value.bounds().y()).distinct().count());
        assertTrue(pagers.stream().allMatch(value -> ((VexUIText) ((VexUIButton) value.element()).content()).centered()));
    }

    private void assertCostRows(final VexUIFrame frame, final int expected) {
        var root = new VexUILayout(new VexUITextEngine(new VexUIMetrics())).layout(frame.content(), frame.width());
        var rows = placements(root).stream().filter(value -> value.element() instanceof VexUIRow row && row.size().height() == 16).toList();
        assertEquals(expected, rows.size());
        assertEquals(1, rows.stream().map(value -> value.bounds().x() + value.bounds().width()).distinct().count());
        for (var row : rows) {
            assertEquals(16, row.bounds().height());
            assertEquals(row.children().get(1).bounds().y(), row.children().get(2).bounds().y());
            assertTrue(row.children().get(1).bounds().x() + row.children().get(1).bounds().width()
                <= row.children().get(2).bounds().x());
        }
    }

    private List<VexUIPlacement> placements(final VexUIPlacement parent) {
        List<VexUIPlacement> result = new ArrayList<>();
        result.add(parent);
        parent.children().forEach(child -> result.addAll(placements(child)));
        return result;
    }

    private static final class Provider implements VexUICraftingProvider {

        private final VexUIState<VexUICraftingSnapshot> state;
        private final List<String> resolved = new ArrayList<>();
        private final List<VexUICraftingQuote> crafted = new ArrayList<>();
        private final CompletableFuture<Component> completion = new CompletableFuture<>();
        private VexUICraftingQuote lastQuote;
        private int maximumCalls;
        private int costCount = 11;
        private boolean legacyCosts;
        private boolean closed;

        private Provider() {
            var recipes = IntStream.range(0, 8).mapToObj(index -> "recipe_" + index).toList();
            var categories = IntStream.range(0, 7).mapToObj(index -> new VexUICraftingSnapshot.Category(
                "category_" + index, Component.text("Werkzeuge " + index), Component.text("Beschreibung"), recipes)).toList();
            state = new VexUIState<>(new VexUICraftingSnapshot(true, Component.text("Amboss"), categories));
        }

        @Override
        public VexUICraftingSnapshot get() {
            return state.get();
        }

        @Override
        public VexUISubscription subscribe(final Runnable changed) {
            return state.subscribe(changed);
        }

        @Override
        public VexUITemplateLayout layout(final String section) {
            return switch (section) {
                case "categories" -> new VexUITemplateLayout(560, 1, 5, 34, 3);
                case "recipes" -> new VexUITemplateLayout(560, 2, 3, 58, 3);
                default -> legacyCosts ? new VexUITemplateLayout(560, 1, 4, 32, 3)
                    : new VexUITemplateLayout(560, 1, 8, 16, 1);
            };
        }

        @Override
        public VexUICraftingSnapshot.Recipe recipe(final String id) {
            resolved.add(id);
            return new VexUICraftingSnapshot.Recipe(Component.text("Verbesserte Spitzhacke"), Component.text("Herstellbar"),
                Component.text("Details"), VexUIIcon.item("minecraft:item/iron_pickaxe"));
        }

        @Override
        public VexUICraftingQuote quote(final String id, final WholeAmount executions) {
            var costs = IntStream.range(0, costCount).mapToObj(index -> new VexUICraftingQuote.Cost(
                Component.text("Eisenbarren"), VexUIIcon.item("minecraft:item/iron_ingot"), executions.multiply(3),
                WholeAmount.of(9), Component.text("Materialdetails"))).toList();
            lastQuote = new VexUICraftingQuote(executions, executions.multiply(9), costs,
                List.of(Component.text("Schmiede Lv. 5 · Erreicht"),
                    Component.text("Bergbau Lv. 10 · Erreicht"), Component.text("Weitere Voraussetzung erreicht")), executions.compareTo(WholeAmount.of(3)) <= 0,
                Component.text("Herstellen"), id);
            return lastQuote;
        }

        @Override
        public WholeAmount maximum(final String id) {
            maximumCalls++;
            return WholeAmount.of(3);
        }

        @Override
        public CompletionStage<Component> craft(final String id, final VexUICraftingQuote shown) {
            crafted.add(shown);
            return completion;
        }

        @Override
        public Component text(final String key, final Map<String, String> values) {
            return Component.text(switch (key) {
                case "back" -> "Zurück";
                case "close" -> "Schließen";
                case "categories" -> "Kategorien";
                case "recipes" -> values.get("amount") + " Rezepte";
                case "previous" -> "«";
                case "next" -> "»";
                case "recipe_page" -> "Rezepte " + values.get("page") + " / " + values.get("pages");
                case "cost_page" -> "Kosten " + values.get("page") + " / " + values.get("pages");
                case "page" -> values.get("page") + " / " + values.get("pages");
                case "cost_available", "cost_missing" -> values.get("required") + " / " + values.get("available");
                case "result" -> "Ergebnis: " + values.get("amount") + " Stück";
                case "costs" -> "Kosten gesamt";
                case "cost_columns" -> "Bedarf / Bestand";
                case "subtitle" -> "Ausrüstung und Handwerk";
                case "recipe_subtitle" -> "%category% · Rezept";
                case "yield" -> values.get("amount") + " Stück je Herstellung";
                case "quantity_title" -> "Herstellungen";
                case "quantity" -> values.get("amount") + "×";
                case "maximum" -> "Max";
                case "minus" -> "−1";
                case "plus" -> "+1";
                case "minus_many" -> "−10";
                case "plus_many" -> "+10";
                default -> key;
            });
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
