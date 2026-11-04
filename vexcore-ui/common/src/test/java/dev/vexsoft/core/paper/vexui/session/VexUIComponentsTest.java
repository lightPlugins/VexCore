package dev.vexsoft.core.paper.vexui.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.paper.vexui.VexUIDebugOptions;
import dev.vexsoft.core.paper.vexui.VexUIFrame;
import dev.vexsoft.core.paper.vexui.VexUILayoutException;
import dev.vexsoft.core.paper.vexui.VexUIService;
import dev.vexsoft.core.paper.vexui.action.VexUIAction;
import dev.vexsoft.core.paper.vexui.component.VexUIOption;
import dev.vexsoft.core.paper.vexui.component.VexUIQuantitySelector;
import dev.vexsoft.core.paper.vexui.component.VexUISelectionGroup;
import dev.vexsoft.core.paper.vexui.debug.VexUIDebugComponentsView;
import dev.vexsoft.core.paper.vexui.element.VexUIButton;
import dev.vexsoft.core.paper.vexui.element.VexUIColumn;
import dev.vexsoft.core.paper.vexui.element.VexUISize;
import dev.vexsoft.core.paper.vexui.element.VexUIText;
import dev.vexsoft.core.paper.vexui.layout.VexUILayout;
import dev.vexsoft.core.paper.vexui.render.VexUIMetrics;
import dev.vexsoft.core.paper.vexui.render.VexUIRenderer;
import dev.vexsoft.core.paper.vexui.render.VexUITextEngine;
import dev.vexsoft.core.paper.vexui.service.VexUIRegistration;
import dev.vexsoft.core.paper.vexui.style.VexUIStyleSheet;
import dev.vexsoft.core.paper.vexui.style.VexUITheme;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import javax.imageio.ImageIO;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

/** Exercises reusable components and diagnostics through real scoped services, localized views and fonts. */
public final class VexUIComponentsTest {

    @Test
    public void localizedPlaygroundSupportsSelectionConfirmationLoadingAndSearchReturn() throws IOException {
        var fixture = new VexUISessionTest.Fixture();
        List<CompletableFuture<Integer>> requests = new ArrayList<>();
        var view = new VexUIDebugComponentsView(480, locale("de_DE"), () -> {
            var result = new CompletableFuture<Integer>();
            requests.add(result);
            return result;
        }, false);
        var session = fixture.ui.open(fixture.player, view);
        fixture.flush();
        assertEquals(3, fixture.dialogs.shown.getLast().regions().stream()
            .filter(region -> region.action().startsWith("select.")).count());
        fixture.invoke(session, "select.carrot");
        fixture.invoke(session, "amount.plus16");
        assertFalse(enabled(fixture, "stock.take"));
        fixture.invoke(session, "load");
        assertFalse(enabled(fixture, "load"));
        assertFalse(enabled(fixture, "load.error"));
        requests.getLast().complete(42);
        fixture.flush();
        assertTrue(enabled(fixture, "stock.take"));
        assertTrue(text(fixture.dialogs.frames.getLast()).contains("Geladen"));
        fixture.invoke(session, "load.error");
        assertTrue(text(fixture.dialogs.frames.getLast()).contains("Fehler"));
        fixture.invoke(session, "tab.settings");
        fixture.invoke(session, "option.notifications");
        fixture.invoke(session, "option.sound");
        assertFalse(fixture.dialogs.frames.getLast().clickSound().enabled());
        assertFalse(enabled(fixture, "option.locked"));
        fixture.invoke(session, "theme");
        assertEquals(22, session.styleSheet().buttonHeight());
        fixture.invoke(session, "reset");
        assertTrue(fixture.dialogs.frames.getLast().actions().containsKey("confirmation.accept"));
        fixture.invoke(session, "navigation.back");
        fixture.invoke(session, "tab.catalog");
        assertTrue(text(fixture.dialogs.frames.getLast()).contains("17x"));
        assertTrue(text(fixture.dialogs.frames.getLast()).contains("42"));
        fixture.invoke(session, "tab.settings");
        fixture.invoke(session, "reset");
        fixture.invoke(session, "confirmation.accept");
        fixture.invoke(session, "tab.catalog");
        assertTrue(text(fixture.dialogs.frames.getLast()).contains("1x"));
        assertTrue(text(fixture.dialogs.frames.getLast()).contains("Karotte"));
        fixture.invoke(session, "search");
        fixture.coordinator.dispatch(fixture.player, fixture.dialogs.searchSubmit, "Diamant");
        fixture.flush();
        assertEquals(1, fixture.dialogs.shown.getLast().regions().stream()
            .filter(region -> region.action().startsWith("select.")).count());
        fixture.invoke(session, "search.clear");
        assertEquals(3, fixture.dialogs.shown.getLast().regions().stream()
            .filter(region -> region.action().startsWith("select.")).count());
        fixture.invoke(session, "inspect");
        assertTrue(fixture.dialogs.shown.getLast().body().toString().contains("root/"));
        session.close();
        fixture.flush();
    }

