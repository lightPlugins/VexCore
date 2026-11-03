package dev.vexsoft.core.paper.vexui.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.paper.vexui.VexUIState;
import dev.vexsoft.core.paper.vexui.VexUIClickSound;
import dev.vexsoft.core.paper.vexui.VexUISubscription;
import dev.vexsoft.core.paper.vexui.VexUIButton;
import dev.vexsoft.core.paper.vexui.VexUIFrame;
import dev.vexsoft.core.paper.vexui.VexUIPanel;
import dev.vexsoft.core.paper.vexui.VexUIRow;
import dev.vexsoft.core.paper.vexui.VexUISize;
import dev.vexsoft.core.paper.vexui.VexUISpacer;
import dev.vexsoft.core.paper.vexui.VexUIText;
import dev.vexsoft.core.paper.vexui.VexUITheme;
import dev.vexsoft.core.paper.vexui.VexUIElement;
import dev.vexsoft.core.paper.vexui.VexUIColumn;
import dev.vexsoft.core.paper.vexui.VexUIGrid;
import dev.vexsoft.core.paper.vexui.VexUIGridCell;
import dev.vexsoft.core.paper.vexui.template.VexUIIcon;
import dev.vexsoft.core.paper.vexui.template.VexUIProfessionProvider;
import dev.vexsoft.core.paper.vexui.template.VexUIProfessionSnapshot;
import dev.vexsoft.core.paper.vexui.template.VexUIProfessionSnapshot.Claim;
import dev.vexsoft.core.paper.vexui.template.VexUIProfessionSnapshot.Entry;
import dev.vexsoft.core.paper.vexui.template.VexUIProfessionSnapshot.Milestone;
import dev.vexsoft.core.paper.vexui.template.VexUIProfessionSnapshot.Profession;
import dev.vexsoft.core.paper.vexui.template.VexUIProfessionSnapshot.Receipt;
import dev.vexsoft.core.paper.vexui.template.VexUIProfessionSnapshot.Status;
import dev.vexsoft.core.paper.vexui.template.VexUIProfessionTemplate;
import dev.vexsoft.core.paper.vexui.template.VexUITemplateLayout;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.IntStream;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

/** Decodes actual pack glyphs for progression pages and verifies padding and preview-only level navigation. */
public final class VexUIProfessionTemplateTest {

    @Test
    public void collectionSoundWaitsForSuccessfulNonemptyReceipt() {
        var fixture = new VexUISessionTest.Fixture();
        var provider = new Provider();
        var pending = new CompletableFuture<Claim>();
        provider.claimResult = pending;
        var session = fixture.ui.open(fixture.player, new VexUIProfessionTemplate().createView(provider));
        fixture.flush();
        fixture.invoke(session, "claim.all");
        assertEquals(0, fixture.sounds.stream().filter(VexUIClickSound.collect().asSound()::equals).count());
        var job = provider.get().professions().getFirst();
        pending.complete(new Claim(true, List.of(new Receipt(job.id(), job.name(), 5, job.milestones().getFirst().rewards()))));
        fixture.flush();
        assertEquals(1, fixture.sounds.stream().filter(VexUIClickSound.collect().asSound()::equals).count());

        for (var result : List.of(new Claim(false, List.of()), new Claim(true, List.of()))) {
            var rejected = new VexUISessionTest.Fixture();
            var rejectedProvider = new Provider();
            rejectedProvider.claimResult = CompletableFuture.completedFuture(result);
            var rejectedSession = rejected.ui.open(rejected.player, new VexUIProfessionTemplate().createView(rejectedProvider));
            rejected.flush();
            rejected.invoke(rejectedSession, "claim.all");
            rejected.flush();
            assertEquals(0, rejected.sounds.stream().filter(VexUIClickSound.collect().asSound()::equals).count());
        }
    }

    @Test
    public void professionAndBundlePagesKeepGeometryAndGoldAttentionWithoutClaimsOnTheLevelPath() throws IOException {
        var fixture = new VexUISessionTest.Fixture();
        var provider = new Provider();
        var session = fixture.ui.open(fixture.player, new VexUIProfessionTemplate().createView(provider));
        fixture.flush();
        save(fixture.dialogs.shown.getLast(), "professions.png");
        int height = fixture.dialogs.shown.getLast().height();
        fixture.invoke(session, "professions.next");
        assertEquals(height, fixture.dialogs.shown.getLast().height());
        assertEquals(2, fixture.dialogs.shown.getLast().regions().stream()
            .filter(region -> region.action().startsWith("profession.")).count());
        fixture.invoke(session, "professions.previous");
        fixture.invoke(session, "profession.job_0");
        assertFalse(fixture.dialogs.frames.getLast().actions().containsKey("claim.job"));
        assertFalse(fixture.dialogs.frames.getLast().actions().keySet().stream()
            .anyMatch(value -> value.startsWith("claim.level.")));
        fixture.invoke(session, "tab.rewards");
        save(fixture.dialogs.shown.getLast(), "profession-rewards.png");
        fixture.invoke(session, "bundle.5");
        assertFalse(provider.closed, "Selection stays in the same workspace");
        int bundleHeight = fixture.dialogs.shown.getLast().height();
        for (int index = 0; index < 3; index++) {
            fixture.invoke(session, "items.next");
        }
        assertEquals(bundleHeight, fixture.dialogs.shown.getLast().height());
        save(fixture.dialogs.shown.getLast(), "profession-bundle.png");
        session.close();
        fixture.flush();
        assertTrue(provider.closed);
    }

