package dev.vexsoft.core.paper.vexui.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.number.WholeAmount;
import dev.vexsoft.core.paper.vexui.VexUIFrame;
import dev.vexsoft.core.paper.vexui.component.VexUIPagedGrid;
import dev.vexsoft.core.paper.vexui.component.VexUIPagination;
import dev.vexsoft.core.paper.vexui.debug.VexUIDebugProduct;
import dev.vexsoft.core.paper.vexui.debug.VexUIDebugShopView;
import dev.vexsoft.core.paper.vexui.element.VexUIButton;
import dev.vexsoft.core.paper.vexui.element.VexUIColumn;
import dev.vexsoft.core.paper.vexui.element.VexUIElement;
import dev.vexsoft.core.paper.vexui.element.VexUIGrid;
import dev.vexsoft.core.paper.vexui.element.VexUIGridCell;
import dev.vexsoft.core.paper.vexui.element.VexUIPanel;
import dev.vexsoft.core.paper.vexui.element.VexUIRow;
import dev.vexsoft.core.paper.vexui.element.VexUISize;
import dev.vexsoft.core.paper.vexui.element.VexUISpacer;
import dev.vexsoft.core.paper.vexui.element.VexUIText;
import dev.vexsoft.core.paper.vexui.layout.VexUILayout;
import dev.vexsoft.core.paper.vexui.render.VexUIMetrics;
import dev.vexsoft.core.paper.vexui.render.VexUIRenderer;
import dev.vexsoft.core.paper.vexui.render.VexUITextEngine;
import dev.vexsoft.core.paper.vexui.style.VexUITheme;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

/** Drives the real localized shop snapshots through categories, paging, quantities and capacity changes. */
public final class VexUIDebugShopTest {

    @Test
    public void realShopRendersAndItsEmittedFontInstructionsProduceAPixelPreview() throws IOException {
        var view = shop(new AtomicInteger(100), new ArrayList<>());
        VexUIFrame frame = view.render(null);
        var rendered = new VexUIRenderer(new VexUIMetrics()).render(frame,
            action -> ClickEvent.custom(Key.key("test", action), null));
        assertEquals(480, rendered.width());
        assertTrue(rendered.height() < 320);
        assertEquals(9, buttons(frame.content()).stream().filter(button -> button.action().startsWith("select.")).count());
        assertFalse(button(frame, "page.previous").enabled());
        assertTrue(button(frame, "page.next").enabled());
        var preview = new VexUIPackPreview();
        preview.save(rendered);
        // Compare actual font-derived action rectangles against the layout at every pixel center,
        // including button edges and gaps. This catches advance and empty-space hitbox mismatches.
        for (int y = 0; y < rendered.height(); y++) {
            for (int x = 0; x < rendered.width(); x++) {
                Key expected = null;
                for (var region : rendered.regions()) {
                    var bounds = region.bounds();
                    if (x >= bounds.x() && x < bounds.x() + bounds.width()
                        && y >= bounds.y() && y < bounds.y() + bounds.height()) {
                        expected = Key.key("test", region.action());
                    }
                }
                assertEquals(expected, preview.actionAt(x + 0.5, y + 0.5), "Action geometry at " + x + "," + y);
            }
        }
    }

    @Test
    public void categoryPagingQuantityAndCapacityRemainConsistentAcrossRefreshes() throws IOException {
        AtomicInteger capacity = new AtomicInteger(100);
        List<Integer> purchases = new ArrayList<>();
        var view = shop(capacity, purchases);
        var frame = view.render(null);
        frame.actions().get("category.farming").execute(null);
        frame = view.render(null);
        assertTrue(button(frame, "select.potato").enabled());
        frame.actions().get("select.potato").execute(null);
        frame = view.render(null);
        frame.actions().get("amount.plus16").execute(null);
        frame = view.render(null);
        frame.actions().get("buy").execute(null);
        assertEquals(List.of(17), purchases);
        frame.actions().get("amount.max").execute(null);
        frame = view.render(null);
        assertFalse(button(frame, "amount.plus1").enabled());
        capacity.set(0);
        frame = view.render(null);
        assertFalse(button(frame, "buy").enabled());
        var rendered = new VexUIRenderer(new VexUIMetrics()).render(frame,
            action -> ClickEvent.custom(Key.key("test", action), null));
        assertTrue(rendered.regions().stream().noneMatch(region -> region.action().equals("buy")));
        frame.actions().get("page.next").execute(null);
        frame = view.render(null);
        assertFalse(button(frame, "page.next").enabled());
        assertTrue(button(frame, "page.previous").enabled());
        frame.actions().get("category.tools").execute(null);
        frame = view.render(null);
        assertFalse(button(frame, "amount.plus1").enabled());
    }