    @Test
    public void englishPlaygroundProducesNormalAndInspectedPixelPreviews() throws IOException {
        var fixture = new VexUISessionTest.Fixture();
        var session = fixture.ui.open(fixture.player, new VexUIDebugComponentsView(480, locale("en_EN"),
            () -> CompletableFuture.completedFuture(42), false));
        fixture.flush();
        var preview = new VexUIPackPreview();
        Path output = Objects.requireNonNull(Path.of(System.getProperty("vexui.preview")).getParent());
        Files.createDirectories(output);
        ImageIO.write(preview.render(fixture.dialogs.shown.getLast()), "png", output.resolve("components.png").toFile());
        fixture.invoke(session, "inspect");
        ImageIO.write(preview.render(fixture.dialogs.shown.getLast()), "png",
            output.resolve("components-inspect.png").toFile());
        fixture.invoke(session, "tab.settings");
        fixture.invoke(session, "theme");
        fixture.invoke(session, "reset");
        assertTrue(text(fixture.dialogs.frames.getLast()).contains("Confirm reset"));
    }

    @Test
    public void selectionsEnforceSingleMultipleAndDisabledChoicesAndQuantitiesCannotOverflow() {
        var options = List.of(new VexUIOption("a", Component.text("A")), new VexUIOption("b", Component.text("B")),
            new VexUIOption("locked", Component.text("Locked"), Component.empty(), false));
        var single = new VexUISelectionGroup(options, false, Set.of("a"));
        single.select("b");
        single.select("locked");
        assertEquals(Set.of("b"), single.selected().get());
        var multiple = new VexUISelectionGroup(options, true, Set.of("a"));
        multiple.select("b");
        assertEquals(Set.of("a", "b"), multiple.selected().get());
        multiple.select("a");
        assertEquals(Set.of("b"), multiple.selected().get());
        assertThrows(IllegalArgumentException.class, () -> multiple.select("unknown"));
        AtomicInteger result = new AtomicInteger();
        Map<String, VexUIAction> actions = new LinkedHashMap<>();
        new VexUIQuantitySelector(2, 1, Integer.MAX_VALUE, Integer.MAX_VALUE).controls("count", Component::text,
            Component::text, result::set, VexUIStyleSheet.defaults(), actions);
        actions.get("count.plus2147483647").execute(null);
        assertEquals(Integer.MAX_VALUE, result.get());
        actions.get("count.minus2147483647").execute(null);
        assertEquals(1, result.get());
    }

    @Test
    public void pluginThemesRefreshOwnedViewsAndSessionOverridesRemainLocal() {
        var fixture = new VexUISessionTest.Fixture();
        var session = fixture.ui.open(fixture.player, viewer -> new VexUIFrame(Component.text("Theme"),
            Component.text("Close"), 200, VexUIButton.text(Component.text("Action"), "action"),
            Map.of("action", ignored -> { })));
        fixture.flush();
        var base = VexUIStyleSheet.defaults();
        var themed = new VexUIStyleSheet(new VexUITheme(0x382C45, 0x17141F, 0xB69ACB, 0x756182), base.primary(),
            base.selected(), base.danger(), base.disabled(), base.panel(), 6, 5, 28, 70);
        fixture.ui.setStyleSheet(themed);
        fixture.flush();
        assertEquals(2, fixture.dialogs.shown.size());
        assertEquals(28, fixture.dialogs.shown.getLast().regions().getFirst().bounds().height());
        session.setStyleSheet(base);
        fixture.flush();
        assertEquals(20, fixture.dialogs.shown.getLast().regions().getFirst().bounds().height());
        session.setStyleSheet(null);
        fixture.flush();
        assertEquals(themed, session.styleSheet());
        var other = fixture.services.scoped(() -> "other");
        VexUIRegistration.owner(other);
        other.registerQueuedServices();
        assertEquals(base, other.require(VexUIService.class).getStyleSheet());
        other.unregisterOwnedServices();
    }