    @Test
    public void compactWorkspaceFitsTheReportedViewportAndCentersNavigationLabels() {
        var fixture = new VexUISessionTest.Fixture();
        var session = fixture.ui.open(fixture.player, new VexUIProfessionTemplate().createView(new Provider()));
        fixture.flush();
        assertFalse(fixture.dialogs.frames.getLast().actions().containsKey("search"));
        int height = fixture.dialogs.shown.getLast().height();
        for (String tab : List.of("levels", "rewards", "sources", "unlocks")) {
            fixture.invoke(session, "tab." + tab);
            var frame = fixture.dialogs.frames.getLast();
            assertEquals(height, fixture.dialogs.shown.getLast().height(), "Stable tab height: " + tab);
            assertPagersAligned(frame);
            assertFalse(frame.actions().containsKey("goal"));
            assertFalse(frame.actions().containsKey("level.rewards"));
            assertTrue(fixture.dialogs.shown.getLast().height() <= 300,
                "The standard workspace must fit the screenshot's available dialog height: " + tab);
            for (var button : buttons(fixture.dialogs.frames.getLast().content())) {
                String id = button.action();
                if (id.equals("filter") || id.equals("sort") || id.startsWith("tab.")
                    || id.endsWith(".previous") || id.endsWith(".next")) {
                    assertTrue(((VexUIText) button.content()).centered(), id);
                }
            }
        }
        fixture.invoke(session, "filter");
        fixture.invoke(session, "filter");
        assertPagersAligned(fixture.dialogs.frames.getLast());
        assertTrue(fixture.dialogs.shown.getLast().height() <= 300, "An empty filter must retain compact geometry");
    }

    @Test
    public void fiveLevelRowsAndEmptyPreviewsKeepTheCommonPagerBaselineOnTheLastPage() {
        var fixture = new VexUISessionTest.Fixture();
        var session = fixture.ui.open(fixture.player, new VexUIProfessionTemplate().createView(new Provider()));
        fixture.flush();
        var frame = fixture.dialogs.frames.getLast();
        assertEquals(5, frame.actions().keySet().stream().filter(id -> id.startsWith("level.")).count());
        int height = fixture.dialogs.shown.getLast().height();
        fixture.invoke(session, "page.previous");
        fixture.invoke(session, "level.4");
        assertPagersAligned(fixture.dialogs.frames.getLast());
        assertEquals(height, fixture.dialogs.shown.getLast().height());
        fixture.invoke(session, "level.5");
        assertPagersAligned(fixture.dialogs.frames.getLast());
        for (int index = 0; index < 3; index++) {
            fixture.invoke(session, "items.next");
        }
        assertEquals(height, fixture.dialogs.shown.getLast().height());
        assertPagersAligned(fixture.dialogs.frames.getLast());
        for (int index = 0; index < 9; index++) {
            fixture.invoke(session, "page.next");
        }
        frame = fixture.dialogs.frames.getLast();
        assertTrue(frame.actions().containsKey("level.50"));
        assertFalse(frame.actions().containsKey("level.51"));
        assertEquals(height, fixture.dialogs.shown.getLast().height());
        assertPagersAligned(frame);
    }