    @Test
    public void partialShopPageKeepsNavigationAndPurchaseControlsAtTheSameCoordinates() throws IOException {
        var view = shop(new AtomicInteger(100), new ArrayList<>());
        view.render(null).actions().get("category.farming").execute(null);
        var first = view.render(null);
        var renderer = new VexUIRenderer(new VexUIMetrics());
        var full = renderer.render(first, action -> ClickEvent.custom(Key.key("test", action), null));
        first.actions().get("page.next").execute(null);
        var last = view.render(null);
        var partial = renderer.render(last, action -> ClickEvent.custom(Key.key("test", action), null));
        assertEquals(full.height(), partial.height());
        assertEquals(3, buttons(last.content()).stream().filter(button -> button.action().startsWith("select.")).count());
        for (String action : List.of("buy", "amount.plus1", "amount.plus16", "amount.max")) {
            assertEquals(full.regions().stream().filter(region -> region.action().equals(action)).findFirst(),
                partial.regions().stream().filter(region -> region.action().equals(action)).findFirst());
        }
        var layout = new VexUILayout(new VexUITextEngine(new VexUIMetrics()));
        var fullLayout = layout.layout(first.content(), first.width());
        var partialLayout = layout.layout(last.content(), last.width());
        assertEquals(fullLayout.children().get(3).bounds(), partialLayout.children().get(3).bounds());
        assertEquals(fullLayout.children().get(4).bounds(), partialLayout.children().get(4).bounds());
        var grid = partialLayout.children().get(2);
        assertEquals(6, grid.children().stream().filter(cell -> cell.element() instanceof VexUISpacer).count());
        var preview = new VexUIPackPreview();
        preview.render(partial);
        for (var cell : grid.children()) {
            if (cell.element() instanceof VexUISpacer) {
                var bounds = cell.bounds();
                for (int y = bounds.y(); y < bounds.y() + bounds.height(); y++) {
                    for (int x = bounds.x(); x < bounds.x() + bounds.width(); x++) {
                        assertNull(preview.actionAt(x + 0.5, y + 0.5));
                    }
                }
            }
        }
    }

    @Test
    public void automaticPageSlotsAlsoReserveEmptyAndIncompleteRows() {
        var layout = new VexUILayout(new VexUITextEngine(new VexUIMetrics()));
        var full = VexUIPagedGrid.slice(List.of(1, 2, 3, 4, 5, 6), 0, 5);
        var partial = VexUIPagedGrid.slice(List.of(1, 2, 3, 4, 5, 6), 1, 5);
        var empty = VexUIPagedGrid.slice(List.<Integer>of(), 0, 5);
        var fullGrid = full.grid(3, 4, 36, value -> VexUIButton.text(Component.text(value), "select." + value));
        var partialGrid = partial.grid(3, 4, 36, value -> VexUIButton.text(Component.text(value), "select." + value));
        var emptyGrid = empty.grid(3, 4, 36, value -> {
            throw new AssertionError("Empty pages must not render data");
        });
        assertEquals(layout.layout(fullGrid, 302).bounds(), layout.layout(partialGrid, 302).bounds());
        assertEquals(layout.layout(fullGrid, 302).bounds(), layout.layout(emptyGrid, 302).bounds());
        assertEquals(76, layout.layout(emptyGrid, 302).bounds().height());
        assertEquals(5, emptyGrid.children().size());
        var oversized = partial.grid(3, 4, 18, value -> new VexUISpacer(new VexUISize(-1, 19, 0)));
        assertThrows(IllegalArgumentException.class, () -> layout.layout(oversized, 302));
    }

    @Test
    public void nestedBoxLayoutsSupportLargeAndSmallNeighborsAtArbitraryPixelOffsets() {
        var tree = new VexUIRow(7,
            new VexUIPanel(new VexUISize(100, 50, 0), 3,
                VexUITheme.PANEL, new VexUIText(Component.text("Large"))),
            new VexUIColumn(VexUISize.flexible(), 3, List.of(
                VexUIButton.text(Component.text("Small"), "one"), VexUIButton.text(Component.text("Small"), "two"))));
        var frame = new VexUIFrame(Component.text("Test"), Component.text("Close"), 200, tree,
            Map.of("one", ignored -> { }, "two", ignored -> { }));
        var rendered = new VexUIRenderer(new VexUIMetrics()).render(frame,
            action -> ClickEvent.custom(Key.key("test", action), null));
        assertEquals(2, rendered.regions().size());
        assertEquals(107, rendered.regions().getFirst().bounds().x());
        assertEquals(93, rendered.regions().getFirst().bounds().width());
    }