    @Test
    public void developerOverlaysPreserveNativeActionsAndLayoutErrorsIdentifyTheOccurrence() throws IOException {
        var frame = new VexUIFrame(Component.text("Inspect"), Component.text("Close"), 180,
            VexUIButton.text(Component.text("Action"), "action"), Map.of("action", ignored -> { }));
        var renderer = new VexUIRenderer(new VexUIMetrics());
        var plain = renderer.render(frame, (action, sound) -> ClickEvent.custom(Key.key("test", action), null),
            VexUIStyleSheet.defaults(), VexUIDebugOptions.disabled());
        var inspected = renderer.render(frame, (action, sound) -> ClickEvent.custom(Key.key("test", action), null),
            VexUIStyleSheet.defaults(), VexUIDebugOptions.inspect());
        assertEquals(plain.regions(), inspected.regions());
        assertEquals(plain.height(), inspected.height());
        var first = new VexUIPackPreview();
        first.render(plain);
        var second = new VexUIPackPreview();
        second.render(inspected);
        for (int y = 0; y < plain.height(); y++) {
            for (int x = 0; x < plain.width(); x++) {
                assertEquals(first.actionAt(x + 0.5, y + 0.5), second.actionAt(x + 0.5, y + 0.5));
            }
        }
        var invalid = new VexUIColumn(new VexUISize(-1, 15, 0), 0,
            List.of(new VexUIText(Component.text("One")), new VexUIText(Component.text("Two"))));
        var tree = new VexUIColumn(4, new VexUIText(Component.text("Header")), invalid);
        var layout = new VexUILayout(new VexUITextEngine(new VexUIMetrics()));
        var error = assertThrows(VexUILayoutException.class, () -> layout.layout(tree, 180));
        assertEquals("root/Column/Column[1]", error.getPath());
    }

    private static boolean enabled(final VexUISessionTest.Fixture fixture, final String action) {
        return fixture.dialogs.shown.getLast().regions().stream().anyMatch(region -> region.action().equals(action));
    }

    private static String text(final VexUIFrame frame) {
        // Labels are rendered into the body, while the immutable tree also includes state-dependent content.
        return frame.content().toString();
    }

    private static BiFunction<String, Map<String, String>, Component> locale(final String language) throws IOException {
        Path resources = Path.of(System.getProperty("vexui.source"), "vexcore-paper", "src", "main", "resources",
            "languages", language, "vexui");
        var components = YamlConfigurationLoader.builder().path(resources.resolve("components.yml")).build().load();
        var navigation = YamlConfigurationLoader.builder().path(resources.resolve("navigation.yml")).build().load();
        var mini = MiniMessage.builder().tags(TagResolver.builder().resolver(StandardTags.defaults())
            .resolver(TagResolver.resolver("tailwind", (arguments, context) -> {
                int color = switch (arguments.pop().value()) {
                    case "violet" -> 0xBEA3D1;
                    case "amber" -> 0xF2C46C;
                    case "lime" -> 0xA8C98D;
                    case "red" -> 0xE18888;
                    default -> 0xD4CFDB;
                };
                while (arguments.hasNext()) {
                    arguments.pop();
                }
                return Tag.styling(TextColor.color(color));
            })).build()).build();
        return (key, replacements) -> {
            boolean nav = key.startsWith("vexui.navigation.");
            String path = key.substring(nav ? "vexui.navigation.".length() : "vexui.components.".length());
            String value = (nav ? navigation : components).node((Object[]) path.split("\\.")).getString();
            if (value == null) {
                throw new AssertionError("Missing " + language + " label " + key);
            }
            for (var entry : replacements.entrySet()) {
                value = value.replace("%" + entry.getKey() + "%", entry.getValue());
            }
            return mini.deserialize(value);
        };
    }
}
