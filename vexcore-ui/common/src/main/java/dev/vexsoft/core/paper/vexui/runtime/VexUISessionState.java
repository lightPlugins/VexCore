package dev.vexsoft.core.paper.vexui.runtime;

import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.paper.scheduler.VexTask;
import dev.vexsoft.core.paper.service.scheduler.ScheduleService;
import dev.vexsoft.core.paper.vexui.VexUIAction;
import dev.vexsoft.core.paper.vexui.VexUIClickSound;
import dev.vexsoft.core.paper.vexui.VexUIFrame;
import dev.vexsoft.core.paper.vexui.VexUIDebugOptions;
import dev.vexsoft.core.paper.vexui.VexUIObservable;
import dev.vexsoft.core.paper.vexui.VexUIScope;
import dev.vexsoft.core.paper.vexui.VexUIStyleSheet;
import dev.vexsoft.core.paper.vexui.VexUISubscription;
import dev.vexsoft.core.paper.vexui.VexUISearch;
import dev.vexsoft.core.paper.vexui.VexUISession;
import dev.vexsoft.core.paper.vexui.VexUIView;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.entity.Player;
import org.bukkit.plugin.IllegalPluginAccessException;

/** Serializes viewer operations and invalidates opaque action tokens after each render or transition. */
@SuppressWarnings("UnstableApiUsage")
public final class VexUISessionState implements VexUISession {

    private final UUID id = UUID.randomUUID();
    private final ServiceOwner owner;
    private final Player player;
    private final VexUICoordinatorService coordinator;
    private final ScheduleService scheduler;
    private final VexUIRenderer renderer;
    private final VexUIDialogService dialogs;
    private final Supplier<VexUIClickSound> clickSound;
    private final Supplier<VexUIStyleSheet> styleSheet;
    private final Map<VexUIObservable<?>, VexUISubscription> bindings = new IdentityHashMap<>();
    private final Set<VexUIObservable<?>> readBindings = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Deque<VexUIView> views = new ArrayDeque<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean pending = new AtomicBoolean();
    private final Set<Key> queuedActions = ConcurrentHashMap.newKeySet();
    private volatile VexTask refreshTask;
    private volatile Map<Key, VexUIAction> actions = Map.of();
    private Map<Key, String> routeNames = Map.of();
    private Map<Key, VexUIClickSound> routeSounds = Map.of();
    private VexUIFrame lastFrame;
    private VexUIStyleSheet lastStyle;
    private VexUIDebugOptions lastDebug;
    private volatile VexUIDebugOptions debugOptions = VexUIDebugOptions.disabled();
    private volatile VexUIStyleSheet styleOverride;
    private volatile ViewScope viewScope = new ViewScope();
    private boolean rendering;
    private volatile Key closeAction;
    private volatile boolean consumed;
    private VexUISearch search;

    VexUISessionState(
        final ServiceOwner owner,
        final Player player,
        final VexUIView initial,
        final VexUICoordinatorService coordinator,
        final ScheduleService scheduler,
        final VexUIRenderer renderer,
        final VexUIDialogService dialogs,
        final Supplier<VexUIClickSound> clickSound,
        final Supplier<VexUIStyleSheet> styleSheet
    ) {
        this.owner = owner;
        this.player = player;
        this.coordinator = coordinator;
        this.scheduler = scheduler;
        this.renderer = renderer;
        this.dialogs = dialogs;
        this.clickSound = clickSound;
        this.styleSheet = styleSheet;
        views.push(initial);
    }

    public ServiceOwner owner() {
        return owner;
    }

    @Override
    public UUID id() {
        return id;
    }

    @Override
    public Player player() {
        return player;
    }

    @Override
    public VexUIStyleSheet styleSheet() {
        var override = styleOverride;
        return override == null ? styleSheet.get() : override;
    }

    @Override
    public void setStyleSheet(final VexUIStyleSheet value) {
        styleOverride = value;
        refresh();
    }

    @Override
    public synchronized <T> T bind(final VexUIObservable<T> observable) {
        if (!rendering) {
            throw new IllegalStateException("VexUI bindings must be read during render");
        }
        Objects.requireNonNull(observable, "observable");
        readBindings.add(observable);
        bindings.computeIfAbsent(observable,
            value -> Objects.requireNonNull(value.subscribe(this::refresh), "subscription"));
        return observable.get();
    }

    @Override
    public VexUIScope scope() {
        return viewScope;
    }

    @Override
    public List<Component> wrap(final Component text, final int width) {
        return renderer.wrap(text, width);
    }

    @Override
    public void setDebugOptions(final VexUIDebugOptions options) {
        debugOptions = Objects.requireNonNull(options, "options");
        refresh();
    }

