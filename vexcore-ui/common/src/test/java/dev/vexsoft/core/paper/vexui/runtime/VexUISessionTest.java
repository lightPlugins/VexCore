package dev.vexsoft.core.paper.vexui.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.api.service.registry.VexClassFactory;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.common.service.registry.DefaultServiceRegistry;
import dev.vexsoft.core.paper.service.scheduler.ScheduleService;
import dev.vexsoft.core.api.service.configuration.ConfigurationService;
import dev.vexsoft.core.api.service.localization.LocalizationService;
import dev.vexsoft.core.api.service.player.PlayerService;
import dev.vexsoft.core.paper.service.messages.SendMessageService;
import dev.vexsoft.core.paper.vexui.debug.VexUIDebugShop;
import dev.vexsoft.core.paper.vexui.debug.VexUIDebugShopService;
import dev.vexsoft.core.paper.vexui.VexUIButton;
import dev.vexsoft.core.paper.vexui.VexUIClickSound;
import dev.vexsoft.core.paper.vexui.VexUIFrame;
import dev.vexsoft.core.paper.vexui.VexUISearch;
import dev.vexsoft.core.paper.vexui.VexUIService;
import dev.vexsoft.core.paper.vexui.VexUISession;
import dev.vexsoft.core.paper.vexui.VexUISize;
import dev.vexsoft.core.paper.vexui.VexUIText;
import dev.vexsoft.core.paper.vexui.VexUITheme;
import dev.vexsoft.core.paper.vexui.VexUIView;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

/** Exercises production registrations, owner replacement, action generations and shutdown with a queued transport. */
public final class VexUISessionTest {

    @Test
    public void productionRegistrationsAndListenerUseRealFactoryAndDependencyOrder() {
        var services = new DefaultServiceRegistry().scoped(() -> "core");
        VexUIRegistration.core(services);
        services.register(ScheduleService.class, VexUITestScheduleService.class);
        services.register(ConfigurationService.class, VexUIDebugTestServices.class);
        services.register(LocalizationService.class, VexUIDebugTestServices.class);
        services.register(PlayerService.class, VexUIDebugTestServices.class);
        services.register(SendMessageService.class, VexUIDebugTestServices.class);
        services.registerQueuedServices();
        assertInstanceOf(VexUIRuntimeService.class, services.require(VexUIService.class));
        assertInstanceOf(VexUIPaperDialogService.class, services.require(VexUIDialogService.class));
        assertInstanceOf(VexUIDebugShop.class, services.require(VexUIDebugShopService.class));
        assertInstanceOf(VexUIListener.class, VexClassFactory.create(VexUIListener.class, services, "listener"));
        services.unregisterOwnedServices();
    }

    @Test
    public void coalescesRefreshAndRejectsDuplicateOldAndForeignActions() {
        var fixture = new Fixture();
        AtomicInteger clicks = new AtomicInteger();
        var session = fixture.ui.open(fixture.player, view(clicks));
        session.refresh();
        session.refresh();
        fixture.flush();
        assertEquals(1, fixture.dialogs.shown.size());
        Key original = action(fixture.dialogs.shown.getLast().body());
        fixture.coordinator.dispatch(fixture.player, Key.key("vexcore:vexui/forged"), null);
        fixture.coordinator.dispatch(fixture.player, original, null);
        fixture.coordinator.dispatch(fixture.player, original, null);
        fixture.flush();
        assertEquals(1, clicks.get());
        assertEquals(List.of(VexUIClickSound.defaults().asSound()), fixture.sounds);
        assertEquals(2, fixture.dialogs.shown.size());
        assertNotEquals(original, action(fixture.dialogs.shown.getLast().body()));
        fixture.coordinator.dispatch(fixture.player, original, null);
        fixture.flush();
        assertEquals(1, clicks.get());
        assertEquals(1, fixture.sounds.size());
        session.refresh();
        fixture.flush();
        assertEquals(2, fixture.dialogs.shown.size());
        session.close();
        fixture.flush();
        assertEquals(1, fixture.dialogs.closed);
        assertTrue(session.isClosed());
    }

    @Test
    public void rejectsInvalidAndDuplicateClicksBeforeTheyReachTheScheduler() {
        var fixture = new Fixture();
        AtomicInteger clicks = new AtomicInteger();
        fixture.ui.open(fixture.player, view(clicks));
        fixture.flush();
        Key original = action(fixture.dialogs.shown.getLast().body());
        for (int i = 0; i < 10_000; i++) {
            fixture.coordinator.dispatch(fixture.player, Key.key("vexcore", "vexui/forged/" + i), null);
        }
        assertEquals(0, fixture.scheduler.queuedTaskCount());
        for (int i = 0; i < 10_000; i++) {
            fixture.coordinator.dispatch(fixture.player, original, null);
        }
        assertEquals(1, fixture.scheduler.queuedTaskCount());
        fixture.flush();
        assertEquals(1, clicks.get());
        assertEquals(1, fixture.sounds.size());
        for (int i = 0; i < 10_000; i++) {
            fixture.coordinator.dispatch(fixture.player, original, null);
        }
        assertEquals(0, fixture.scheduler.queuedTaskCount());
        fixture.click();
        assertEquals(2, clicks.get());
    }

