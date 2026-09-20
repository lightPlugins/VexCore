package dev.vexsoft.core.paper.service.interactiveui;

import static org.junit.jupiter.api.Assertions.*;

import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.interactiveui.InteractiveUiElement;
import dev.vexsoft.core.paper.interactiveui.InteractiveUiInput;
import dev.vexsoft.core.paper.interactiveui.UiBounds;
import dev.vexsoft.core.paper.packets.interactiveui.InteractiveUiPacketInput;
import dev.vexsoft.core.paper.packets.interactiveui.InteractiveUiPacketInput.Kind;
import dev.vexsoft.core.paper.packets.interactiveui.InteractiveUiPacketTransport;
import dev.vexsoft.core.paper.packets.service.InteractiveUiPacketAdapterService;
import dev.vexsoft.core.paper.scheduler.VexTask;
import dev.vexsoft.core.paper.service.scheduler.ScheduleService;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

public final class InteractiveUiLifecycleTest {

    @Test
    public void isolatesOwnersAndRestoresOnlyOnce() {
        Harness test = new Harness();
        var screen = test.coordinator.open(test.owner, test.player, "canvas");

        assertThrows(IllegalStateException.class,
            () -> test.coordinator.open(() -> "other", test.player, "canvas"));
        assertTrue(test.coordinator.find(() -> "other", test.player, "canvas").isEmpty());
        test.tick();
        test.coordinator.closeOwner(test.owner);
        screen.close();
        test.coordinator.close();

        assertTrue(screen.isClosed());
        assertEquals(1, test.restores);
        assertTrue(test.coordinator.find(test.owner, test.player, "canvas").isEmpty());
    }

    @Test
    public void oldScheduledTickCannotAffectReopenedSession() {
        Harness test = new Harness();
        var first = test.coordinator.open(test.owner, test.player, "canvas");
        Runnable stale = test.ticks.getFirst();

        test.coordinator.consume(test.viewerId, input(Kind.ROTATION, 20, -1));
        first.close();
        var second = test.coordinator.open(test.owner, test.player, "canvas");

        stale.run();
        assertEquals(160, second.state().x());
        assertEquals(0, test.scenes);
        test.tick();
        assertEquals(1, test.scenes);
    }

    @Test
    public void cursorUpdatesDoNotResendUnchangedSceneOrIdlePackets() {
        Harness test = new Harness();
        var screen = test.coordinator.open(test.owner, test.player, "canvas");

        test.tick();
        test.coordinator.consume(test.viewerId, input(Kind.ROTATION, 5, -1));
        test.tick();
        int updates = test.cursors;

        for (int tick = 0; tick < 12; tick++) {
            test.tick();
        }
        assertEquals(175, screen.state().x());
        assertEquals(1, test.scenes);
        assertEquals(updates, test.cursors);
    }

    @Test
    public void cursorIsSentBeforeTheNextEntityTickWithoutInvokingCallbacksOnTheNetworkThread() {
        Harness test = new Harness();
        var screen = test.coordinator.open(test.owner, test.player, "canvas");
        List<InteractiveUiInput> callbacks = new ArrayList<>();

        screen.onInput(callbacks::add);
        test.tick();
        int initial = test.cursors;
        test.ownsPlayer = false;
        test.coordinator.consume(test.viewerId, input(Kind.ROTATION, 5, -1));

        assertEquals(initial + 1, test.cursors);
        assertEquals(InteractiveUiRenderer.renderCursor(175, 90), test.cursorContent);
        assertEquals(160, screen.state().x());
        assertTrue(callbacks.isEmpty());

        test.ownsPlayer = true;
        test.tick();
        assertEquals(175, screen.state().x());
        assertEquals(initial + 1, test.cursors);
        assertTrue(callbacks.stream().anyMatch(event -> event.kind() == InteractiveUiInput.Kind.MOVE));
    }

    @Test
    public void firstSceneUsesTheLatestNetworkPositionEvenWhenNoFurtherInputArrives() {
        Harness test = new Harness();

        test.coordinator.open(test.owner, test.player, "canvas");
        test.coordinator.consume(test.viewerId, input(Kind.ROTATION, 5, -1));
        assertEquals(0, test.cursors);
        test.tick();

        assertEquals(1, test.scenes);
        assertEquals(1, test.cursors);
        assertEquals(InteractiveUiRenderer.renderCursor(175, 90), test.cursorContent);
    }

    @Test
    public void tickCannotPullCursorBackAfterNewInputArrivesDuringCallback() {
        Harness test = new Harness();
        var screen = test.coordinator.open(test.owner, test.player, "canvas");

        test.tick();
        screen.onInput(event -> {
            if (event.kind() == InteractiveUiInput.Kind.MOVE) {
                test.coordinator.consume(test.viewerId, input(Kind.ROTATION, 10, -1));
            }
        });
        test.coordinator.consume(test.viewerId, input(Kind.ROTATION, 5, -1));
        test.tick();

        assertEquals(175, screen.state().x());
        assertEquals(InteractiveUiRenderer.renderCursor(190, 90), test.cursorContent);
        test.tick();
        assertEquals(190, screen.state().x());
    }