    @Test
    public void gridSpansAndEmptyPaginationHaveDeterministicBounds() {
        var grid = new VexUIGrid(VexUISize.content(), 3, 4, List.of(
            new VexUIGridCell(2, 2, new VexUIPanel(new VexUISize(-1, 60, 0), 3,
                VexUITheme.PANEL, new VexUIText(Component.text("Large")))),
            VexUIButton.text(Component.text("One"), "one"),
            VexUIButton.text(Component.text("Two"), "two")));
        var layout = new VexUILayout(new VexUITextEngine(new VexUIMetrics())).layout(grid, 302);
        assertEquals(200, layout.children().getFirst().bounds().width());
        assertEquals(60, layout.children().getFirst().bounds().height());
        assertEquals(204, layout.children().get(1).bounds().x());
        assertEquals(32, layout.children().get(2).bounds().y());
        var empty = VexUIPagedGrid.slice(List.of(), Integer.MAX_VALUE, 9);
        assertEquals(0, empty.page());
        assertEquals(1, empty.pages());
        assertTrue(empty.entries().isEmpty());
        var clamped = VexUIPagedGrid.slice(List.of(1, 2, 3, 4), 99, 3);
        assertEquals(List.of(4), clamped.entries());
        assertEquals(1, clamped.page());
    }

    private VexUIDebugShopView shop(final AtomicInteger capacity, final List<Integer> purchases) throws IOException {
        Path resources = Path.of(System.getProperty("vexui.source"), "vexcore-paper", "src", "main", "resources");
        var config = YamlConfigurationLoader.builder().path(resources.resolve("vexui/menus/debug-shop.yml")).build().load();
        var translations = YamlConfigurationLoader.builder()
            .path(resources.resolve("languages/de_DE/vexui/debug-shop.yml")).build().load();
        var navigation = YamlConfigurationLoader.builder()
            .path(resources.resolve("languages/de_DE/vexui/navigation.yml")).build().load();
        MiniMessage mini = MiniMessage.builder().tags(TagResolver.builder().resolver(StandardTags.defaults())
            .resolver(TagResolver.resolver("tailwind", (arguments, context) -> {
                int rgb = switch (arguments.pop().value()) {
                    case "violet" -> 0xBEA3D1;
                    case "amber" -> 0xF2C46C;
                    case "lime" -> 0xA8C98D;
                    case "red" -> 0xE18888;
                    default -> 0xD4CFDB;
                };
                while (arguments.hasNext()) {
                    arguments.pop();
                }
                return Tag.styling(TextColor.color(rgb));
            })).build()).build();
        BiFunction<String, Map<String, String>, Component> localize = (key, replacements) -> {
            boolean nav = key.startsWith("vexui.navigation.");
            String path = key.substring(nav ? "vexui.navigation.".length() : "vexui.debug-shop.".length());
            String value = (nav ? navigation : translations).node((Object[]) path.split("\\.")).getString();
            if (value == null) {
                throw new AssertionError("Missing localization " + key);
            }
            for (var entry : replacements.entrySet()) {
                value = value.replace("%" + entry.getKey() + "%", entry.getValue());
            }
            return mini.deserialize(value);
        };
        List<VexUIDebugProduct> products = new ArrayList<>();
        for (var category : config.node("categories").childrenMap().entrySet()) {
            for (var item : category.getValue().childrenList()) {
                Material material = Material.valueOf(item.getString());
                String id = material.name().toLowerCase(Locale.ROOT);
                int maximum = category.getKey().equals("tools") ? 1 : 64;
                products.add(new VexUIDebugProduct(material, category.getKey().toString(),
                    WholeAmount.of(config.node("prices", id).getLong(8)), maximum));
            }
        }
        return new VexUIDebugShopView(products, 480, 3, 9, 4, 36, VexUIPagination.DEFAULT_BUTTON_WIDTH,
            localize, ignored -> capacity.get(), (product, count) -> purchases.add(count));
    }

    private VexUIButton button(final VexUIFrame frame, final String action) {
        return buttons(frame.content()).stream().filter(button -> button.action().equals(action)).findFirst().orElseThrow();
    }

    private List<VexUIButton> buttons(final VexUIElement root) {
        List<VexUIButton> result = new ArrayList<>();
        List<VexUIElement> children;
        if (root instanceof VexUIButton button) {
            result.add(button);
            children = List.of(button.content());
        } else if (root instanceof VexUIPanel panel) {
            children = List.of(panel.content());
        } else if (root instanceof VexUIRow row) {
            children = row.children();
        } else if (root instanceof VexUIColumn column) {
            children = column.children();
        } else if (root instanceof VexUIGrid grid) {
            children = grid.children();
        } else {
            children = List.of();
        }
        children.forEach(child -> result.addAll(buttons(child)));
        return result;
    }
}