    @Test
    public void closeIsNotBlockedByAnAlreadyQueuedButtonClick() {
        var fixture = new Fixture();
        AtomicInteger clicks = new AtomicInteger();
        var session = fixture.ui.open(fixture.player, view(clicks));
        fixture.flush();
        Key original = action(fixture.dialogs.shown.getLast().body());
        Key close = fixture.dialogs.closeAction;
        for (int i = 0; i < 100; i++) {
            fixture.coordinator.dispatch(fixture.player, original, null);
            fixture.coordinator.dispatch(fixture.player, close, null);
        }
        assertEquals(2, fixture.scheduler.queuedTaskCount());
        fixture.flush();
        assertEquals(1, clicks.get());
        assertTrue(session.isClosed());
        assertEquals(1, fixture.dialogs.closed);
        assertEquals(1, fixture.dialogs.shown.size());
        fixture.coordinator.dispatch(fixture.player, close, null);
        assertEquals(0, fixture.scheduler.queuedTaskCount());
    }

    @Test
    public void finalValidationRejectsQueuedClicksAfterFrameOrSessionReplacement() {
        var fixture = new Fixture();
        AtomicInteger clicks = new AtomicInteger();
        var session = fixture.ui.open(fixture.player, view(clicks));
        fixture.flush();
        Key original = action(fixture.dialogs.shown.getLast().body());
        // Queue a frame transition before admitting a click that is still valid at dispatch time.
        session.search(new VexUISearch(Component.text("Search"), Component.text("Query"),
            Component.text("Apply"), Component.text("Back"), "", ignored -> { }));
        fixture.coordinator.dispatch(fixture.player, original, null);
        fixture.flush();
        assertEquals(0, clicks.get());
        assertTrue(fixture.sounds.isEmpty());
        fixture.coordinator.dispatch(fixture.player, fixture.dialogs.searchCancel, null);
        fixture.flush();
        fixture.coordinator.dispatch(fixture.player, action(fixture.dialogs.shown.getLast().body()), null);
        fixture.ui.open(fixture.player, view(clicks));
        fixture.flush();
        assertTrue(session.isClosed());
        assertEquals(0, clicks.get());
        assertTrue(fixture.sounds.isEmpty());
        fixture.click();
        assertEquals(1, clicks.get());
    }

    @Test
    public void ownerReplacementAndOwnerShutdownDoNotCloseAnotherOwnersView() {
        var fixture = new Fixture();
        AtomicInteger retired = new AtomicInteger();
        var first = fixture.ui.open(fixture.player, new VexUIView() {
            @Override
            public VexUIFrame render(final VexUISession session) {
                return frame();
            }

            @Override
            public void closed() {
                retired.incrementAndGet();
            }
        });
        fixture.flush();
        var secondOwner = fixture.services.scoped(() -> "plugin");
        VexUIRegistration.owner(secondOwner);
        secondOwner.registerQueuedServices();
        fixture.ui.setClickSound(VexUIClickSound.disabled());
        assertEquals(VexUIClickSound.defaults(), secondOwner.require(VexUIService.class).getClickSound());
        var second = secondOwner.require(VexUIService.class).open(fixture.player, ignored -> frame());
        fixture.services.require(VexUIService.class).find(fixture.player).ifPresent(value -> value.close());
        fixture.flush();
        assertTrue(first.isClosed());
        assertFalse(second.isClosed());
        assertEquals(1, retired.get());
        assertEquals(0, fixture.dialogs.closed);
        secondOwner.unregisterOwnedServices();
        fixture.flush();
        assertTrue(second.isClosed());
        assertEquals(1, fixture.dialogs.closed);
    }

    @Test
    public void navigationSearchCancelAndDisconnectRetireTheCorrectState() {
        var fixture = new Fixture();
        AtomicInteger closed = new AtomicInteger();
        var session = fixture.ui.open(fixture.player, ignored -> frame());
        fixture.flush();
        session.navigate(new VexUIView() {
            @Override
            public VexUIFrame render(final VexUISession viewer) {
                return frame();
            }

            @Override
            public void closed() {
                closed.incrementAndGet();
            }
        });
        fixture.flush();
        session.back();
        fixture.flush();
        assertEquals(1, closed.get());
        List<String> queries = new ArrayList<>();
        var label = Component.text("Search");
        session.search(new VexUISearch(label, label, label, label, "", queries::add));
        fixture.flush();
        fixture.coordinator.dispatch(fixture.player, fixture.dialogs.searchSubmit, "potato");
        fixture.flush();
        assertEquals(List.of("potato"), queries);
        assertTrue(fixture.sounds.isEmpty());
        session.search(new VexUISearch(label, label, label, label, "", queries::add));
        fixture.flush();
        fixture.coordinator.dispatch(fixture.player, fixture.dialogs.searchCancel, "discard");
        fixture.flush();
        assertEquals(List.of("potato"), queries);
        fixture.coordinator.disconnected(fixture.player.getUniqueId());
        fixture.flush();
        assertTrue(session.isClosed());
        assertEquals(0, fixture.dialogs.closed);
    }

