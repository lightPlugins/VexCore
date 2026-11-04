package dev.vexsoft.core.paper.vexui.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.paper.vexui.state.VexUIState;
import dev.vexsoft.core.paper.vexui.state.VexUISubscription;
import dev.vexsoft.core.paper.vexui.style.VexUIAttention;
import dev.vexsoft.core.paper.vexui.template.VexUIIcon;
import dev.vexsoft.core.paper.vexui.template.VexUIMainMenuTemplate;
import dev.vexsoft.core.paper.vexui.template.VexUITemplateLayout;
import dev.vexsoft.core.paper.vexui.template.VexUITemplateProvider;
import dev.vexsoft.core.paper.vexui.template.shop.VexUIShopProvider;
import dev.vexsoft.core.paper.vexui.template.shop.VexUIShopTemplate;
import dev.vexsoft.core.paper.vexui.template.upgrade.VexUIUpgradeProvider;
import dev.vexsoft.core.paper.vexui.template.upgrade.VexUIUpgradeTemplate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

/** Exercises the public templates through actual layout, opaque action regions and scoped runtime lifecycle. */
public final class VexUITemplateTest {

    private static final VexUIIcon PAPER = VexUIIcon.item("minecraft:item/paper");

    @Test
    public void shopMaintainsItsGridAndUsesCurrentQuotesAndSingleFlightPurchases() {
        var fixture = new VexUISessionTest.Fixture();
        var provider = new Shop();
        var session = fixture.ui.open(fixture.player, new VexUIShopTemplate().createView(provider));
        fixture.flush();
        fixture.invoke(session, "product.0");
        int fullHeight = fixture.dialogs.shown.getLast().height();
        assertTrue(enabled(fixture, "purchase"));
        fixture.invoke(session, "quantity.max");
        assertFalse(enabled(fixture, "purchase"));
        fixture.invoke(session, "quantity.minus16");
        fixture.invoke(session, "quantity.minus16");
        fixture.invoke(session, "quantity.minus16");
        fixture.invoke(session, "quantity.minus16");
        assertTrue(enabled(fixture, "purchase"));
        fixture.invoke(session, "page.next");
        assertEquals(fullHeight, fixture.dialogs.shown.getLast().height());
        assertEquals(2, fixture.dialogs.shown.getLast().regions().stream()
            .filter(region -> region.action().startsWith("product.")).count());
        fixture.invoke(session, "purchase");
        fixture.invoke(session, "purchase");
        assertEquals(1, provider.purchases);
        assertFalse(enabled(fixture, "purchase"));
        session.close();
        fixture.flush();
        int frames = fixture.dialogs.shown.size();
        provider.result.complete(Component.text("Bought"));
        fixture.flush();
        assertEquals(frames, fixture.dialogs.shown.size());
        assertTrue(provider.closed);
    }

    @Test
    public void dashboardAttentionAndDisabledTilesPreservePagingAndReleaseTheirProvider() {
        var fixture = new VexUISessionTest.Fixture();
        AtomicInteger selected = new AtomicInteger();
        var entries = IntStream.range(0, 8).mapToObj(index -> new VexUIMainMenuTemplate.Entry("entry_" + index,
            Component.text("Menu " + index), List.of(Component.text("Details")), PAPER, index != 0,
            new VexUIAttention(index == 0 ? 2 : 0, index == 1), viewer -> selected.incrementAndGet())).toList();
        var provider = new Provider<>(new VexUIMainMenuTemplate.Snapshot(Component.text("Menu"), Component.empty(), entries));
        var session = fixture.ui.open(fixture.player, new VexUIMainMenuTemplate().createView(provider));
        fixture.flush();
        assertFalse(enabled(fixture, "entry.entry_0"));
        fixture.invoke(session, "entry.entry_0");
        assertEquals(0, selected.get());
        int height = fixture.dialogs.shown.getLast().height();
        fixture.invoke(session, "page.next");
        assertEquals(height, fixture.dialogs.shown.getLast().height());
        fixture.invoke(session, "entry.entry_7");
        assertEquals(1, selected.get());
        session.close();
        fixture.flush();
        assertTrue(provider.closed);
    }