    @Override
    public void refresh() {
        if (!isClosed() && coordinator.active(this) && pending.compareAndSet(false, true)) {
            refreshTask = scheduler.runFor(player, () -> {
                pending.set(false);
                if (!isClosed() && coordinator.active(this)) {
                    try {
                        render();
                    } catch (RuntimeException failure) {
                        close();
                        throw failure;
                    }
                }
            }, this::disconnected).orElse(null);
            if (refreshTask == null) {
                pending.set(false);
                disconnected();
            }
        }
    }

    @Override
    public void update(final Runnable mutation) {
        if (isClosed()) {
            return;
        }
        scheduler.runFor(player, () -> {
            synchronized (this) {
                if (!isClosed() && coordinator.active(this)) {
                    try {
                        mutation.run();
                    } finally {
                        refresh();
                    }
                }
            }
        }, this::disconnected);
    }

    @Override
    public void navigate(final VexUIView view) {
        Objects.requireNonNull(view, "view");
        update(() -> {
            invalidateView();
            views.push(view);
        });
    }

    @Override
    public void back() {
        update(() -> {
            if (views.size() < 2) {
                close();
            } else {
                invalidateView();
                views.pop().closed();
            }
        });
    }

    @Override
    public void search(final VexUISearch form) {
        scheduler.runFor(player, () -> {
            synchronized (this) {
                if (!isClosed() && coordinator.active(this)) {
                    invalidateView();
                    search = form;
                    showSearch();
                }
            }
        }, this::disconnected);
    }

    @Override
    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public void close() {
        retire(true);
    }

    void retire(final boolean closeScreen) {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        boolean wasActive = coordinator.active(this);
        coordinator.release(this);
        actions = Map.of();
        queuedActions.clear();
        var task = refreshTask;
        if (task != null) {
            task.cancel();
        }
        try {
            releaseViews();
        } finally {
            if (closeScreen && wasActive) {
                closeDialog();
            }
        }
    }

    void disconnected() {
        closed.set(true);
        actions = Map.of();
        queuedActions.clear();
        coordinator.release(this);
        var task = refreshTask;
        if (task != null) {
            task.cancel();
        }
        releaseViews();
    }

    void accept(final Key action, final String submittedText) {
        if (isClosed() || !coordinator.active(this)) {
            return;
        }
        // This is a cheap admission check only; the entity task must still validate the current frame.
        // Closing remains available while a normal action is queued or waiting for its refresh.
        if (!action.equals(closeAction) && (consumed || !actions.containsKey(action))) {
            return;
        }
        if (!queuedActions.add(action)) {
            return;
        }
        try {
            if (scheduler.runFor(player, () -> applyQueuedAction(action, submittedText), this::disconnected).isEmpty()) {
                disconnected();
            }
        } catch (RuntimeException failure) {
            queuedActions.remove(action);
            throw failure;
        }
    }

    private void applyQueuedAction(final Key action, final String submittedText) {
        try {
            applyAction(action, submittedText);
        } finally {
            queuedActions.remove(action);
        }
    }

    private synchronized void applyAction(final Key action, final String submittedText) {
        if (isClosed() || !coordinator.active(this)) {
            return;
        }
        if (action.equals(closeAction)) {
            close();
            return;
        }
        var handler = actions.get(action);
        if (handler == null || consumed) {
            return;
        }
        consumed = true;
        try {
            if (search != null) {
                var form = search;
                search = null;
                if (action.value().endsWith("/submit")) {
                    form.accepted().accept(submittedText == null ? "" : submittedText);
                }
            } else {
                playClickSound(action);
                handler.execute(this);
            }
        } finally {
            refresh();
        }
    }

    private void playClickSound(final Key action) {
        var sound = routeSounds.get(action);
        if (sound == null) {
            sound = lastFrame.clickSound();
        }
        if (sound == null) {
            sound = clickSound.get();
        }
        if (sound.enabled()) {
            player.playSound(sound.asSound());
        }
    }

    private void closeDialog() {
        var plugin = scheduler.getOwner();
        if (plugin != null && !plugin.isEnabled()) {
            return;
        }
        try {
            scheduler.runFor(player, () -> {
                if (!coordinator.occupied(player.getUniqueId())) {
                    dialogs.close(player);
                }
            });
        } catch (IllegalPluginAccessException exception) {
            // Paper may disable the plugin between the enabled check and task registration.
            if (plugin == null || plugin.isEnabled()) {
                throw exception;
            }
        }
    }