    @Test
    public void outAndBackDragBeforeOneTickDoesNotBecomeAClick() {
        Harness test = new Harness();
        var screen = test.coordinator.open(test.owner, test.player, "canvas");
        List<InteractiveUiInput> events = new ArrayList<>();

        screen.put(new InteractiveUiElement("canvas", new UiBounds(0, 0, 320, 180), Component.empty(), 0, true));
        screen.onInput(events::add);
        test.coordinator.consume(test.viewerId, input(Kind.PRESS, 0, 1));
        test.coordinator.consume(test.viewerId, input(Kind.ROTATION, 10, -1));
        test.coordinator.consume(test.viewerId, input(Kind.ROTATION, 0, -1));
        test.coordinator.consume(test.viewerId, input(Kind.RELEASE, 0, -1));
        test.tick();

        assertTrue(events.stream().anyMatch(event -> event.kind() == InteractiveUiInput.Kind.DRAG));
        assertFalse(events.stream().anyMatch(event -> event.kind() == InteractiveUiInput.Kind.CLICK));
    }

    @Test
    public void failedCursorSendRestoresPartiallyDisplayedScene() {
        Harness test = new Harness();
        var screen = test.coordinator.open(test.owner, test.player, "canvas");

        test.failCursor = true;
        test.tick();

        assertEquals(1, test.scenes);
        assertEquals(1, test.restores);
        assertTrue(screen.isClosed());
    }

    @Test
    public void callbackFailureReleasesControlsAndStopsFutureInput() {
        Harness test = new Harness();
        var screen = test.coordinator.open(test.owner, test.player, "canvas");

        screen.put(new InteractiveUiElement("button", new UiBounds(140, 80, 60, 20),
            Component.text("Button"), 0x0369A1, true));
        screen.onInput(event -> {
            throw new IllegalStateException("Simulated callback failure");
        });
        test.coordinator.consume(test.viewerId, input(Kind.PRESS, 0, 5));
        test.tick();

        assertTrue(screen.isClosed());
        assertEquals(5, test.acknowledged);
        assertEquals(1, test.restores);
        assertFalse(test.coordinator.consume(test.viewerId, input(Kind.RELEASE, 0, -1)));
    }

    @Test
    public void setupFailureDoesNotOccupyViewerOrLeaveRepeatingTask() {
        Harness test = new Harness();

        test.failOpen = true;
        assertThrows(IllegalStateException.class, () -> test.coordinator.open(test.owner, test.player, "canvas"));
        assertTrue(test.coordinator.find(test.owner, test.player, "canvas").isEmpty());
        assertTrue(test.ticks.isEmpty());
        test.failOpen = false;
        assertNotNull(test.coordinator.open(test.owner, test.player, "canvas"));
    }

    @Test
    public void rejectsForeignBlockClicksAndLeavesUnrelatedPacketsAlone() {
        Harness test = new Harness();
        var screen = test.coordinator.open(test.owner, test.player, "canvas");

        assertFalse(test.coordinator.consume(test.viewerId, new Object()));
        assertTrue(test.coordinator.consume(test.viewerId,
            new InteractiveUiPacketInput(Kind.PRESS, 0, 0, 9, 100, 100, 100)));
        test.tick();
        assertFalse(screen.state().pressed());
        assertEquals(9, test.acknowledged);
    }

    @Test
    public void closeOwnerOnAnotherThreadDisablesInputBeforeScheduledCleanup() {
        Harness test = new Harness();
        var screen = test.coordinator.open(test.owner, test.player, "canvas");

        test.ownsPlayer = false;
        test.coordinator.closeOwner(test.owner);
        assertTrue(screen.isClosed());
        assertFalse(test.coordinator.consume(test.viewerId, input(Kind.PRESS, 0, 1)));
        assertEquals(0, test.restores);
        test.ownsPlayer = true;
        test.scheduled.removeFirst().run();
        assertEquals(1, test.restores);
    }

    @Test
    public void closingBeforeTheNextTickAcknowledgesAlreadyConsumedPackets() {
        Harness test = new Harness();
        var screen = test.coordinator.open(test.owner, test.player, "canvas");

        test.coordinator.consume(test.viewerId, input(Kind.PRESS, 0, 12));
        screen.close();

        assertEquals(12, test.acknowledged);
        assertEquals(1, test.restores);
    }

    @Test
    public void removingAndReaddingTargetDuringReleaseDoesNotClickTheReplacement() {
        Harness test = new Harness();
        var screen = test.coordinator.open(test.owner, test.player, "canvas");
        var button = new InteractiveUiElement("button", new UiBounds(140, 80, 60, 20),
            Component.text("Button"), 0x0369A1, true);
        List<InteractiveUiInput.Kind> received = new ArrayList<>();

        screen.put(button);
        screen.onInput(event -> {
            received.add(event.kind());
            if (event.kind() == InteractiveUiInput.Kind.RELEASE) {
                screen.remove("button");
                screen.put(button);
            }
        });
        test.coordinator.consume(test.viewerId, input(Kind.PRESS, 0, 1));
        test.coordinator.consume(test.viewerId, input(Kind.RELEASE, 0, -1));
        test.tick();

        assertTrue(received.contains(InteractiveUiInput.Kind.RELEASE));
        assertFalse(received.contains(InteractiveUiInput.Kind.CLICK));
    }

