package dev.vexsoft.core.paper.scene.service;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.scene.SceneCaption;
import dev.vexsoft.core.paper.scene.SceneDefinition;
import dev.vexsoft.core.paper.scene.ScenePlayback;
import dev.vexsoft.core.paper.scheduler.VexTask;
import dev.vexsoft.core.paper.screenui.ScreenUi;
import dev.vexsoft.core.paper.screenui.layout.HorizontalAlignment;
import dev.vexsoft.core.paper.screenui.layout.TextBlockLayout;
import dev.vexsoft.core.paper.screenui.layout.TextOverflow;
import dev.vexsoft.core.paper.screenui.layout.VerticalAlignment;
import dev.vexsoft.core.paper.screenui.render.ScreenUiRenderer;
import dev.vexsoft.core.paper.service.scheduler.ScheduleService;
import dev.vexsoft.core.paper.service.screenui.ScreenUiService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;

/** Runs scenes against a monotonic clock so caption timing follows client-side audio through tick delays. */
@Dependencies(ScheduleService.class)
public final class VexSceneCoordinatorService implements SceneCoordinatorService, AutoCloseable {

    private static final String SCREEN_ID = "vexcore:scene";
    private static final TextColor SHADOW = TextColor.color(0x191323);
    private final ScheduleService schedules;
    private final LongSupplier clock;
    private final Map<UUID, Playback> active = new ConcurrentHashMap<>();
    private volatile boolean closed;

    public VexSceneCoordinatorService(final VexServiceRegistry services) {
        this(services.require(ScheduleService.class), System::nanoTime);
    }

    VexSceneCoordinatorService(final ScheduleService schedules, final LongSupplier clock) {
        this.schedules = schedules;
        this.clock = clock;
    }

    @Override
    public Optional<ScenePlayback> play(
        final ServiceOwner owner, final ScreenUiService screens, final Player viewer, final SceneDefinition definition,
        final SceneCameraPlayback camera
    ) {
        if (closed || !viewer.isOnline() || viewer.isDead() || active.containsKey(viewer.getUniqueId())) {
            return Optional.empty();
        }
        var captions = definition.captions().stream().map(caption -> prepare(caption, definition.layout())).toList();
        var playback = new Playback(owner, viewer, definition, captions, camera);
        if (active.putIfAbsent(viewer.getUniqueId(), playback) != null) {
            return Optional.empty();
        }
        try {
            if (closed || !playback.start(screens)) {
                playback.close();
                return Optional.empty();
            }
            return Optional.of(playback);
        } catch (RuntimeException failure) {
            playback.close();
            throw failure;
        }
    }

    @Override
    public boolean reveal(final Player viewer) {
        var playback = active.get(viewer.getUniqueId());
        return playback != null && playback.viewer == viewer && playback.advance(true);
    }

    @Override
    public void interrupt(final Player viewer) {
        var playback = active.get(viewer.getUniqueId());
        if (playback != null && playback.viewer == viewer) {
            playback.close();
        }
    }

    @Override
    public void closeOwner(final ServiceOwner owner) {
        active.values().stream().filter(playback -> playback.owner == owner).forEach(this::retire);
    }

    @Override
    public void close() {
        closed = true;
        active.values().forEach(Playback::close);
    }

    private void retire(final Playback playback) {
        if (schedules.runFor(playback.viewer, playback::close, playback::close).isEmpty()) {
            playback.close();
        }
    }

    private static PreparedCaption prepare(final SceneCaption caption, final TextBlockLayout layout) {
        var lines = ScreenUiRenderer.layout(caption.lines(), layout);
        if (lines.size() > 8) {
            throw new IllegalArgumentException("A scene caption may wrap to at most eight visual lines");
        }
        return new PreparedCaption(caption, lines, lines.stream().mapToInt(line -> line.glyphs().size()).sum());
    }

    private static TextBlockLayout lineLayout(
        final TextBlockLayout layout, final ScreenUiRenderer.Line line, final boolean shadow
    ) {
        int offset = shadow ? 1 : 0;
        return TextBlockLayout.builder()
            .anchor(layout.anchor()).x(line.x() + offset).y(line.y() + offset)
            .scale(layout.scale()).maxWidth(layout.maxWidth()).overflow(TextOverflow.REJECT)
            .horizontalAlignment(HorizontalAlignment.LEFT).verticalAlignment(VerticalAlignment.TOP)
            .layer(layout.layer() + (shadow ? 0 : 1))
            .build();
    }

    private static String id(final int index, final boolean shadow) {
        return (shadow ? "shadow-" : "caption-") + index;
    }

    private record PreparedCaption(SceneCaption caption, List<ScreenUiRenderer.Line> lines, int glyphs) {
    }

    private final class Playback implements ScenePlayback {

