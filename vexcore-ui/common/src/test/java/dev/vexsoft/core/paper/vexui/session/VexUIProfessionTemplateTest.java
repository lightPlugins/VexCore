package dev.vexsoft.core.paper.vexui.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.paper.vexui.VexUIFrame;
import dev.vexsoft.core.paper.vexui.action.VexUIClickSound;
import dev.vexsoft.core.paper.vexui.element.VexUIButton;
import dev.vexsoft.core.paper.vexui.element.VexUIColumn;
import dev.vexsoft.core.paper.vexui.element.VexUIElement;
import dev.vexsoft.core.paper.vexui.element.VexUIEntrance;
import dev.vexsoft.core.paper.vexui.element.VexUIHotspot;
import dev.vexsoft.core.paper.vexui.element.VexUIItemIcon;
import dev.vexsoft.core.paper.vexui.element.VexUIMap;
import dev.vexsoft.core.paper.vexui.element.VexUIPanel;
import dev.vexsoft.core.paper.vexui.element.VexUIRow;
import dev.vexsoft.core.paper.vexui.element.VexUISize;
import dev.vexsoft.core.paper.vexui.element.VexUISpacer;
import dev.vexsoft.core.paper.vexui.element.VexUIText;
import dev.vexsoft.core.paper.vexui.element.VexUITexture;
import dev.vexsoft.core.paper.vexui.layout.VexUIBounds;
import dev.vexsoft.core.paper.vexui.render.VexUIMetrics;
import dev.vexsoft.core.paper.vexui.render.VexUIRenderedFrame;
import dev.vexsoft.core.paper.vexui.render.VexUIRenderer;
import dev.vexsoft.core.paper.vexui.render.VexUITextEngine;
import dev.vexsoft.core.paper.vexui.state.VexUIState;
import dev.vexsoft.core.paper.vexui.state.VexUISubscription;
import dev.vexsoft.core.paper.vexui.style.VexUITheme;
import dev.vexsoft.core.paper.vexui.template.VexUIIcon;
import dev.vexsoft.core.paper.vexui.template.VexUITemplateLayout;
import dev.vexsoft.core.paper.vexui.template.profession.VexUIProfessionProvider;
import dev.vexsoft.core.paper.vexui.template.profession.VexUIProfessionSnapshot;
import dev.vexsoft.core.paper.vexui.template.profession.VexUIProfessionSnapshot.Entry;
import dev.vexsoft.core.paper.vexui.template.profession.VexUIProfessionSnapshot.Milestone;
import dev.vexsoft.core.paper.vexui.template.profession.VexUIProfessionSnapshot.Profession;
import dev.vexsoft.core.paper.vexui.template.profession.VexUIProfessionSnapshot.Status;
import dev.vexsoft.core.paper.vexui.template.profession.VexUIProfessionTemplate;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.IntStream;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentIteratorType;
import net.kyori.adventure.text.ObjectComponent;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.object.SpriteObjectContents;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

/** Verifies native dialog layout, profession navigation, reward collections and resource pack glyphs. */
public final class VexUIProfessionTemplateTest {

    private static final VexUIMetrics METRICS = new VexUIMetrics();

    @Test
    public void centersGermanIconLabelsAndKeepsTheFullNativeTabsClickable() {
        var fixture = new VexUISessionTest.Fixture();
        var provider = new Provider();
        provider.scene = true;
        provider.unthemed = "job_5";
        var session = fixture.ui.open(fixture.player, new VexUIProfessionTemplate().createView(provider));
        fixture.flush();
        var frame = fixture.dialogs.frames.getLast();
        var hits = fixture.dialogs.shown.getLast().regions();
        var firstJob = hitBounds(hits, "profession.job_0");
        var secondJob = hitBounds(hits, "profession.job_1");
        assertEquals(86, firstJob.width());
        assertEquals(24, secondJob.width());
        assertEquals(27, firstJob.height());
        assertControlContent(frame, firstJob, 24, 0xE005, "Bergbau");
        assertControlContent(frame, secondJob, 24, 0xE005, "");
        var unthemed = hitBounds(hits, "profession.job_5");
        var fallback = ((VexUIMap) frame.content()).layers().stream().filter(layer ->
            layer.element() instanceof VexUIItemIcon && layer.y() >= unthemed.y() && layer.x() >= unthemed.x()
                && layer.x() < unthemed.x() + unthemed.width()).findFirst().orElseThrow();
        assertEquals(unthemed.x() + 4, fallback.x());
        assertEquals(unthemed.y() + 4, fallback.y());

        var back = hitBounds(hits, "navigation.back");
        assertEquals(frame.width(), back.x() + back.width());
        assertEquals(new VexUIBounds(344, 18, 60, 18), back);
        assertControlContent(frame, back, 18, 0xE004, "Zurück");
        assertControlContent(frame, hitBounds(hits, "scene.catalog"), 18, 0xE02C, "Berufe");
        assertTrue(((VexUIMap) frame.content()).layers().stream().noneMatch(layer -> layer.y() >= 198));
        assertPathActions(frame);

        fixture.invoke(session, "profession.job_1");
        frame = fixture.dialogs.frames.getLast();
        hits = fixture.dialogs.shown.getLast().regions();
        assertControlContent(frame, hitBounds(hits, "profession.job_0"), 24, 0xE005, "");
        var drawn = assertControlContent(frame, hitBounds(hits, "profession.job_1"), 24, 0xE005, "Land");
        assertTrue(drawn.endsWith("..."), "A long job name stays inside its selected tab");

        fixture.invoke(session, "navigation.back");
        assertTrue(session.isClosed());
        assertTrue(provider.closed);
    }

