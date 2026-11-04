package dev.vexsoft.core.paper.vexui.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.paper.vexui.VexUIFrame;
import dev.vexsoft.core.paper.vexui.VexUISession;
import dev.vexsoft.core.paper.vexui.VexUIView;
import dev.vexsoft.core.paper.vexui.action.VexUIActionLabels;
import dev.vexsoft.core.paper.vexui.action.VexUIActionStatus;
import dev.vexsoft.core.paper.vexui.action.VexUIAsyncAction;
import dev.vexsoft.core.paper.vexui.component.VexUISearch;
import dev.vexsoft.core.paper.vexui.element.VexUIButton;
import dev.vexsoft.core.paper.vexui.element.VexUIElement;
import dev.vexsoft.core.paper.vexui.element.VexUIText;
import dev.vexsoft.core.paper.vexui.state.VexUIObservable;
import dev.vexsoft.core.paper.vexui.state.VexUIState;
import dev.vexsoft.core.paper.vexui.state.VexUISubscription;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

/** Verifies actual session bindings, view scopes and asynchronous callbacks across lifecycle transitions. */
public final class VexUIReactiveTest {

    @Test
    public void bindingsCoalesceUpdatesAndRetireWhenUnusedHiddenOrClosed() {
        var fixture = new VexUISessionTest.Fixture();
        var source = new TrackingSource<>(1);
        var visible = new VexUIState<>(true);
        var session = fixture.ui.open(fixture.player, viewer -> frame(new VexUIText(Component.text(
            viewer.bind(visible) ? viewer.bind(source) : 0))));
        fixture.flush();
        assertEquals(1, source.subscribers.get());
        CompletableFuture.runAsync(() -> {
            source.state.set(2);
            source.state.set(3);
        }).join();
        assertEquals(1, fixture.scheduler.queuedTaskCount());
        fixture.flush();
        assertEquals(2, fixture.dialogs.frames.size());
        assertEquals(1, source.subscribers.get());
        visible.set(false);
        fixture.flush();
        assertEquals(0, source.subscribers.get());
        source.state.set(4);
        assertEquals(0, fixture.scheduler.queuedTaskCount());
        visible.set(true);
        fixture.flush();
        session.navigate(viewer -> frame(new VexUIText(Component.text("Child"))));
        fixture.flush();
        assertEquals(0, source.subscribers.get());
        source.state.set(5);
        assertEquals(0, fixture.scheduler.queuedTaskCount());
        session.back();
        fixture.flush();
        assertEquals(1, source.subscribers.get());
        var label = Component.text("Search");
        session.search(new VexUISearch(label, label, label, label, "", ignored -> { }));
        fixture.flush();
        assertEquals(0, source.subscribers.get());
        fixture.coordinator.dispatch(fixture.player, fixture.dialogs.searchCancel, null);
        fixture.flush();
        assertEquals(1, source.subscribers.get());
        session.close();
        fixture.flush();
        assertEquals(0, source.subscribers.get());
        assertThrows(IllegalStateException.class, () -> session.bind(source));
    }

    @Test
    public void disconnectAndFaultyCleanupStillReleaseAllSubscriptionsAndViews() {
        var fixture = new VexUISessionTest.Fixture();
        var source = new TrackingSource<>(1);
        var session = fixture.ui.open(fixture.player,
            viewer -> frame(new VexUIText(Component.text(viewer.bind(source)))));
        fixture.flush();
        fixture.coordinator.disconnected(fixture.player.getUniqueId());
        assertEquals(0, source.subscribers.get());
        assertTrue(session.isClosed());
        AtomicInteger viewsClosed = new AtomicInteger();
        VexUIObservable<Integer> faulty = new VexUIObservable<>() {
            @Override
            public Integer get() {
                return 1;
            }

            @Override
            public VexUISubscription subscribe(final Runnable changed) {
                return () -> {
                    throw new IllegalStateException("Cleanup failure");
                };
            }
        };
        var replacement = fixture.ui.open(fixture.player, new VexUIView() {
            @Override
            public VexUIFrame render(final VexUISession viewer) {
                return frame(new VexUIText(Component.text(viewer.bind(source) + viewer.bind(faulty))));
            }

            @Override
            public void closed() {
                viewsClosed.incrementAndGet();
            }
        });
        fixture.flush();
        assertThrows(IllegalStateException.class, replacement::close);
        assertEquals(0, source.subscribers.get());
        assertEquals(1, viewsClosed.get());
        assertTrue(replacement.isClosed());
        assertTrue(fixture.ui.find(fixture.player).isEmpty());
        fixture.flush();
    }

    @Test
    public void scopesRejectQueuedUpdatesAfterNavigationAndCleanupOnlyOnce() {
        var fixture = new VexUISessionTest.Fixture();
        var session = fixture.ui.open(fixture.player, viewer -> frame(new VexUIText(Component.text("Parent"))));
        fixture.flush();
        var original = session.scope();
        AtomicInteger updates = new AtomicInteger();
        AtomicInteger retired = new AtomicInteger();
        original.onClose(retired::incrementAndGet);
        var removed = original.onClose(() -> retired.addAndGet(100));
        removed.close();
        removed.close();
        session.navigate(viewer -> frame(new VexUIText(Component.text("Child"))));
        original.update(updates::incrementAndGet);
        fixture.flush();
        assertFalse(original.isActive());
        assertEquals(0, updates.get());
        assertEquals(1, retired.get());
        original.onClose(retired::incrementAndGet);
        assertEquals(2, retired.get());
        session.back();
        fixture.flush();
        assertNotSame(original, session.scope());
        session.scope().update(updates::incrementAndGet);
        fixture.flush();
        assertEquals(1, updates.get());
        session.close();
        fixture.flush();
        assertEquals(2, retired.get());
    }