    @Test
    public void cancellationFailureStillRestoresAndReleasesSession() {
        Harness test = new Harness();
        var screen = test.coordinator.open(test.owner, test.player, "canvas");

        test.failCancel = true;
        screen.close();

        assertEquals(1, test.restores);
        assertTrue(test.coordinator.find(test.owner, test.player, "canvas").isEmpty());
    }

    @Test
    public void reentrantOwnerCloseDuringSetupStillRestoresTheTransport() {
        Harness test = new Harness();
        VexServiceRegistry registry = proxy(VexServiceRegistry.class, (object, method, arguments) ->
            method.getName().equals("getOwner") ? test.owner : test.coordinator);
        VexInteractiveUiService scoped = new VexInteractiveUiService(registry);

        test.onOpen = scoped::close;
        assertThrows(IllegalStateException.class, () -> scoped.open(test.player, "canvas"));
        assertEquals(1, test.restores);
        assertTrue(test.coordinator.find(test.owner, test.player, "canvas").isEmpty());
        assertThrows(IllegalStateException.class, () -> scoped.open(test.player, "canvas"));
    }

    private static InteractiveUiPacketInput input(Kind kind, float yaw, int sequence) {
        return new InteractiveUiPacketInput(kind, yaw, 0, sequence, 0, 66, 0);
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }

    private static final class Harness {

        private final UUID viewerId = UUID.randomUUID();
        private final ServiceOwner owner = () -> "test";
        private final List<Runnable> ticks = new ArrayList<>();
        private final List<Runnable> scheduled = new ArrayList<>();
        private boolean ownsPlayer = true;
        private boolean failOpen;
        private boolean failCursor;
        private boolean failCancel;
        private Runnable onOpen = () -> { };
        private int scenes;
        private int cursors;
        private Component cursorContent;
        private int restores;
        private int acknowledged = -1;
        private long gameTime;
        private final Player player;
        private final VexInteractiveUiCoordinatorService coordinator;

        private Harness() {
            Server server = proxy(Server.class, (object, method, arguments) -> {
                if (method.getName().equals("isOwnedByCurrentRegion")) {
                    return ownsPlayer;
                }
                throw new UnsupportedOperationException(method.getName());
            });
            World world = proxy(World.class, (object, method, arguments) -> gameTime);

            player = proxy(Player.class, (object, method, arguments) -> switch (method.getName()) {
                case "getUniqueId" -> viewerId;
                case "getServer" -> server;
                case "getWorld" -> world;
                case "isOnline" -> true;
                case "isDead" -> false;
                case "closeInventory" -> null;
                default -> throw new UnsupportedOperationException(method.getName());
            });
            VexTask task = proxy(VexTask.class, (object, method, arguments) -> {
                if (failCancel) {
                    throw new IllegalStateException("Simulated task cancellation failure");
                }
                return null;
            });
            ScheduleService scheduler = proxy(ScheduleService.class, (object, method, arguments) -> {
                if (method.getName().equals("runForTimer")) {
                    ticks.add((Runnable) arguments[3]);
                } else if (method.getName().equals("runFor")) {
                    scheduled.add((Runnable) arguments[1]);
                } else {
                    throw new UnsupportedOperationException(method.getName());
                }
                return Optional.of(task);
            });
            InteractiveUiPacketAdapterService packets = proxy(InteractiveUiPacketAdapterService.class,
                (object, method, arguments) -> switch (method.getName()) {
                    case "open" -> {
                        if (failOpen) {
                            throw new IllegalStateException("Simulated setup failure");
                        }
                        resetCamera();
                        onOpen.run();
                        yield new InteractiveUiPacketTransport(viewerId, UUID.randomUUID(), 10, 11,
                            0, 66, 0, 0.5, 66.5, 0.5, 0, 0);
                    }
                    case "close" -> {
                        restores++;
                        yield null;
                    }
                    case "scene" -> {
                        scenes++;
                        yield null;
                    }
                    case "cursorImmediate" -> {
                        if (failCursor) {
                            throw new IllegalStateException("Simulated cursor send failure");
                        }
                        cursorContent = (Component) arguments[1];
                        cursors++;
                        yield null;
                    }
                    case "acknowledge" -> {
                        acknowledged = (int) arguments[1];
                        yield null;
                    }
                    case "decode" -> arguments[0] instanceof InteractiveUiPacketInput input
                        ? Optional.of(input) : Optional.empty();
                    default -> throw new UnsupportedOperationException(method.getName());
                });
            VexServiceRegistry registry = proxy(VexServiceRegistry.class, (object, method, arguments) ->
                arguments[0] == ScheduleService.class ? scheduler : packets);

            coordinator = new VexInteractiveUiCoordinatorService(registry);
        }

        private void tick() {
            gameTime++;
            ticks.getLast().run();
        }

        private void resetCamera() {
            coordinator.consume(viewerId, input(Kind.ROTATION, 0, -1));
        }
    }
}