    private static void assertPathActions(final VexUIFrame frame) {
        for (var id : frame.actions().keySet()) {
            assertTrue(id.matches("(level\\.\\d+|profession\\.[^.]+|professions\\.(previous|next)|"
                + "page\\.(previous|next)|navigation\\.back|scene\\.catalog)"), id);
        }
    }

    private static VexUIBounds hitBounds(final List<VexUIRenderedFrame.HitRegion> regions, final String action) {
        return regions.stream().filter(region -> region.action().equals(action)).findFirst().orElseThrow().bounds();
    }

    @Test
    public void jobChangesSpringFromThePreviousStripInBothDirectionsWithFinalHitTargets() {
        var fixture = new VexUISessionTest.Fixture();
        var provider = new Provider();
        provider.scene = true;
        var session = fixture.ui.open(fixture.player, new VexUIProfessionTemplate().createView(provider));
        fixture.flush();
        for (int job : List.of(4, 0)) {
            var previous = fixture.dialogs.frames.getLast();
            int firstFrame = fixture.dialogs.frames.size();
            fixture.invoke(session, "profession.job_" + job);
            var entering = fixture.dialogs.frames.get(firstFrame);
            var positions = job == 4 ? List.of(78, 105, 132, 159, 186, 275, 302)
                : List.of(78, 167, 194, 221, 248, 275, 302);
            assertJobTransition(previous, entering, fixture.dialogs.shown.get(firstFrame), 0, job, positions);
            assertTrue(jobCaptions(entering).isEmpty());
            assertEquals(List.of(provider.get().professions().get(job).name()), jobCaptions(fixture.dialogs.frames.getLast()));
        }

        fixture.invoke(session, "page.next");
        var pathPage = fixture.dialogs.frames.getLast();
        for (int y : List.of(171, 175)) {
            for (var layer : jobArt(pathPage, y)) {
                assertEquals(VexUIEntrance.SETTLED, ((VexUITexture) layer.element()).tint());
            }
        }
        var path = ((VexUIMap) pathPage.content()).layers();
        assertTrue(path.stream().anyMatch(layer -> layer.element() instanceof VexUITexture sprite
            && sprite.outgoing() && sprite.fontPrefix().equals(Key.key("test:professions/job_0"))));
    }

    @Test
    public void selectingTheEighthJobExpandsFromTheCenteredSingleTabOnItsOwnPage() {
        var fixture = new VexUISessionTest.Fixture();
        var provider = new Provider();
        provider.scene = true;
        var session = fixture.ui.open(fixture.player, new VexUIProfessionTemplate().createView(provider));
        fixture.flush();
        fixture.invoke(session, "professions.next");
        var previous = fixture.dialogs.frames.getLast();
        var oldButton = jobArt(previous, 171).getFirst();
        assertEquals(190, oldButton.x());
        assertEquals(24, ((VexUITexture) oldButton.element()).width());
        assertTrue(jobCaptions(previous).isEmpty());
        int firstFrame = fixture.dialogs.frames.size();
        fixture.invoke(session, "profession.job_7");
        var entering = fixture.dialogs.frames.get(firstFrame);
        assertJobTransition(previous, entering, fixture.dialogs.shown.get(firstFrame), 7, 7, List.of(159));
        assertTrue(jobCaptions(entering).isEmpty());
        assertEquals(List.of(provider.get().professions().get(7).name()), jobCaptions(fixture.dialogs.frames.getLast()));
    }

    private static void assertJobTransition(final VexUIFrame previous, final VexUIFrame entering,
                                            final VexUIRenderedFrame rendered, final int firstJob, final int selected,
                                            final List<Integer> expectedPositions) {
        var oldTabs = jobArt(previous, 171);
        var newTabs = jobArt(entering, 171);
        var oldIcons = jobArt(previous, 175);
        var newIcons = jobArt(entering, 175);
        assertEquals(expectedPositions, newTabs.stream().map(VexUIMap.Layer::x).toList());
        assertEquals(expectedPositions.size(), oldTabs.size());
        assertEquals(oldTabs.size(), oldIcons.size());
        assertEquals(oldTabs.size(), newIcons.size());
        for (int slot = 0; slot < newTabs.size(); slot++) {
            int job = firstJob + slot;
            var before = oldTabs.get(slot);
            var after = newTabs.get(slot);
            var texture = (VexUITexture) after.element();
            int width = job == selected ? 86 : 24;
            assertEquals(width, texture.width());
            assertEquals(new VexUIBounds(after.x(), 171, width, 27), hitBounds(rendered.regions(), "profession.job_" + job));
            int oldWidth = ((VexUITexture) before.element()).width();
            assertEquals(expectedJobTint(before.x() - after.x(), oldWidth - width), texture.tint());

            var oldIcon = oldIcons.get(slot);
            var newIcon = newIcons.get(slot);
            var icon = (VexUITexture) newIcon.element();
            var asset = Key.key("test:professions/job_" + job);
            assertEquals(asset, ((VexUITexture) oldIcon.element()).fontPrefix());
            assertEquals(asset, icon.fontPrefix());
            assertEquals(16, icon.width());
            assertEquals(expectedJobTint(oldIcon.x() - newIcon.x(), 0), icon.tint());
        }
    }

    private static TextColor expectedJobTint(final int x, final int width) {
        return x == 0 && width == 0 ? VexUIEntrance.SETTLED : VexUIEntrance.jobTabColor(100, x, width);
    }

    private static List<VexUIMap.Layer> jobArt(final VexUIFrame frame, final int y) {
        return ((VexUIMap) frame.content()).layers().stream()
            .filter(layer -> layer.y() == y && layer.element() instanceof VexUITexture).toList();
    }