    @Test
    public void headerOwnsBackAndGlobalClaimWhileUnlocksFillBothColumnsAndKeepPaginationStable() throws IOException {
        var fixture = new VexUISessionTest.Fixture();
        var provider = new Provider();
        var session = fixture.ui.open(fixture.player, new VexUIProfessionTemplate().createView(provider));
        fixture.flush();
        var frame = fixture.dialogs.frames.getLast();
        var layout = new VexUILayout(new VexUITextEngine(new VexUIMetrics())).layout(frame.content(), frame.width());
        var controls = placements(layout).stream().filter(value -> value.element() instanceof VexUIButton).toList();
        var claim = controls.stream().filter(value -> ((VexUIButton) value.element()).action().equals("claim.all"))
            .findFirst().orElseThrow();
        var back = controls.stream().filter(value -> ((VexUIButton) value.element()).action().equals("navigation.back"))
            .findFirst().orElseThrow();
        assertEquals(claim.bounds().y(), back.bounds().y());
        assertEquals(claim.bounds().x() + claim.bounds().width() + 4, back.bounds().x());
        assertTrue(back.bounds().y() < 24);
        int height = fixture.dialogs.shown.getLast().height();
        fixture.invoke(session, "tab.unlocks");
        frame = fixture.dialogs.frames.getLast();
        assertFalse(frame.actions().keySet().stream().anyMatch(value -> value.startsWith("entry.")));
        assertFalse(frame.actions().containsKey("details.next"));
        layout = new VexUILayout(new VexUITextEngine(new VexUIMetrics())).layout(frame.content(), frame.width());
        var entries = placements(layout).stream().filter(value -> value.element() instanceof VexUIPanel panel
            && !panel.tooltip().equals(Component.empty()) && value.bounds().height() == 24).toList();
        assertEquals(8, entries.size());
        assertEquals(2, entries.stream().map(value -> value.bounds().x()).distinct().count());
        assertEquals(4, entries.stream().map(value -> value.bounds().y()).distinct().count());
        assertEquals(8, provider.seen.size());
        save(fixture.dialogs.shown.getLast(), "profession-unlocks.png");
        fixture.invoke(session, "page.next");
        assertEquals(10, provider.seen.size());
        assertEquals(height, fixture.dialogs.shown.getLast().height());
        assertPagersAligned(fixture.dialogs.frames.getLast());
    }