    @Test
    public void ownerSoundChangesApplyToAlreadyOpenSessions() {
        var fixture = new Fixture();
        AtomicInteger clicks = new AtomicInteger();
        fixture.ui.setClickSound(VexUIClickSound.disabled());
        fixture.ui.open(fixture.player, view(clicks));
        fixture.flush();
        fixture.click();
        assertTrue(fixture.sounds.isEmpty());
        var custom = new VexUIClickSound(true, Key.key("minecraft:block.note_block.hat"), 0.2F, 1.5F);
        fixture.ui.setClickSound(custom);
        fixture.click();
        assertEquals(List.of(custom.asSound()), fixture.sounds);
        fixture.ui.setClickSound(VexUIClickSound.disabled());
        fixture.click();
        assertEquals(1, fixture.sounds.size());
        assertEquals(3, clicks.get());
    }

    @Test
    public void frameAndButtonOverridesRespectInheritanceAndDisabledButtonsStaySilent() {
        var fixture = new Fixture();
        AtomicReference<VexUIClickSound> menuSound = new AtomicReference<>();
        AtomicReference<VexUIClickSound> buttonSound = new AtomicReference<>();
        AtomicReference<Boolean> enabled = new AtomicReference<>(true);
        AtomicInteger clicks = new AtomicInteger();
        fixture.ui.setClickSound(VexUIClickSound.disabled());
        var session = fixture.ui.open(fixture.player, ignored -> new VexUIFrame(
            Component.text("Test"), Component.text("Close"), 150,
            new VexUIButton(VexUISize.content(), new VexUIText(Component.text("Test")),
                VexUITheme.BUTTON, enabled.get(), "test", Component.empty())
                .withClickSound(buttonSound.get()),
            Map.of("test", viewer -> clicks.incrementAndGet())
        ).withClickSound(menuSound.get()));
        fixture.flush();
        // A frame-only sound change must take effect even when the unchanged render skips the packet.
        menuSound.set(VexUIClickSound.defaults());
        session.refresh();
        fixture.flush();
        assertEquals(1, fixture.dialogs.shown.size());
        fixture.click();
        assertEquals(List.of(VexUIClickSound.defaults().asSound()), fixture.sounds);
        buttonSound.set(VexUIClickSound.disabled());
        session.refresh();
        fixture.flush();
        fixture.click();
        assertEquals(1, fixture.sounds.size());
        menuSound.set(VexUIClickSound.disabled());
        var custom = new VexUIClickSound(true, Key.key("example:button"), 0.3F, 1.6F);
        buttonSound.set(custom);
        session.refresh();
        fixture.flush();
        fixture.click();
        assertEquals(custom.asSound(), fixture.sounds.getLast());
        Key stale = action(fixture.dialogs.shown.getLast().body());
        enabled.set(false);
        session.refresh();
        fixture.flush();
        assertNull(action(fixture.dialogs.shown.getLast().body()));
        fixture.coordinator.dispatch(fixture.player, stale, null);
        fixture.flush();
        assertEquals(2, fixture.sounds.size());
        assertEquals(3, clicks.get());
    }

    @Test
    public void disabledPluginClosesSessionsAndOwnedServicesWithoutScheduling() {
        for (boolean closeSessionFirst : List.of(false, true)) {
            var enabled = new AtomicBoolean(true);
            var fixture = new Fixture(pluginOwner(enabled));
            var closed = new AtomicInteger();
            var cleanup = new AtomicInteger();
            var session = fixture.ui.open(fixture.player, new VexUIView() {
                @Override
                public VexUIFrame render(final VexUISession viewer) {
                    viewer.scope().onClose(cleanup::incrementAndGet);
                    return frame();
                }

                @Override
                public void closed() {
                    closed.incrementAndGet();
                }
            });
            fixture.flush();
            session.refresh();
            enabled.set(false);
            if (closeSessionFirst) {
                session.close();
            }
            fixture.services.unregisterOwnedServices();
            fixture.flush();
            session.close();
            assertTrue(session.isClosed());
            assertEquals(1, closed.get());
            assertEquals(1, cleanup.get());
            assertFalse(fixture.coordinator.occupied(fixture.player.getUniqueId()));
            assertEquals(0, fixture.scheduler.queuedTaskCount());
            assertEquals(0, fixture.dialogs.closed);
        }
    }