    @Test
    public void asyncActionsDisableButtonsAndReturnCompletionToTheEntityScheduler() {
        var fixture = new VexUISessionTest.Fixture();
        var action = new VexUIAsyncAction<Integer>();
        var result = new AtomicInteger();
        var future = new CompletableFuture<Integer>();
        var session = fixture.ui.open(fixture.player, viewer -> new VexUIFrame(Component.text("Load"),
            Component.text("Close"), 250, action.button(viewer, "load", labels(), Component.empty()),
            Map.of("load", active -> action.run(active, () -> future,
                (current, value) -> result.set(value), (current, error) -> { }))));
        fixture.flush();
        fixture.invoke(session, "load");
        assertEquals(VexUIActionStatus.RUNNING, action.status().get());
        assertFalse(((VexUIButton) fixture.dialogs.frames.getLast().content()).enabled());
        assertFalse(action.run(session, () -> {
            throw new AssertionError("Duplicate operation must not start");
        }, (current, value) -> { }, (current, error) -> { }));
        CompletableFuture.runAsync(() -> future.complete(42)).join();
        assertEquals(0, result.get());
        fixture.flush();
        assertEquals(42, result.get());
        assertEquals(VexUIActionStatus.SUCCESS, action.status().get());
        assertTrue(((VexUIButton) fixture.dialogs.frames.getLast().content()).enabled());
        var failure = new IllegalStateException("service failure");
        var observed = new AtomicReference<Throwable>();
        session.update(() -> action.run(session,
            () -> CompletableFuture.failedFuture(new CompletionException(failure)),
            (current, value) -> { }, (current, error) -> observed.set(error)));
        fixture.flush();
        assertSame(failure, observed.get());
        assertEquals(VexUIActionStatus.ERROR, action.status().get());
        session.update(() -> action.run(session, () -> {
            throw failure;
        }, (current, value) -> { }, (current, error) -> observed.set(error)));
        fixture.flush();
        assertEquals(VexUIActionStatus.ERROR, action.status().get());
    }

    @Test
    public void closingDuringActionAdmissionCannotLeaveABusyControllerOrStartTheOperation() {
        var fixture = new VexUISessionTest.Fixture();
        var action = new VexUIAsyncAction<Integer>();
        var session = fixture.ui.open(fixture.player, viewer -> frame(action.button(viewer,
            "load", labels(), Component.empty())));
        fixture.flush();
        var subscription = action.status().subscribe(() -> {
            if (action.status().get() == VexUIActionStatus.RUNNING) {
                session.close();
            }
        });
        AtomicInteger requests = new AtomicInteger();
        session.update(() -> assertFalse(action.run(session, () -> {
            requests.incrementAndGet();
            return CompletableFuture.completedFuture(42);
        }, (current, value) -> { }, (current, error) -> { })));
        fixture.flush();
        assertTrue(session.isClosed());
        assertEquals(0, requests.get());
        assertEquals(VexUIActionStatus.IDLE, action.status().get());
        subscription.close();
    }

    @Test
    public void lateResultsCannotChangeANewActivationOrLeaveTheOldActionBusy() {
        var fixture = new VexUISessionTest.Fixture();
        var action = new VexUIAsyncAction<Integer>();
        var old = new CompletableFuture<Integer>();
        var next = new CompletableFuture<Integer>();
        var results = new AtomicInteger();
        var session = fixture.ui.open(fixture.player, viewer -> frame(action.button(viewer,
            "load", labels(), Component.empty())));
        fixture.flush();
        session.update(() -> action.run(session, () -> old,
            (current, value) -> results.set(value), (current, error) -> { }));
        fixture.flush();
        session.navigate(viewer -> frame(new VexUIText(Component.text("Child"))));
        fixture.flush();
        assertEquals(VexUIActionStatus.IDLE, action.status().get());
        session.back();
        fixture.flush();
        session.update(() -> action.run(session, () -> next,
            (current, value) -> results.set(value), (current, error) -> { }));
        fixture.flush();
        old.complete(99);
        fixture.flush();
        assertEquals(VexUIActionStatus.RUNNING, action.status().get());
        assertEquals(0, results.get());
        next.complete(7);
        fixture.flush();
        assertEquals(7, results.get());
        var pending = new CompletableFuture<Integer>();
        session.update(() -> action.run(session, () -> pending,
            (current, value) -> results.set(value), (current, error) -> { }));
        fixture.flush();
        fixture.services.unregisterOwnedServices();
        fixture.flush();
        pending.complete(100);
        assertEquals(0, fixture.scheduler.queuedTaskCount());
        assertEquals(7, results.get());
        assertEquals(VexUIActionStatus.IDLE, action.status().get());
    }

    private static VexUIFrame frame(final VexUIElement content) {
        return new VexUIFrame(Component.text("Reactive"), Component.text("Close"), 250,
            content, Map.of("load", ignored -> { }));
    }

    private static VexUIActionLabels labels() {
        return new VexUIActionLabels(Component.text("Load"), Component.text("Loading"),
            Component.text("Loaded"), Component.text("Failed"));
    }

    private static final class TrackingSource<T> implements VexUIObservable<T> {
        private final VexUIState<T> state;
        private final AtomicInteger subscribers = new AtomicInteger();

        private TrackingSource(final T initial) {
            state = new VexUIState<>(initial);
        }

        @Override
        public T get() {
            return state.get();
        }

        @Override
        public VexUISubscription subscribe(final Runnable changed) {
            subscribers.incrementAndGet();
            var subscription = state.subscribe(changed);
            var closed = new AtomicBoolean();
            return () -> {
                if (closed.compareAndSet(false, true)) {
                    subscription.close();
                    subscribers.decrementAndGet();
                }
            };
        }
    }
}
