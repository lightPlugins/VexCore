package dev.vexsoft.core.paper.vexui.action;

import dev.vexsoft.core.paper.vexui.VexUIScope;
import dev.vexsoft.core.paper.vexui.VexUISession;
import dev.vexsoft.core.paper.vexui.element.VexUIButton;
import dev.vexsoft.core.paper.vexui.element.VexUISize;
import dev.vexsoft.core.paper.vexui.element.VexUIText;
import dev.vexsoft.core.paper.vexui.state.VexUIObservable;
import dev.vexsoft.core.paper.vexui.state.VexUIState;
import dev.vexsoft.core.paper.vexui.state.VexUISubscription;
import dev.vexsoft.core.paper.vexui.style.VexUIButtonRole;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;

/** Viewer-owned asynchronous feedback for one action; domain operations remain caller-owned. */
public final class VexUIAsyncAction<T> {

    private static final VexUISubscription RELEASED = () -> { };
    // Keep request identity and presentation status in one atomic snapshot, including shutdown races.
    private final VexUIState<Execution<T>> execution = new VexUIState<>(new Execution<>(null, VexUIActionStatus.IDLE));
    private final VexUIObservable<VexUIActionStatus> status = new VexUIObservable<>() {
        @Override
        public VexUIActionStatus get() {
            return execution.get().status();
        }

        @Override
        public VexUISubscription subscribe(final Runnable changed) {
            return execution.subscribe(changed);
        }
    };

    /** Returns observable status for custom layouts or dependent button states. */
    public VexUIObservable<VexUIActionStatus> status() {
        return status;
    }

    /** Resets completed feedback on the entity thread; an in-flight request cannot be reset or started twice. */
    public void reset() {
        execution.update(current -> current.request() == null ? new Execution<>(null, VexUIActionStatus.IDLE) : current);
    }

    /**
     * Starts on the entity thread and returns false if already running or inactive. The supplier must be
     * nonblocking; completion callbacks return to that same view's entity context. Leaving the view suppresses
     * callbacks and releases captured UI references, but does not cancel an external domain transaction.
     */
    public boolean run(
        final VexUISession session,
        final Supplier<? extends CompletionStage<T>> operation,
        final BiConsumer<VexUISession, T> success,
        final BiConsumer<VexUISession, Throwable> failure
    ) {
        Objects.requireNonNull(operation, "operation");
        var scope = session.scope();
        if (!scope.isActive()) {
            return false;
        }
        var request = new Run<>(new Target<>(scope, session,
            Objects.requireNonNull(success, "success"), Objects.requireNonNull(failure, "failure")));
        try {
            Execution<T> previous;
            do {
                previous = execution.get();
                if (previous.request() != null) {
                    return false;
                }
            } while (!execution.compareAndSet(previous, new Execution<>(request, VexUIActionStatus.RUNNING)));
            request.attach(scope.onClose(() -> abandoned(request)));
            if (execution.get().request() != request) {
                return false;
            }
            Objects.requireNonNull(operation.get(), "operation result").whenComplete((value, error) -> {
                var target = request.target.get();
                if (target != null) {
                    target.scope().update(() -> completed(request, value, error));
                }
            });
        } catch (RuntimeException error) {
            completed(request, null, error);
        }
        return true;
    }

    /** Observes status and creates a themed button that cannot be clicked while work is running. */
    public VexUIButton button(
        final VexUISession session,
        final String action,
        final VexUIActionLabels labels,
        final Component tooltip
    ) {
        var state = session.bind(status());
        return new VexUIButton(new VexUISize(-1, session.styleSheet().buttonHeight(), 1),
            new VexUIText(labels.label(state)), session.styleSheet().primary(),
            state != VexUIActionStatus.RUNNING, action, tooltip)
            .withRole(state == VexUIActionStatus.ERROR ? VexUIButtonRole.DANGER : VexUIButtonRole.PRIMARY);
    }

    private void abandoned(final Run<T> request) {
        try {
            execution.update(current -> current.request() == request
                ? new Execution<>(null, VexUIActionStatus.IDLE) : current);
        } finally {
            request.clear();
        }
    }

    private void completed(final Run<T> request, final T value, final Throwable error) {
        var previous = execution.get();
        if (previous.request() != request) {
            return;
        }
        var target = request.target.get();
        boolean active = target != null && target.scope().isActive();
        VexUIActionStatus next = !active ? VexUIActionStatus.IDLE
            : error == null ? VexUIActionStatus.SUCCESS : VexUIActionStatus.ERROR;
        try {
            if (!execution.compareAndSet(previous, new Execution<>(null, next))) {
                return;
            }
        } finally {
            request.clear();
        }
        if (!active || !target.scope().isActive()) {
            return;
        }
        if (error == null) {
            target.success().accept(target.session(), value);
        } else {
            Throwable cause = error;
            while (cause instanceof CompletionException && cause.getCause() != null) {
                cause = cause.getCause();
            }
            target.failure().accept(target.session(), cause);
        }
    }

    private record Execution<T>(Run<T> request, VexUIActionStatus status) {
    }

    private record Target<T>(
        VexUIScope scope,
        VexUISession session,
        BiConsumer<VexUISession, T> success,
        BiConsumer<VexUISession, Throwable> failure
    ) {
    }

    private static final class Run<T> {
        private final AtomicReference<Target<T>> target;
        private final AtomicReference<VexUISubscription> subscription = new AtomicReference<>();

        private Run(final Target<T> target) {
            this.target = new AtomicReference<>(target);
        }

        private void attach(final VexUISubscription value) {
            if (!subscription.compareAndSet(null, value)) {
                value.close();
            }
        }

        private void clear() {
            target.set(null);
            var release = subscription.getAndSet(RELEASED);
            if (release != null && release != RELEASED) {
                release.close();
            }
        }
    }
}