    @Test
    public void upgradeCostsPaginateAndDisabledExecutionCannotCallTheDomain() {
        var fixture = new VexUISessionTest.Fixture();
        var provider = new Upgrade();
        var session = fixture.ui.open(fixture.player, new VexUIUpgradeTemplate().createView(provider));
        fixture.flush();
        assertFalse(enabled(fixture, "upgrade"));
        fixture.invoke(session, "upgrade");
        assertEquals(0, provider.upgrades);
        int height = fixture.dialogs.shown.getLast().height();
        fixture.invoke(session, "page.next");
        assertEquals(height, fixture.dialogs.shown.getLast().height());
        provider.state.set(Upgrade.snapshot(true));
        fixture.flush();
        assertTrue(enabled(fixture, "upgrade"));
        fixture.invoke(session, "upgrade");
        assertEquals(1, provider.upgrades);
        assertFalse(enabled(fixture, "upgrade"));
        provider.result.complete(Component.text("Upgraded"));
        fixture.flush();
        assertTrue(fixture.dialogs.frames.getLast().content().toString().contains("Upgraded"));
    }

    private static boolean enabled(final VexUISessionTest.Fixture fixture, final String action) {
        return fixture.dialogs.shown.getLast().regions().stream().anyMatch(region -> region.action().equals(action));
    }

    private static class Provider<T> implements VexUITemplateProvider<T> {
        final VexUIState<T> state;
        boolean closed;

        Provider(final T initial) {
            state = new VexUIState<>(initial);
        }

        @Override
        public T get() {
            return state.get();
        }

        @Override
        public VexUISubscription subscribe(final Runnable changed) {
            return state.subscribe(changed);
        }

        @Override
        public VexUITemplateLayout layout() {
            return VexUITemplateLayout.overview();
        }

        @Override
        public Component text(final String key, final Map<String, String> values) {
            return Component.text(key);
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static final class Shop extends Provider<VexUIShopTemplate.Snapshot> implements VexUIShopProvider {
        private int purchases;
        private final CompletableFuture<Component> result = new CompletableFuture<>();

        private Shop() {
            super(new VexUIShopTemplate.Snapshot(Component.text("Shop"), Component.text("Balance: 10"),
                List.of(new VexUIShopTemplate.Category("items", Component.text("Items"))),
                IntStream.range(0, 8).mapToObj(index -> new VexUIShopTemplate.Product(Integer.toString(index), "items",
                    Component.text("Item " + index), Component.text("1 Coin"), List.of(), PAPER, 64, true, index,
                    VexUIAttention.none())).toList()));
        }

        @Override
        public Quote quote(final String product, final int quantity) {
            return new Quote(Component.text("Total: " + quantity), Component.text(quantity <= 10 ? "Buy" : "Insufficient"),
                quantity <= 10);
        }

        @Override
        public CompletionStage<Component> purchase(final String product, final int quantity) {
            purchases++;
            return result.minimalCompletionStage();
        }
    }

    private static final class Upgrade extends Provider<VexUIUpgradeTemplate.Snapshot> implements VexUIUpgradeProvider {
        private int upgrades;
        private final CompletableFuture<Component> result = new CompletableFuture<>();

        private Upgrade() {
            super(snapshot(false));
        }

        private static VexUIUpgradeTemplate.Snapshot snapshot(final boolean enabled) {
            var costs = IntStream.range(0, 8).mapToObj(index -> new VexUIUpgradeTemplate.Requirement(
                Component.text("Cost " + index), List.of(Component.text("10 / 20")), PAPER)).toList();
            return new VexUIUpgradeTemplate.Snapshot(Component.text("Upgrade"), Component.text("Sword +1"), PAPER,
                List.of(new VexUIUpgradeTemplate.Option("next", Component.text("Sword +2"),
                    List.of(Component.text("+10 Damage")), costs, 0.5, Component.text("Upgrade"), enabled)));
        }

        @Override
        public CompletionStage<Component> upgrade(final String option) {
            upgrades++;
            return result.minimalCompletionStage();
        }
    }
}