        private final ServiceOwner owner;
        private final Player viewer;
        private final SceneDefinition definition;
        private final List<PreparedCaption> captions;
        private final SceneCameraPlayback camera;
        private final CompletableFuture<Result> completion = new CompletableFuture<>();
        private ScreenUi screen;
        private VexTask task;
        private long started;
        private boolean narrationStarted;
        private boolean finished;
        private int page = -1;
        private int visible = -1;
        private boolean revealed;

        private Playback(
            final ServiceOwner owner, final Player viewer, final SceneDefinition definition,
            final List<PreparedCaption> captions, final SceneCameraPlayback camera
        ) {
            this.owner = owner;
            this.viewer = viewer;
            this.definition = definition;
            this.captions = captions;
            this.camera = camera;
        }

        @Override
        public CompletionStage<Result> completion() {
            return completion.minimalCompletionStage();
        }

        @Override
        public void close() {
            finish(Result.INTERRUPTED);
        }

        private synchronized boolean start(final ScreenUiService screens) {
            if (finished) {
                return false;
            }
            screen = screens.open(viewer, SCREEN_ID);
            task = schedules.runForTimer(viewer, 1, 1, () -> advance(false), this::close).orElse(null);
            if (task == null || finished) {
                return false;
            }
            if (camera != null) {
                camera.start();
            }
            started = clock.getAsLong();
            if (definition.narration() != null) {
                if (camera == null) {
                    viewer.playSound(definition.narration(), Sound.Emitter.self());
                } else {
                    camera.playSound(definition.narration());
                }
                narrationStarted = true;
            }
            return advance(false);
        }

        private synchronized boolean advance(final boolean reveal) {
            if (finished || screen == null) {
                return false;
            }
            if (!viewer.isOnline() || viewer.isDead() || screen.isClosed()) {
                close();
                return false;
            }
            long elapsed = TimeUnit.NANOSECONDS.toMillis(clock.getAsLong() - started);
            if (elapsed >= definition.durationMillis()) {
                finish(Result.COMPLETED);
                return false;
            }
            int next = page;
            while (next + 1 < captions.size() && captions.get(next + 1).caption().startMillis() <= elapsed) {
                next++;
            }
            try {
                if (camera != null && !reveal && elapsed > 0) {
                    camera.advance(elapsed);
                }
                if (next != page) {
                    page = next;
                    visible = -1;
                    revealed = false;
                    putLines(captions.get(page).lines());
                }
                if (page >= 0) {
                    revealed |= reveal;
                    render(captions.get(page), elapsed);
                }
                return true;
            } catch (RuntimeException failure) {
                close();
                schedules.getOwner().getLogger().log(Level.WARNING, "Unable to render scene for " + viewer.getUniqueId(),
                    failure);
                return false;
            }
        }

        private void putLines(final List<ScreenUiRenderer.Line> lines) {
            for (int index = 0; index < 8; index++) {
                if (index < lines.size()) {
                    var line = lines.get(index);
                    screen.textBlock(id(index, true), List.of(Component.empty()), lineLayout(definition.layout(), line, true));
                    screen.textBlock(id(index, false), List.of(Component.empty()), lineLayout(definition.layout(), line, false));
                } else {
                    screen.remove(id(index, true));
                    screen.remove(id(index, false));
                }
            }
        }

        private void render(final PreparedCaption prepared, final long elapsed) {
            long revealMillis = prepared.caption().revealMillis();
            double progress = revealed || revealMillis == 0 ? 1.0
                : Math.min(1.0, (double) (elapsed - prepared.caption().startMillis()) / revealMillis);
            int count = (int) Math.floor(prepared.glyphs() * progress);
            if (count == visible) {
                return;
            }
            visible = count;
            for (int index = 0; index < prepared.lines().size(); index++) {
                var glyphs = prepared.lines().get(index).glyphs();
                var text = Component.text();
                var shadow = Component.text();
                int limit = Math.min(count, glyphs.size());
                for (int glyph = 0; glyph < limit; glyph++) {
                    var current = glyphs.get(glyph);
                    text.append(Component.text(current.text()).style(current.style()));
                    shadow.append(Component.text(current.text()).style(current.style()).color(SHADOW));
                }
                screen.setLines(id(index, true), List.of(shadow.build()));
                screen.setLines(id(index, false), List.of(text.build()));
                count -= limit;
            }
        }

        private synchronized void finish(final Result result) {
            if (finished) {
                return;
            }
            finished = true;
            try {
                if (task != null) {
                    task.cancel();
                }
                if (result == Result.INTERRUPTED && narrationStarted && viewer.isOnline()) {
                    viewer.stopSound(definition.narration());
                }
            } finally {
                try {
                    if (screen != null) {
                        screen.close();
                    }
                } finally {
                    try {
                        if (camera != null) {
                            camera.close();
                        }
                    } finally {
                        active.remove(viewer.getUniqueId(), this);
                        completion.complete(result);
                    }
                }
            }
        }
    }
}