    @Test
    public void sourceDetailsAndClaimReceiptRetainAlignedPagersThroughTheirLastPages() {
        var fixture = new VexUISessionTest.Fixture();
        var session = fixture.ui.open(fixture.player, new VexUIProfessionTemplate().createView(new Provider()));
        fixture.flush();
        int height = fixture.dialogs.shown.getLast().height();
        fixture.invoke(session, "tab.sources");
        for (int index = 0; index < 3; index++) {
            fixture.invoke(session, "page.next");
            assertPagersAligned(fixture.dialogs.frames.getLast());
        }
        assertEquals(height, fixture.dialogs.shown.getLast().height());
        fixture.invoke(session, "claim.all");
        fixture.invoke(session, "receipt.toggle");
        assertPagersAligned(fixture.dialogs.frames.getLast());
        for (int index = 0; index < 3; index++) {
            fixture.invoke(session, "receipt.next");
            assertPagersAligned(fixture.dialogs.frames.getLast());
        }
        fixture.invoke(session, "details.next");
        assertPagersAligned(fixture.dialogs.frames.getLast());
        assertTrue(fixture.dialogs.shown.getLast().height() <= 300);
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

    private static void save(final VexUIRenderedFrame frame, final String name) throws IOException {
        var path = Path.of(System.getProperty("vexui.preview")).resolveSibling(name);
        new VexUIPackPreview().save(frame, path);
    }

    private static void assertPagersAligned(final VexUIFrame frame) {
        var layout = new VexUILayout(new VexUITextEngine(new VexUIMetrics())).layout(frame.content(), frame.width());
        var pagers = placements(layout).stream().filter(value -> value.element() instanceof VexUIButton button
            && (button.action().endsWith(".previous") || button.action().endsWith(".next"))).toList();
        int expected = frame.actions().containsKey("details.next") || frame.actions().containsKey("items.next") ? 6 : 4;
        assertEquals(expected, pagers.size(), "Each list retains both arrows, including disabled arrows");
        assertEquals(1, pagers.stream().map(value -> value.bounds().y()).distinct().count());
        assertEquals(1, pagers.stream().map(value -> value.bounds().height()).distinct().count());
    }

    private static List<VexUIPlacement> placements(final VexUIPlacement placement) {
        List<VexUIPlacement> result = new ArrayList<>();
        result.add(placement);
        placement.children().forEach(child -> result.addAll(placements(child)));
        return result;
    }

    private static List<VexUIButton> buttons(final VexUIElement element) {
        if (element instanceof VexUIButton button) {
            return List.of(button);
        }
        List<VexUIElement> children = switch (element) {
            case VexUIColumn column -> column.children();
            case VexUIRow row -> row.children();
            case VexUIGrid grid -> grid.children();
            case VexUIGridCell cell -> List.of(cell.content());
            case VexUIPanel panel -> List.of(panel.content());
            default -> List.of();
        };
        return children.stream().flatMap(child -> buttons(child).stream()).toList();
    }

    static final class Provider implements VexUIProfessionProvider {
        private final VexUIState<VexUIProfessionSnapshot> state;
        private CompletionStage<Claim> claimResult;
        private boolean closed;
        private final List<String> seen = new ArrayList<>();

        Provider() {
            List<String> names = List.of("Bergbau", "Landwirtschaft", "Holzfällen", "Schmieden", "Verzaubern", "Arkiologie");
            List<String> icons = List.of("iron_pickaxe", "iron_hoe", "iron_axe", "iron_ingot", "enchanted_book", "ender_eye");
            var rewards = IntStream.range(0, 14).mapToObj(index -> new Entry("reward_" + index,
                Component.text((index + 1) + "x Eisenbarren"), List.of(Component.text("Belohnung für deinen Fortschritt")),
                VexUIIcon.item("minecraft:item/iron_ingot"))).toList();
            var unlocks = IntStream.range(0, 10).mapToObj(index -> new Entry("unlock_" + index,
                Component.text("Freischaltung " + index), List.of(Component.text("Details")),
                VexUIIcon.item("minecraft:item/iron_ingot"), true, true, index)).toList();
            var jobs = IntStream.range(0, 8).mapToObj(index -> new Profession("job_" + index,
                Component.text(names.get(index % names.size())).color(TextColor.color(0xBEA3D1)), List.of(),
                VexUIIcon.item("minecraft:item/" + icons.get(index % icons.size())), 7, 50, 230, 500, 0.46,
                List.of(new Milestone(5, Status.AVAILABLE, rewards), new Milestone(10, Status.LOCKED, rewards.subList(0, 2))),
                rewards, unlocks)).toList();
            state = new VexUIState<>(new VexUIProfessionSnapshot(true, jobs, false));
        }

        @Override
        public VexUIProfessionSnapshot get() {
            return state.get();
        }

        @Override
        public VexUISubscription subscribe(final Runnable changed) {
            return state.subscribe(changed);
        }

        @Override
        public CompletionStage<Void> seen(final List<String> entries) {
            seen.addAll(entries);
            return CompletableFuture.completedFuture(null);
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
                case "main.nothing" -> "Nichts bereit";
                case "main.close" -> "Schließen";
                case "main.back" -> "Zurück";
                case "main.filter_0" -> "Alle";
                case "main.sort_0" -> "Standard";
                case "main.progress" -> "Lv. %level% / %maximum%";
                case "job.progress" -> "Lv. %level% / %maximum% · %xp% / %required% XP";
                case "main.available" -> "%available% Belohnung bereit";
                case "main.global_summary" -> "%professions% Berufe · %available% Belohnungen bereit";
                case "main.collect_all" -> "ALLE Berufsbelohnungen abholen (%available%)";
                case "main.previous" -> "«";
                case "main.next" -> "»";
                case "main.page" -> "%page% / %pages%";
                case "job.title" -> "%profession%";
                case "job.remaining" -> "Noch %remaining% XP bis zum nächsten Level";
                case "job.tab_levels" -> "Fortschritt";
                case "job.tab_rewards" -> "Belohnungen (%available%)";
                case "job.tab_sources" -> "XP-Quellen";
                case "job.tab_unlocks" -> "Freischaltungen";
                case "levels.level" -> "Lv. %level%";
                case "levels.locked" -> "Gesperrt";
                case "levels.reached" -> "Erreicht";
                case "levels.available" -> "Belohnung bereit";
                case "levels.claimed" -> "Abgeholt";
                case "levels.preview" -> "Vorschau · Level %level%";
                case "levels.no_reward" -> "Keine Levelbelohnung";
                case "rewards.level" -> "Level %level%";
                case "rewards.more" -> "+ %count% weitere · siehe Vorschau";
                case "rewards.locked_button" -> "Gesperrt";
                case "rewards.claimed_button" -> "Abgeholt";
                case "rewards.available" -> "%count% Belohnungen bereit";
                case "rewards.locked" -> "Noch gesperrt";
                case "rewards.collect_job" -> "Alle abholen (%available%)";
                case "rewards.collect_level" -> "Abholen";
                case "rewards.bundle" -> "Level %level% · %count% Belohnungen";
                default -> key.substring(key.indexOf('.') + 1);
            };
            for (var entry : values.entrySet()) {
                label = label.replace("%" + entry.getKey() + "%", entry.getValue());
            }
            return Component.text(label).color(TextColor.color(key.contains("collect") || key.endsWith("available")
                ? 0xF2C46C : 0xD4CFDB));
        }

        @Override
        public CompletionStage<Claim> claim(final String profession, final Integer level) {
            if (claimResult != null) {
                return claimResult;
            }
            var job = state.get().professions().getFirst();
            return CompletableFuture.completedFuture(new Claim(true,
                List.of(new Receipt(job.id(), job.name(), 5, job.milestones().getFirst().rewards()))));
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