    private static List<Component> jobCaptions(final VexUIFrame frame) {
        return ((VexUIMap) frame.content()).layers().stream().filter(layer -> layer.y() == 177)
            .map(VexUIMap.Layer::element).filter(VexUIText.class::isInstance).map(VexUIText.class::cast)
            .map(VexUIText::text).toList();
    }

    private static String assertControlContent(final VexUIFrame frame, final VexUIBounds cell, final int skinHeight,
                                               final int glyph, final String expectedStart) {
        var layers = ((VexUIMap) frame.content()).layers().stream().filter(layer -> layer.x() >= cell.x()
            && layer.x() < cell.x() + cell.width() && layer.y() >= cell.y() && layer.y() < cell.y() + skinHeight).toList();
        var icon = layers.stream().filter(layer -> layer.element() instanceof VexUITexture sprite
            && sprite.codePoint() == glyph).findFirst().orElseThrow();
        var texture = (VexUITexture) icon.element();
        assertEquals(skinHeight / 2.0, icon.y() - cell.y() + texture.height() / 2.0, 1, expectedStart);
        int right = icon.x() + texture.width();
        String drawn = "";
        if (!expectedStart.isEmpty()) {
            var layer = layers.stream().filter(value -> value.element() instanceof VexUIText).findFirst().orElseThrow();
            var label = (VexUIText) layer.element();
            var metrics = new VexUITextEngine(METRICS);
            var fitted = metrics.fit(label.text(), layer.width(), 0);
            drawn = PlainTextComponentSerializer.plainText().serialize(fitted);
            assertTrue(drawn.startsWith(expectedStart), drawn);
            int width = metrics.width(fitted);
            int x = layer.x() + (label.centered() ? (layer.width() - width) / 2 : 0);
            assertTrue(x >= right && x + width <= cell.x() + cell.width(), drawn);
            // The vanilla uppercase ink starts two pixels below this text layer's origin.
            assertEquals(skinHeight / 2.0, layer.y() - cell.y() + 5.5, 1, drawn);
            right = x + width;
        }
        int leftMargin = icon.x() - cell.x();
        int rightMargin = cell.x() + cell.width() - right;
        assertTrue(Math.abs(leftMargin - rightMargin) <= 2, drawn + ": margins " + leftMargin + " / " + rightMargin);
        return drawn;
    }

    @Test
    public void pathArrowsStayStillWhileTheIslandsTransition() {
        var fixture = new VexUISessionTest.Fixture();
        var provider = new Provider();
        provider.scene = true;
        var session = fixture.ui.open(fixture.player, new VexUIProfessionTemplate().createView(provider));
        fixture.flush();
        assertStaticNavigation(fixture.dialogs.frames.getLast(), List.of(0xE004, 0xE005));

        fixture.invoke(session, "page.previous");
        var firstPage = fixture.dialogs.frames.getLast();
        assertStaticNavigation(firstPage, List.of(0xE005));
        var layers = ((VexUIMap) firstPage.content()).layers();
        assertTrue(layers.stream().anyMatch(layer -> layer.element() instanceof VexUITexture sprite
            && sprite.fontPrefix().equals(Key.key("test:professions/job_0")) && sprite.codePoint() == 0xE000
            && sprite.outgoing()));
        assertPathActions(firstPage);
    }

    private static void assertStaticNavigation(final VexUIFrame frame, final List<Integer> expectedArrows) {
        var arrows = new ArrayList<Integer>();
        for (var layer : ((VexUIMap) frame.content()).layers()) {
            if (!(layer.element() instanceof VexUITexture sprite)) {
                continue;
            }
            if (layer.y() == 81 && (layer.x() == 4 || layer.x() == 389)) {
                arrows.add(sprite.codePoint());
            } else {
                continue;
            }
            assertEquals(VexUIEntrance.SETTLED, sprite.tint());
            assertFalse(sprite.outgoing());
        }
        assertEquals(expectedArrows, arrows);
    }

    @Test
    public void pagingTheIslandPathInEitherDirectionOnlyAnimatesTheIslandsAndTheirConnections() {
        var fixture = new VexUISessionTest.Fixture();
        var provider = new Provider();
        provider.scene = true;
        var session = fixture.ui.open(fixture.player, new VexUIProfessionTemplate().createView(provider));
        fixture.flush();
        var theme = Key.key("test:professions/job_0");
        var panorama = Key.key("test:professions/job_0/background");
        for (String direction : List.of("page.next", "page.previous")) {
            fixture.invoke(session, direction);
            var frame = fixture.dialogs.frames.getLast();
            assertPanorama(frame, panorama, Map.of(panorama, VexUIEntrance.SETTLED));
            var layers = ((VexUIMap) frame.content()).layers();
            var islands = layers.stream().map(VexUIMap.Layer::element)
                .filter(VexUITexture.class::isInstance).map(VexUITexture.class::cast)
                .filter(sprite -> theme.equals(sprite.fontPrefix()) && sprite.codePoint() >= 0xE000
                    && sprite.codePoint() <= 0xE004).toList();
            assertEquals(5, islands.stream().filter(VexUITexture::outgoing).count());
            assertEquals(5, islands.stream().filter(sprite -> !sprite.outgoing()).count());
            for (var layer : layers) {
                if (!(layer.element() instanceof VexUITexture sprite)) {
                    continue;
                }
                boolean islandPath = layer.x() >= 27 && layer.x() < 377 && layer.y() >= 63 && layer.y() <= 117;
                assertEquals(!islandPath, VexUIEntrance.SETTLED.equals(sprite.tint()), direction + " " + layer);
                if (!islandPath) {
                    assertFalse(sprite.outgoing());
                }
            }
        }
    }