    private synchronized void render() {
        if (search != null) {
            return;
        }
        VexUIFrame frame;
        rendering = true;
        readBindings.clear();
        try {
            frame = views.peek().render(this);
        } finally {
            rendering = false;
            releaseUnusedBindings();
        }
        if (isClosed() || !coordinator.active(this)) {
            return;
        }
        var style = styleSheet();
        var debug = debugOptions;
        if (!consumed && lastFrame != null && frame.title().equals(lastFrame.title())
            && frame.closeLabel().equals(lastFrame.closeLabel()) && frame.width() == lastFrame.width()
            && frame.compactCloseButton() == lastFrame.compactCloseButton()
            && frame.content().equals(lastFrame.content()) && frame.actions().keySet().equals(lastFrame.actions().keySet())
            && style.equals(lastStyle) && debug.equals(lastDebug)) {
            Map<Key, VexUIAction> refreshed = new HashMap<>();
            routeNames.forEach((key, name) -> refreshed.put(key, frame.actions().get(name)));
            actions = Map.copyOf(refreshed);
            lastFrame = frame;
            return;
        }
        String token = "vexui/" + id + "/" + UUID.randomUUID();
        Map<Key, VexUIAction> nextActions = new HashMap<>();
        Map<Key, String> names = new HashMap<>();
        Map<Key, VexUIClickSound> sounds = new HashMap<>();
        var rendered = renderer.render(frame, (action, sound) -> {
            var handler = frame.actions().get(action);
            if (handler == null) {
                throw new IllegalArgumentException("Missing VexUI action " + action);
            }
            Key key = Key.key("vexcore", token + "/" + nextActions.size());
            nextActions.put(key, handler);
            names.put(key, action);
            if (sound != null) {
                sounds.put(key, sound);
            }
            return ClickEvent.custom(key, null);
        }, style, debug);
        closeAction = Key.key("vexcore", token + "/close");
        actions = Map.copyOf(nextActions);
        routeNames = Map.copyOf(names);
        routeSounds = Map.copyOf(sounds);
        lastFrame = frame;
        lastStyle = style;
        lastDebug = debug;
        consumed = false;
        dialogs.show(player, frame, rendered, closeAction);
    }

    private synchronized void showSearch() {
        String token = "vexui/" + id + "/" + UUID.randomUUID();
        Key submit = Key.key("vexcore", token + "/submit");
        Key cancel = Key.key("vexcore", token + "/cancel");
        actions = Map.of(submit, VexUISession::refresh, cancel, VexUISession::refresh);
        routeNames = Map.of();
        routeSounds = Map.of();
        lastFrame = null;
        closeAction = null;
        consumed = false;
        dialogs.search(player, search, submit, cancel);
    }

    private synchronized void releaseViews() {
        RuntimeException failure = null;
        try {
            invalidateView();
        } catch (RuntimeException exception) {
            failure = exception;
        }
        while (!views.isEmpty()) {
            try {
                views.pop().closed();
            } catch (RuntimeException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private void releaseUnusedBindings() {
        List<Runnable> cleanup = new ArrayList<>();
        var iterator = bindings.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (!readBindings.contains(entry.getKey())) {
                cleanup.add(entry.getValue()::close);
                iterator.remove();
            }
        }
        runCleanup(cleanup);
    }

    private void invalidateView() {
        List<Runnable> cleanup = new ArrayList<>();
        bindings.values().forEach(binding -> cleanup.add(binding::close));
        bindings.clear();
        readBindings.clear();
        var previous = viewScope;
        viewScope = new ViewScope();
        cleanup.addAll(previous.cleanup);
        previous.cleanup.clear();
        runCleanup(cleanup);
    }

    private static void runCleanup(final List<Runnable> callbacks) {
        RuntimeException failure = null;
        for (var callback : callbacks) {
            try {
                callback.run();
            } catch (RuntimeException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private synchronized boolean attachCleanup(final ViewScope scope, final Runnable callback) {
        if (!scope.isActive()) {
            return false;
        }
        scope.cleanup.add(callback);
        return true;
    }

    private synchronized void detachCleanup(final ViewScope scope, final Runnable callback) {
        scope.cleanup.remove(callback);
    }

    private final class ViewScope implements VexUIScope {
        private final List<Runnable> cleanup = new ArrayList<>();

        @Override
        public boolean isActive() {
            return viewScope == this && !isClosed() && coordinator.active(VexUISessionState.this);
        }

        @Override
        public void update(final Runnable mutation) {
            if (isActive()) {
                VexUISessionState.this.update(() -> {
                    if (isActive()) {
                        mutation.run();
                    }
                });
            }
        }

        @Override
        public VexUISubscription onClose(final Runnable callback) {
            Objects.requireNonNull(callback, "callback");
            Runnable registration = callback::run;
            if (attachCleanup(this, registration)) {
                return () -> detachCleanup(this, registration);
            }
            callback.run();
            return () -> { };
        }
    }
}