    @Test
    public void pluginDisabledDuringCloseSchedulingStillRetiresTheSession() {
        var enabled = new AtomicBoolean(true);
        var fixture = new Fixture(pluginOwner(enabled));
        var closed = new AtomicInteger();
        var session = fixture.ui.open(fixture.player, new VexUIView() {
            @Override
            public VexUIFrame render(final VexUISession viewer) {
                return frame();
            }

            @Override
            public void closed() {
                closed.incrementAndGet();
            }
        });
        fixture.flush();
        fixture.scheduler.beforeNextTask = () -> enabled.set(false);
        session.close();
        fixture.services.unregisterOwnedServices();
        fixture.flush();
        assertTrue(session.isClosed());
        assertEquals(1, closed.get());
        assertFalse(fixture.coordinator.occupied(fixture.player.getUniqueId()));
        assertEquals(0, fixture.dialogs.closed);
    }

    @Test
    public void lateClosePacketCannotCloseAReplacementWithAnotherOwner() {
        var fixture = new Fixture();
        var first = fixture.ui.open(fixture.player, ignored -> frame());
        fixture.flush();
        first.close();
        var plugin = fixture.services.scoped(() -> "plugin");
        VexUIRegistration.owner(plugin);
        plugin.registerQueuedServices();
        var second = plugin.require(VexUIService.class).open(fixture.player, ignored -> frame());
        fixture.flush();
        assertEquals(0, fixture.dialogs.closed);
        assertSame(second, plugin.require(VexUIService.class).find(fixture.player).orElseThrow());
    }

    private VexUIView view(final AtomicInteger clicks) {
        return ignored -> new VexUIFrame(Component.text("Test"), Component.text("Close"), 150,
            VexUIButton.text(Component.text("Buy"), "buy"), Map.of("buy", session -> clicks.incrementAndGet()));
    }

    private static ServiceOwner pluginOwner(final AtomicBoolean enabled) {
        return (ServiceOwner) Proxy.newProxyInstance(Plugin.class.getClassLoader(),
            new Class<?>[]{Plugin.class, ServiceOwner.class}, (proxy, method, args) -> switch (method.getName()) {
                case "getServiceOwnerName", "getName", "toString" -> "plugin";
                case "isEnabled" -> enabled.get();
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> null;
            });
    }

    private static VexUIFrame frame() {
        return new VexUIFrame(Component.text("Test"), Component.text("Close"), 150,
            VexUIButton.text(Component.text("Test"), "test"), Map.of("test", ignored -> { }));
    }

    private static Key action(final Component value) {
        var click = value.clickEvent();
        if (click != null && click.payload() instanceof ClickEvent.Payload.Custom custom) {
            return custom.key();
        }
        for (var child : value.children()) {
            Key found = action(child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    static final class Fixture {
        final VexServiceRegistry services;
        final VexUITestScheduleService scheduler;
        final VexUITestDialogService dialogs;
        final VexUIService ui;
        final VexUICoordinatorService coordinator;
        final Player player;
        final List<Sound> sounds = new ArrayList<>();

        Fixture() {
            this(() -> "core");
        }

        Fixture(final ServiceOwner owner) {
            services = new DefaultServiceRegistry().scoped(owner);
            services.register(ScheduleService.class, VexUITestScheduleService.class);
            services.register(VexUICoordinatorService.class, VexUISessionCoordinator.class);
            services.register(VexUIDialogService.class, VexUITestDialogService.class);
            VexUIRegistration.owner(services);
            services.registerQueuedServices();
            scheduler = (VexUITestScheduleService) services.require(ScheduleService.class);
            dialogs = (VexUITestDialogService) services.require(VexUIDialogService.class);
            ui = services.require(VexUIService.class);
            coordinator = services.require(VexUICoordinatorService.class);
            UUID identity = UUID.randomUUID();
            player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> identity;
                    case "isOnline" -> true;
                    case "hashCode" -> identity.hashCode();
                    case "equals" -> proxy == args[0];
                    case "toString" -> identity.toString();
                    case "playSound" -> {
                        sounds.add((Sound) args[0]);
                        yield null;
                    }
                    default -> null;
                });
        }

        void click() {
            coordinator.dispatch(player, action(dialogs.shown.getLast().body()), null);
            flush();
        }

        void flush() {
            scheduler.flush();
        }

        void invoke(final VexUISession session, final String action) {
            var handler = dialogs.frames.getLast().actions().get(action);
            if (handler == null) {
                throw new AssertionError("Missing action " + action);
            }
            session.update(() -> handler.execute(session));
            flush();
        }
    }
}