    @Test
    public void firstAndReopenedDialogsSendSettledBitmapArtAndOnlyAJobChangeCrossfades() {
        var fixture = new VexUISessionTest.Fixture();
        var provider = new Provider();
        provider.scene = true;
        var session = fixture.ui.open(fixture.player, new VexUIProfessionTemplate().createView(provider));
        fixture.flush();
        var first = Key.key("test:professions/job_0/background");
        var second = Key.key("test:professions/job_1/background");
        assertPanorama(fixture.dialogs.frames.getFirst(), first, Map.of(first, VexUIEntrance.SETTLED));

        fixture.invoke(session, "profession.job_1");
        long tick = fixture.player.getWorld().getGameTime();
        assertPanorama(fixture.dialogs.frames.getLast(), second, Map.of(
            first, VexUIEntrance.exitColor(tick, 0), second, VexUIEntrance.color(tick, 0)));

        fixture.invoke(session, "page.next");
        assertPanorama(fixture.dialogs.frames.getLast(), second, Map.of(second, VexUIEntrance.SETTLED));

        session.close();
        fixture.flush();
        int previousFrames = fixture.dialogs.frames.size();
        var reopened = new Provider();
        reopened.scene = true;
        fixture.ui.open(fixture.player, new VexUIProfessionTemplate().createView(reopened, "job_1"));
        fixture.flush();
        assertPanorama(fixture.dialogs.frames.get(previousFrames), second, Map.of(second, VexUIEntrance.SETTLED));
    }

    private static void assertPanorama(final VexUIFrame frame, final Key current,
                                       final Map<Key, TextColor> expected) {
        assertPanorama(frame, current, expected, List.of());
    }

    private record AtlasTheme(String name, int animation) {}

    @Test
    public void atlasThemesHaveTheCurrentBitmapUnderneathAndCrossfadeOnlyOnJobChanges() {
        var fixture = new VexUISessionTest.Fixture();
        var provider = new Provider();
        provider.scene = true;
        provider.panoramaSprites = true;
        var session = fixture.ui.open(fixture.player, new VexUIProfessionTemplate().createView(provider));
        fixture.flush();
        var first = Key.key("test:professions/job_0/background");
        var second = Key.key("test:professions/job_1/background");
        var underlay = TextColor.color(0xFFFFFC);
        var firstSettled = new AtlasTheme("job_0", 4094);
        var secondSettled = new AtlasTheme("job_1", 4094);
        assertPanorama(fixture.dialogs.frames.getFirst(), first, Map.of(first, underlay), List.of(firstSettled));

        int jobFrame = fixture.dialogs.frames.size();
        fixture.invoke(session, "profession.job_1");
        int tick = Math.floorMod(fixture.player.getWorld().getGameTime(), 2000);
        assertPanorama(fixture.dialogs.frames.get(jobFrame), second, Map.of(second, underlay),
            List.of(new AtlasTheme("job_0", 2000 + tick), new AtlasTheme("job_1", tick)));
        fixture.invoke(session, "page.next");
        assertPanorama(fixture.dialogs.frames.getLast(), second, Map.of(second, underlay), List.of(secondSettled));
        fixture.invoke(session, "level.12");
        assertPanorama(fixture.dialogs.frames.getLast(), second, Map.of(second, underlay), List.of(secondSettled));

        session.close();
        fixture.flush();
        int nextFrame = fixture.dialogs.frames.size();
        var reopened = new Provider();
        reopened.scene = true;
        reopened.panoramaSprites = true;
        fixture.ui.open(fixture.player, new VexUIProfessionTemplate().createView(reopened, "job_1"));
        fixture.flush();
        assertPanorama(fixture.dialogs.frames.get(nextFrame), second, Map.of(second, underlay), List.of(secondSettled));
    }

    private static void assertPanorama(final VexUIFrame frame, final Key current,
                                       final Map<Key, TextColor> expected, final List<AtlasTheme> atlasThemes) {
        var glyphs = new HashMap<Key, StringBuilder>();
        var padding = new ArrayList<Integer>();
        int nativeX = 0;
        int missingFontX = 0;
        int spriteCount = 0;
        for (var component : frame.title().iterable(ComponentIteratorType.DEPTH_FIRST)) {
            if (component instanceof ObjectComponent object) {
                assertTrue(spriteCount < 3 * atlasThemes.size(), "Unexpected extra atlas row");
                var theme = atlasThemes.get(spriteCount / 3);
                int row = spriteCount % 3;
                var sprite = assertInstanceOf(SpriteObjectContents.class, object.contents());
                assertEquals(Key.key("minecraft:gui"), sprite.atlas());
                assertEquals(Key.key("test:professions/" + theme.name() + "/panorama_" + row), sprite.sprite());
                assertEquals(Component.empty(), object.fallback());
                int payload = theme.animation();
                assertEquals(TextColor.color(0xC0 | (payload >> 8), 0xB0 + row * 16 + (payload >> 4 & 15),
                    0xF0 | (payload & 15)), object.color());
                assertEquals(4032 + 8 * spriteCount, nativeX, "Shaderless object stays past the right edge");
                nativeX += 8;
                spriteCount++;
                continue;
            }
            var text = assertInstanceOf(TextComponent.class, component);
            if (text.content().isEmpty()) {
                continue;
            }
            if (Key.key("minecraft:default").equals(text.font())) {
                assertTrue(text.content().chars().allMatch(point -> point == ' '));
                assertFalse(text.hasDecoration(TextDecoration.BOLD));
                int width = text.content().length() * METRICS.advance(' ', false);
                padding.add(width);
                nativeX += width;
                missingFontX += width;
                continue;
            }
            assertTrue(expected.containsKey(text.font()), "Every custom advance comes from its panorama font");
            var stream = glyphs.computeIfAbsent(text.font(), ignored -> new StringBuilder());
            for (int point : text.content().codePoints().toArray()) {
                assertTrue(missingFontX >= 4032, "Missing bitmap glyphs must stay offscreen");
                missingFontX += 6;
                stream.appendCodePoint(point);
                if (point == 0xE10C) {
                    nativeX--;
                } else if (point == 0xE10D) {
                    nativeX -= 1008;
                } else {
                    int column = (point - 0xE100) % 4;
                    int origin = text.font().equals(current) ? 0 : 4032;
                    assertEquals(origin + 252 * column, nativeX);
                    assertEquals(expected.get(text.font()), text.color());
                    nativeX += 253;
                }
            }
        }
        assertEquals(3 * atlasThemes.size(), spriteCount);
        int finalPadding = atlasThemes.isEmpty() ? 2048 : 4032;
        assertEquals(List.of(4032, finalPadding), padding);
        assertEquals(finalPadding + spriteCount * 8, nativeX, "The native warning remains in the upper right");
        assertEquals(expected.keySet(), glyphs.keySet());
        glyphs.forEach((font, stream) -> assertEquals(
            (font.equals(current) ? "\uE10D".repeat(4) : "") + panoramaRows(), stream.toString()));
    }

    private static String panoramaRows() {
        var expectedGlyphs = new StringBuilder();
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 4; column++) {
                expectedGlyphs.appendCodePoint(0xE100 + row * 4 + column).appendCodePoint(0xE10C);
            }
            expectedGlyphs.appendCodePoint(0xE10D);
        }
        return expectedGlyphs.toString();
    }

    @Test
    public void islandScenesKeepNativeTargetsWhilePagingOrOpeningJobDetails() {
        var fixture = new VexUISessionTest.Fixture();
        var provider = new Provider();
        provider.scene = true;
        var session = fixture.ui.open(fixture.player, new VexUIProfessionTemplate().createView(provider));
        fixture.flush();
        var frame = fixture.dialogs.frames.getLast();
        var scene = (VexUIMap) frame.content();
        assertEquals(404, frame.width());
        assertTrue(fixture.dialogs.shown.getLast().height() <= 230);
        assertTrue(frame.compactCloseButton());
        assertEquals(5, scene.layers().stream().filter(layer -> layer.element() instanceof VexUIHotspot target
            && target.action().startsWith("level.")).count());
        var target = fixture.dialogs.shown.getLast().regions().stream()
            .filter(region -> region.action().equals("level.7")).findFirst().orElseThrow();
        assertEquals(0, target.bounds().y() % 9);
        assertEquals(54, target.bounds().height());
        fixture.invoke(session, "page.next");
        frame = fixture.dialogs.frames.getLast();
        assertTrue(frame.actions().containsKey("level.15"));
        assertFalse(frame.actions().containsKey("level.7"));
        assertTrue(((VexUIMap) frame.content()).layers().stream()
            .anyMatch(layer -> layer.element() instanceof VexUITexture sprite && sprite.outgoing()));
        var pathExit = fixture.dialogs.closeAction;
        fixture.invoke(session, "level.12");
        frame = fixture.dialogs.frames.getLast();
        assertTrue(frame.actions().containsKey("popup.close"));
        assertTrue(sceneLabels(fixture).contains("Keine Levelbelohnung"));
        assertFalse(frame.actions().keySet().stream().anyMatch(action -> action.startsWith("claim.")));
        assertEquals(404, frame.width());
        assertTrue(frame.title().toString().contains("job_0/background"));
        assertTrue(fixture.dialogs.shown.getLast().height() < 280);
        var popupExit = fixture.dialogs.closeAction;
        fixture.coordinator.dispatch(fixture.player, pathExit, null);
        fixture.flush();
        assertTrue(fixture.dialogs.frames.getLast().actions().containsKey("popup.close"));
        fixture.coordinator.dispatch(fixture.player, popupExit, null);
        fixture.flush();
        assertFalse(session.isClosed());
        assertTrue(fixture.dialogs.frames.getLast().actions().containsKey("level.12"));
        assertFalse(fixture.dialogs.frames.getLast().actions().containsKey("level.7"));
        fixture.coordinator.dispatch(fixture.player, popupExit, null);
        fixture.flush();
        assertFalse(session.isClosed(), "A repeated exit from the previous popup must not close the island map");
        fixture.invoke(session, "professions.next");
        fixture.invoke(session, "profession.job_7");
        assertTrue(fixture.dialogs.frames.getLast().title().toString().contains("job_7/background"));
        fixture.invoke(session, "scene.catalog");
        fixture.invoke(session, "sort");
        assertTrue(fixture.dialogs.frames.getLast().actions().containsKey("filter"));
        var back = hitBounds(fixture.dialogs.shown.getLast().regions(), "scene.return");
        assertEquals(frame.width(), back.x() + back.width());
        assertTrue(back.y() < 36);
        fixture.invoke(session, "scene.return");
        assertPathActions(fixture.dialogs.frames.getLast());
    }

    @Test
    public void islandRewardPopupKeepsItsMapPageAndIsolatesAllFourteenRewardsFromBackgroundActions() {
        var fixture = new VexUISessionTest.Fixture();
        var provider = new Provider();
        provider.scene = true;
        var pending = new CompletableFuture<Boolean>();
        provider.claimResult = pending;
        var session = fixture.ui.open(fixture.player, new VexUIProfessionTemplate().createView(provider, "job_1"));
        fixture.flush();
        fixture.invoke(session, "page.previous");
        var path = fixture.dialogs.frames.getLast();
        var map = (VexUIMap) path.content();
        var islands = map.layers().stream().filter(layer -> layer.element() instanceof VexUITexture sprite
            && !sprite.outgoing() && sprite.fontPrefix().equals(Key.key("test:professions/job_1"))
            && sprite.codePoint() >= 0xE000 && sprite.codePoint() <= 0xE004).toList();
        assertEquals(5, islands.size());
        int height = fixture.dialogs.shown.getLast().height();
        fixture.invoke(session, "level.5");

        var popup = fixture.dialogs.frames.getLast();
        assertEquals(height, fixture.dialogs.shown.getLast().height());
        assertEquals(path.width(), popup.width());
        var popupLayers = ((VexUIMap) popup.content()).layers();
        // The centered popup covers the three inner islands. The two edge islands remain at their map positions.
        for (var island : List.of(islands.getFirst(), islands.getLast())) {
            assertTrue(popupLayers.stream().anyMatch(layer -> layer.x() == island.x() && layer.y() == island.y()
                && layer.element() instanceof VexUITexture sprite && !sprite.outgoing()
                && sprite.codePoint() == ((VexUITexture) island.element()).codePoint()
                && sprite.fontPrefix().equals(Key.key("test:professions/job_1"))));
        }
        assertTrue(showsAsset(fixture, 0xE036), "The left bridge reaches the popup edge");
        assertTrue(showsAsset(fixture, 0xE037), "The right bridge continues past the popup");
        assertFalse(popupLayers.stream().anyMatch(layer -> layer.element() instanceof VexUITexture sprite
            && sprite.outgoing()), "Departing map art must not paint across the popup");
        assertTrue(Collections.disjoint(path.actions().keySet(), popup.actions().keySet()));
        assertTrue(fixture.dialogs.shown.getLast().regions().stream()
            .noneMatch(region -> path.actions().containsKey(region.action())));

        var names = new ArrayList<String>();
        for (int page = 0; page < 5; page++) {
            if (page > 0) {
                fixture.invoke(session, "popup.next");
            }
            var rows = sceneLabels(fixture).stream().filter(label -> label.endsWith("x Eisenbarren")).toList();
            assertEquals(page == 4 ? 2 : 3, rows.size());
            names.addAll(rows);
        }
        assertEquals(IntStream.rangeClosed(1, 14).mapToObj(i -> i + "x Eisenbarren").toList(), names);
        assertFalse(fixture.dialogs.frames.getLast().actions().containsKey("popup.next"));
        fixture.invoke(session, "popup.previous");
        assertTrue(sceneLabels(fixture).contains("10x Eisenbarren"));
        fixture.invoke(session, "claim.level.5");
        assertFalse(fixture.dialogs.frames.getLast().actions().containsKey("claim.level.5"));
        pending.complete(false);
        fixture.flush();
        assertTrue(fixture.dialogs.frames.getLast().actions().containsKey("claim.level.5"), "A failed claim can be retried");
        provider.claimResult = CompletableFuture.completedFuture(true);
        fixture.invoke(session, "claim.level.5");
        assertEquals(List.of(Map.entry("job_1", 5), Map.entry("job_1", 5)), provider.claims);
        assertFalse(fixture.dialogs.frames.getLast().actions().containsKey("claim.level.5"));
        fixture.invoke(session, "popup.close");
        assertEquals(path.actions().keySet(), fixture.dialogs.frames.getLast().actions().keySet());

        fixture.invoke(session, "page.next");
        fixture.invoke(session, "level.10");
        assertTrue(sceneLabels(fixture).contains("Gesperrt"));
        assertEquals(List.of("1x Eisenbarren", "2x Eisenbarren"),
            sceneLabels(fixture).stream().filter(label -> label.endsWith("x Eisenbarren")).toList());
        assertFalse(fixture.dialogs.frames.getLast().actions().containsKey("claim.level.10"));
    }

    private static boolean showsAsset(final VexUISessionTest.Fixture fixture, final int code) {
        var map = (VexUIMap) fixture.dialogs.frames.getLast().content();
        return map.layers().stream().anyMatch(layer -> layer.element() instanceof VexUITexture texture
            && texture.codePoint() == code && !texture.outgoing());
    }

    private static List<String> sceneLabels(final VexUISessionTest.Fixture fixture) {
        var scene = (VexUIMap) fixture.dialogs.frames.getLast().content();
        return scene.layers().stream().map(VexUIMap.Layer::element).filter(VexUIText.class::isInstance)
            .map(VexUIText.class::cast).map(VexUIText::text).map(PlainTextComponentSerializer.plainText()::serialize)
            .toList();
    }

    @Test
    public void nativeSpaceHotspotsLeaveTheBitmapTransparentOnEveryHitRow() throws IOException {
        var area = VexUISize.fixed(36, 27);
        var map = new VexUIMap(VexUISize.fixed(120, 54), List.of(
            new VexUIMap.Layer(24, 9, 36, 27, new VexUIHotspot(area, "island", Component.text("Level")))));
        var frame = new VexUIFrame(Component.text("Scene"), Component.text("Close"), 120, map,
            Map.of("island", ignored -> { }));
        var result = new VexUIRenderer(new VexUIMetrics()).render(frame,
            id -> ClickEvent.custom(Key.key("test", id), null));
        var preview = new VexUIPackPreview();
        var pixels = preview.render(result);
        for (int y = 0; y < 54; y++) {
            for (int x = 0; x < 120; x++) {
                assertEquals(0, pixels.getRGB(x, y));
                var expected = x >= 24 && x < 60 && y >= 9 && y < 36 ? Key.key("test:island") : null;
                assertEquals(expected, preview.actionAt(x + 0.5, y + 0.5));
            }
        }
    }

    @Test
    public void collectionSoundWaitsForTheSelectedLevelGrant() {
        for (boolean granted : List.of(false, true)) {
            var fixture = new VexUISessionTest.Fixture();
            var provider = new Provider();
            provider.scene = true;
            var pending = new CompletableFuture<Boolean>();
            provider.claimResult = pending;
            var session = fixture.ui.open(fixture.player, new VexUIProfessionTemplate().createView(provider));
            fixture.flush();
            fixture.invoke(session, "page.previous");
            fixture.invoke(session, "level.5");
            fixture.invoke(session, "claim.level.5");
            assertEquals(0, fixture.sounds.stream().filter(VexUIClickSound.collect().asSound()::equals).count());
            pending.complete(granted);
            fixture.flush();
            assertEquals(granted ? 1 : 0, fixture.sounds.stream().filter(VexUIClickSound.collect().asSound()::equals).count());
            assertEquals(List.of(Map.entry("job_0", 5)), provider.claims);
        }
    }

    @Test
    public void unthemedJobRetainsLevelPagingAndCollectsOnlyFromTheOpenLevel() {
        var fixture = new VexUISessionTest.Fixture();
        var provider = new Provider();
        var session = fixture.ui.open(fixture.player, new VexUIProfessionTemplate().createView(provider, "job_1"));
        fixture.flush();
        var initial = fixture.dialogs.frames.getLast();
        assertFalse(initial.content() instanceof VexUIMap);
        assertTrue(fixture.dialogs.shown.getLast().height() <= 300);
        assertTrue(initial.actions().keySet().stream().noneMatch(id -> id.startsWith("tab.") || id.startsWith("claim.")));
        if (!initial.actions().containsKey("level.5")) {
            fixture.invoke(session, "page.previous");
        }
        fixture.invoke(session, "level.5");
        assertTrue(fixture.dialogs.frames.getLast().actions().containsKey("popup.close"));
        fixture.invoke(session, "popup.next");
        fixture.invoke(session, "claim.level.5");
        assertEquals(List.of(Map.entry("job_1", 5)), provider.claims);
        assertFalse(fixture.dialogs.frames.getLast().actions().containsKey("claim.level.5"));
        fixture.invoke(session, "popup.close");
        assertTrue(fixture.dialogs.frames.getLast().actions().containsKey("level.5"));
        fixture.invoke(session, "page.next");
        fixture.invoke(session, "level.10");
        assertFalse(fixture.dialogs.frames.getLast().actions().containsKey("claim.level.10"));
        fixture.invoke(session, "popup.close");
        fixture.invoke(session, "navigation.back");
        assertTrue(session.isClosed());
    }

    @Test
    public void panelTooltipDoesNotMaskNestedActionsOrEnableALockedClaim() throws IOException {
        var active = new VexUIButton(VexUISize.flexible(), new VexUIText(Component.text("Abholen")),
            VexUITheme.PRIMARY, true, "claim", Component.text("Claim tooltip"));
        var locked = new VexUIButton(VexUISize.flexible(), new VexUIText(Component.text("Gesperrt")),
            VexUITheme.DISABLED, false, "locked", Component.text("Locked tooltip"));
        var panel = new VexUIPanel(VexUISize.content(), 4, VexUITheme.PANEL,
            new VexUIRow(4, active, locked)).withTooltip(Component.text("\n Paket\n\n Inhalt\n"));
        var frame = new VexUIFrame(Component.text("Nested"), Component.text("Close"), 200, panel,
            Map.of("claim", ignored -> { }, "locked", ignored -> { }));
        var rendered = new VexUIRenderer(new VexUIMetrics()).render(frame,
            (action, sound) -> ClickEvent.custom(Key.key("test", action), null));
        var nativeView = new VexUIPackPreview();
        nativeView.render(rendered);
        assertEquals(1, rendered.regions().size());
        for (int y = 0; y < rendered.height(); y++) {
            for (int x = 0; x < rendered.width(); x++) {
                final int column = x;
                final int row = y;
                var expected = rendered.regions().stream().filter(region -> column >= region.bounds().x()
                    && column < region.bounds().x() + region.bounds().width() && row >= region.bounds().y()
                    && row < region.bounds().y() + region.bounds().height()).findFirst()
                    .map(region -> Key.key("test", region.action())).orElse(null);
                assertEquals(expected, nativeView.actionAt(x + 0.5, y + 0.5));
            }
        }
    }

    @Test
    public void wrappingPreservesWordsBlankLinesAndNestedStyles() {
        var text = new VexUITextEngine(new VexUIMetrics());
        var styled = Component.text("Alpha ").append(Component.text("Beta").color(TextColor.color(0xBEA3D1)))
            .append(Component.text("\n\nGamma"));
        var lines = text.wrap(styled, Math.max(text.width(Component.text("Alpha")), text.width(Component.text("Gamma"))), 0);
        assertEquals(List.of("Alpha", "Beta", "", "Gamma"), lines.stream()
            .map(PlainTextComponentSerializer.plainText()::serialize).toList());
        assertEquals(TextColor.color(0xBEA3D1), lines.get(1).children().getFirst().color());
        assertEquals(1, text.wrap(Component.text("Alpha\n"), 100, 1).size());
        assertEquals(2, text.wrap(Component.text("Alpha\n"), 100, 0).size());
    }

    @Test
    public void vanillaFontKeepsDefaultAdvancesAndRendersAccentsAndNavigationAtEveryOffset() throws IOException {
        var text = new VexUITextEngine(new VexUIMetrics());
        assertEquals(6, text.width(Component.text("A")));
        assertEquals(2, text.width(Component.text("i")));
        assertEquals(4, text.width(Component.text(" ")));
        assertEquals(7, text.width(Component.text("A").decorate(TextDecoration.BOLD)));
        assertEquals("AA...", PlainTextComponentSerializer.plainText().serialize(text.fit(Component.text("AAAA"), 18, 0)));
        List<VexUIElement> labels = new ArrayList<>();
        for (int offset = 0; offset < 9; offset++) {
            labels.add(new VexUIText(Component.text("ÄÖÜ äöü ß · « » → ✦ ★ ✓ Größe Bergbau")));
            labels.add(new VexUISpacer(new VexUISize(-1, 7, 0)));
        }
        var frame = new VexUIFrame(Component.text("Vanilla"), Component.text("Close"), 260,
            new VexUIPanel(VexUISize.content(), 4, VexUITheme.PANEL,
                new VexUIColumn(VexUISize.content(), 0, labels)), Map.of());
        var rendered = new VexUIRenderer(new VexUIMetrics()).render(frame,
            action -> ClickEvent.custom(Key.key("test", action), null));
        new VexUIPackPreview().save(rendered,
            Path.of(System.getProperty("vexui.preview")).resolveSibling("vanilla-font.png"));
    }

    static final class Provider implements VexUIProfessionProvider {
        private final VexUIState<VexUIProfessionSnapshot> state;
        private final List<Map.Entry<String, Integer>> claims = new ArrayList<>();
        private CompletionStage<Boolean> claimResult = CompletableFuture.completedFuture(true);
        private boolean closed;
        private boolean scene;
        private boolean panoramaSprites;
        private String unthemed;

        Provider() {
            List<String> names = List.of("Bergbau", "Landwirtschaft", "Holzfällen", "Schmieden", "Verzaubern", "Arkiologie");
            List<String> icons = List.of("iron_pickaxe", "iron_hoe", "iron_axe", "iron_ingot", "enchanted_book", "ender_eye");
            var rewards = IntStream.range(0, 14).mapToObj(index -> new Entry("reward_" + index,
                Component.text((index + 1) + "x Eisenbarren"), List.of(Component.text("Belohnung für deinen Fortschritt")),
                VexUIIcon.item("minecraft:item/iron_ingot"))).toList();
            var milestones = List.of(new Milestone(5, Status.AVAILABLE, rewards),
                new Milestone(10, Status.LOCKED, rewards.subList(0, 2)));
            var jobs = IntStream.range(0, 8).mapToObj(index -> new Profession("job_" + index,
                Component.text(names.get(index % names.size())).color(TextColor.color(0xBEA3D1)), List.of(),
                VexUIIcon.item("minecraft:item/" + icons.get(index % icons.size())), 7, 50, 230, 500, 0.46,
                milestones)).toList();
            state = new VexUIState<>(new VexUIProfessionSnapshot(true, jobs, false));
        }

        @Override
        public VexUIProfessionSnapshot get() {
            return state.get();
        }

        @Override
        public String sceneTheme(final String profession) {
            return scene && !profession.equals(unthemed) ? "test:professions/" + profession : null;
        }

        @Override
        public boolean hasPanoramaSprites() {
            return panoramaSprites;
        }

        @Override
        public VexUISubscription subscribe(final Runnable changed) {
            return state.subscribe(changed);
        }

        @Override
        public VexUITemplateLayout layout(final String section) {
            int rows = section.equals("main") ? 6 : section.equals("rewards") ? 2 : section.equals("levels") ? 5 : 4;
            return new VexUITemplateLayout(560, 1, rows,
                section.equals("main") ? 26 : section.equals("rewards") ? 40 : 24, 3);
        }

        @Override
        public Component text(final String key, final Map<String, String> values) {
            String label = switch (key) {
                case "main.title" -> "Deine Berufe";
                case "main.hint" -> "Beruf links wählen · Details beim Darüberfahren";
                case "main.close" -> "Schließen";
                case "main.back" -> "Zurück";
                case "main.scene_catalog" -> "Berufe";
                case "main.filter_0" -> "Alle";
                case "main.sort_0" -> "Standard";
                case "main.progress" -> "Lv. %level% / %maximum%";
                case "job.progress" -> "Lv. %level% / %maximum% · %xp% / %required% XP";
                case "main.available" -> "%available% Belohnung bereit";
                case "main.previous" -> "«";
                case "main.next" -> "»";
                case "main.page" -> "%page% / %pages%";
                case "job.title" -> "%profession%";
                case "job.remaining" -> "Noch %remaining% XP bis zum nächsten Level";
                case "levels.level" -> "Lv. %level%";
                case "levels.locked" -> "Gesperrt";
                case "levels.reached" -> "Erreicht";
                case "levels.available" -> "Belohnung bereit";
                case "levels.claimed" -> "Abgeholt";
                case "levels.preview" -> "Vorschau · Level %level%";
                case "levels.no_reward" -> "Keine Levelbelohnung";
                case "rewards.level" -> "Level %level%";
                case "rewards.locked_button" -> "Gesperrt";
                case "rewards.claimed_button" -> "Abgeholt";
                case "rewards.collect_level" -> "Abholen";
                default -> key.substring(key.indexOf('.') + 1);
            };
            for (var entry : values.entrySet()) {
                label = label.replace("%" + entry.getKey() + "%", entry.getValue());
            }
            return Component.text(label).color(TextColor.color(key.contains("collect") || key.endsWith("available")
                ? 0xF2C46C : 0xD4CFDB));
        }

        @Override
        public CompletionStage<Boolean> claim(final String profession, final int level) {
            claims.add(Map.entry(profession, level));
            return claimResult;
        }

        @Override
        public List<Component> lore(
            final String key,
            final Map<String, String> values,
            final Map<String, List<Component>> sections
        ) {
            var lines = new ArrayList<Component>();
            lines.add(Component.empty());
            lines.add(Component.text(" " + key).color(TextColor.color(0xBEA3D1)));
            lines.add(Component.empty());
            sections.values().forEach(lines::addAll);
            lines.add(Component.empty());
            return List.copyOf(lines);
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
