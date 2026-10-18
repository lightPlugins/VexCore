package dev.vexsoft.core.paper.service.upgrade;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.scheduler.VexTask;
import dev.vexsoft.core.paper.screenui.HorizontalAlignment;
import dev.vexsoft.core.paper.screenui.ScreenAnchor;
import dev.vexsoft.core.paper.screenui.ScreenUi;
import dev.vexsoft.core.paper.screenui.TextBlockLayout;
import dev.vexsoft.core.paper.screenui.TextOverflow;
import dev.vexsoft.core.paper.screenui.TextureLayout;
import dev.vexsoft.core.paper.screenui.UiTexture;
import dev.vexsoft.core.paper.service.scheduler.ScheduleService;
import dev.vexsoft.core.paper.service.screenui.ScreenUiService;
import dev.vexsoft.core.paper.upgrade.UpgradeAnimationOutcome;
import dev.vexsoft.core.paper.upgrade.UpgradeAnimationRequest;
import dev.vexsoft.core.paper.upgrade.UpgradeAnimationSound;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;

/** Finite tick-by-tick pixel rituals that wait for authoritative persistence before revealing success. */
@Dependencies({ScheduleService.class, ScreenUiService.class})
public final class VexUpgradeAnimationService implements UpgradeAnimationService, AutoCloseable {

    private static final int CHARGE_TINT = 0xE8E0ED;
    private static final int PROGRESS_COLOR = 0xB9BAC3;
    private static final int PROGRESS_SHADOW = 0x777780;
    private static final int OFFSET_Y = -30;
    private final ScheduleService schedules;
    private final ScreenUiService screens;
    private final LongSupplier seeds;
    private final Map<UUID, Animation> animations = new ConcurrentHashMap<>();
    private volatile boolean closed;

    public VexUpgradeAnimationService(VexServiceRegistry services) {
        this(services, () -> UUID.randomUUID().getLeastSignificantBits());
    }

    VexUpgradeAnimationService(VexServiceRegistry services, LongSupplier seeds) {
        schedules = services.require(ScheduleService.class);
        screens = services.require(ScreenUiService.class);
        this.seeds = Objects.requireNonNull(seeds, "seeds");
    }

    @Override
    public CompletableFuture<UpgradeAnimationOutcome> play(Player player, UpgradeAnimationRequest request) {
        if (closed) {
            return CompletableFuture.failedFuture(new IllegalStateException("Upgrade animation owner is closed"));
        }
        var animation = new Animation(player, request, seeds.getAsLong());
        if (animations.putIfAbsent(player.getUniqueId(), animation) != null) {
            return CompletableFuture.failedFuture(new IllegalStateException("An upgrade animation is already active"));
        }
        try {
            var scheduled = schedules.runFor(player, () -> start(animation),
                () -> stop(animation, UpgradeAnimationOutcome.CANCELLED));
            if (scheduled.isEmpty() && animation.screen == null) {
                stop(animation, UpgradeAnimationOutcome.CANCELLED);
            }
        } catch (RuntimeException failure) {
            stop(animation, UpgradeAnimationOutcome.ERROR);
        }
        return animation.completion;
    }

    @Override
    public void cancel(Player player) {
        var animation = animations.get(player.getUniqueId());
        if (animation != null) {
            stop(animation, UpgradeAnimationOutcome.CANCELLED);
        }
    }

    @Override
    public void skip(Player player) {
        var animation = animations.get(player.getUniqueId());
        if (animation != null) {
            animation.skipRequested = true;
        }
    }

    @Override
    public boolean active(Player player) {
        return animations.containsKey(player.getUniqueId());
    }

    @Override
    public void close() {
        closed = true;
        animations.values().forEach(animation -> stop(animation, UpgradeAnimationOutcome.CANCELLED));
    }

    private void start(Animation animation) {
        if (animation.stopped.get() || closed || !animation.player.isOnline()) {
            stop(animation, UpgradeAnimationOutcome.CANCELLED);
            return;
        }
        try {
            animation.world = animation.player.getWorld();
            // The caller has already reserved its subject. Commit once before advancing any visual step.
            resolve(animation);
            if (animation.stopped.get() || closed) {
                stop(animation, UpgradeAnimationOutcome.CANCELLED);
                return;
            }
            if (!animation.skipRequested) {
                animation.screen = screens.open(animation.player, "upgrade-ritual");
                if (animation.stopped.get() || closed) {
                    animation.screen.close();
                    stop(animation, UpgradeAnimationOutcome.CANCELLED);
                    return;
                }
                var texts = animation.request.texts();
                sprite(animation, "frame", "frame", 224, 240, -112, -114, 100, 0xFFFFFF);
                text(animation, "title", texts.title(), -102);
                text(animation, "subject", texts.subject(), -83);
                text(animation, "upgrade", texts.upgrade(), -66);
                text(animation, "progress", progress(animation, 0), -1);
                text(animation, "phase", texts.charging(), 92);
                if (!texts.skipHint().equals(Component.empty())) {
                    text(animation, "skip_hint", texts.skipHint(), 110);
                }
                render(animation, animation.timeline.frame(0), false);
                startMusic(animation);
                sound(animation, "start");
            }
            animation.task = schedules.runForTimer(animation.player, 1, 1,
                () -> tick(animation), () -> stop(animation, UpgradeAnimationOutcome.CANCELLED)).orElse(null);
            if (animation.task == null) {
                stop(animation, UpgradeAnimationOutcome.CANCELLED);
            } else if (animation.stopped.get()) {
                animation.task.cancel();
                if (animation.screen != null) {
                    animation.screen.close();
                }
            }
        } catch (RuntimeException failure) {
            stop(animation, UpgradeAnimationOutcome.ERROR);
        }
    }

    private void tick(Animation animation) {
        if (animation.stopped.get()) {
            return;
        }
        if (closed || !animation.player.isOnline() || animation.player.getWorld() != animation.world
            || animation.screen != null && animation.screen.isClosed() && !animation.presentationHidden) {
            stop(animation, UpgradeAnimationOutcome.CANCELLED);
            return;
        }
        try {
            animation.tick++;
            playSounds(animation);
            var settings = animation.request.settings();
            if (animation.skipRequested) {
                skipPresentation(animation);
                return;
            }
            if (animation.finishAt >= 0) {
                boolean success = Boolean.TRUE.equals(animation.result);
                render(animation, new UpgradeAnimationTimeline.Frame(success ? 100 : animation.finishProgress,
                    success ? 180 : Math.max(0, (int) Math.round(animation.finishProgress * 1.8)
                        - (animation.tick - animation.finishAt) * 9),
                    success ? 3 : 0, false, 0), true);
                if (animation.tick - animation.finishAt >= settings.finishTicks()) {
                    stop(animation, success ? UpgradeAnimationOutcome.SUCCESS : UpgradeAnimationOutcome.FAILURE);
                }
                return;
            }
            var music = animation.request.settings().music();
            if (animation.musicPlaying && music.loop()
                && animation.tick - animation.musicStartedAt >= music.durationTicks()) {
                stopMusic(animation);
                startMusic(animation);
            }
            if (!animation.resolved) {
                render(animation, animation.timeline.frame(0), false);
                if (animation.tick - animation.resolveAt > settings.resolutionTimeoutTicks()) {
                    stop(animation, UpgradeAnimationOutcome.ERROR);
                }
                return;
            }
            if (animation.resolveFailure) {
                stop(animation, UpgradeAnimationOutcome.ERROR);
                return;
            }
            animation.chargeTick = Math.min(animation.timeline.duration(), animation.chargeTick + 1);
            var retained = animation.timeline.frame(animation.chargeTick);
            if (retained.progress() != animation.lastProgress) {
                animation.lastProgress = retained.progress();
                animation.stepAt = animation.tick;
            }
            var frame = animation.timeline.frame(animation.chargeTick, animation.tick - animation.stepAt,
                settings.holdTicks());
            render(animation, frame, false);
            if (frame.step()) {
                sound(animation, "step");
            }
            if (frame.runes() > animation.runes) {
                sound(animation, "rune");
                animation.runes = frame.runes();
            }
            boolean success = Boolean.TRUE.equals(animation.result);
            if (!success && animation.chargeTick >= animation.timeline.resolutionTick()
                && animation.chargeTick < animation.timeline.duration()
                && frame.barFill() == Math.round(frame.progress() * 1.8)) {
                finish(animation, false, frame.progress());
                return;
            }
            // This is a reveal shortcut for an already committed success, never an additional gameplay roll.
            if (success && frame.step()
                && animation.timeline.checkBreakthrough(animation.chargeTick, settings.instantSuccessChance())) {
                finish(animation, true, frame.progress());
                return;
            }
            if (animation.chargeTick == animation.timeline.duration() && animation.holdAt < 0) {
                animation.holdAt = animation.tick;
                setText(animation, "phase", animation.request.texts().holding());
                sound(animation, "hold");
            }
            // A failure selected for 99% shares the entire last-rune hold with success; reaching it reveals nothing.
            if (animation.holdAt >= 0 && animation.tick - animation.holdAt >= settings.holdTicks()) {
                finish(animation, success, frame.progress());
            }
        } catch (RuntimeException failure) {
            stop(animation, UpgradeAnimationOutcome.ERROR);
        }
    }

    private void finish(Animation animation, boolean success, int progress) {
        animation.finishAt = animation.tick;
        animation.finishProgress = progress;
        animation.sounds.clear();
        stopMusic(animation);
        setText(animation, "phase", success ? animation.request.texts().success() : animation.request.texts().failure());
        render(animation, new UpgradeAnimationTimeline.Frame(success ? 100 : progress,
            success ? 180 : (int) Math.round(progress * 1.8), success ? 3 : 0, false, 0), true);
        sound(animation, success ? "success" : "failure");
    }

    private void resolve(Animation animation) {
        if (animation.resolving) {
            return;
        }
        // A committed result survives later presentation cancellation; the caller owns that recovery decision.
        animation.resolving = true;
        animation.resolveAt = animation.tick;
        animation.request.resolve().get().whenComplete((success, failure) -> {
            animation.result = success;
            animation.resolveFailure = failure != null || success == null;
            animation.resolved = true;
        });
    }

    private void skipPresentation(Animation animation) {
        if (!animation.presentationHidden) {
            animation.presentationHidden = true;
            animation.sounds.clear();
            stopMusic(animation);
            if (animation.screen != null) {
                animation.screen.close();
            }
        }
        resolve(animation);
        if (animation.resolved) {
            if (animation.resolveFailure) {
                stop(animation, UpgradeAnimationOutcome.ERROR);
                return;
            }
            boolean success = Boolean.TRUE.equals(animation.result);
            if (animation.finishAt < 0) {
                var cues = animation.request.settings().sounds().getOrDefault(success ? "success" : "failure", List.of());
                if (!cues.isEmpty()) {
                    // A skip has no remaining sound timeline, but keeps one immediate result cue.
                    animation.finishAt = animation.tick;
                    play(animation, cues.getFirst());
                }
            }
            stop(animation, success ? UpgradeAnimationOutcome.SUCCESS : UpgradeAnimationOutcome.FAILURE);
        } else if (animation.tick - animation.resolveAt > animation.request.settings().resolutionTimeoutTicks()) {
            stop(animation, UpgradeAnimationOutcome.ERROR);
        }
    }

    private void render(Animation animation, UpgradeAnimationTimeline.Frame frame, boolean finishing) {
        double pulse = 0.5 - Math.cos(animation.tick * Math.PI / 16) * 0.5;
        boolean success = finishing && Boolean.TRUE.equals(animation.result);
        int finishAge = finishing ? animation.tick - animation.finishAt : 0;
        int successPhase = Math.min(15, finishAge * 16 / Math.max(1, animation.request.settings().finishTicks()));
        int core = ((finishing && !success ? animation.finishAt : animation.tick) / 2) % 16;
        int tint = finishing && !success
            ? blend(0x504050, 0xFFFFFF,
                Math.max(0, 1 - finishAge / (double) animation.request.settings().finishTicks()))
            : blend(CHARGE_TINT, 0xFFFFFF, Math.max(frame.flash(), pulse));
        if (success) {
            sprite(animation, "success_fx", "success_" + successPhase, 184, 104, -92, -45, 101, 0xFFFFFF);
        }
        sprite(animation, "core", success ? "burst_" + successPhase : "core_" + core,
            72, 72, -36, -45, 102, tint);
        for (int rune = 0; rune < 3; rune++) {
            boolean active = rune < frame.runes();
            boolean pendingLastRune = !finishing && rune == 2 && animation.holdAt >= 0;
            int runeTint = pendingLastRune ? blend(0xFFFFFF, 0xFFD090, 0.25 + pulse * 0.5) : 0xFFFFFF;
            double reveal = Math.clamp((successPhase - (rune == 1 ? 0 : 2)) / 7.0, 0, 1);
            int lift = success ? (int) Math.round(2 * Math.sin(reveal * Math.PI)) : 0;
            String asset = "rune_" + (rune + 1) + (active ? "" : "_inactive");
            sprite(animation, "rune_" + rune, asset, 22, 26, -51 + rune * 40, 25 - lift, 103, runeTint);
        }
        sprite(animation, "bar", "bar_" + frame.barFill(), 180, 12, -90, 64, 102,
            blend(CHARGE_TINT, 0xFFFFFF, frame.flash()));
        if (animation.renderedProgress != frame.progress()) {
            setText(animation, "progress", progress(animation, frame.progress()));
            animation.renderedProgress = frame.progress();
        }
    }

    private static int blend(int first, int second, double amount) {
        int red = (int) (((first >> 16) & 255) * (1 - amount) + ((second >> 16) & 255) * amount);
        int green = (int) (((first >> 8) & 255) * (1 - amount) + ((second >> 8) & 255) * amount);
        int blue = (int) ((first & 255) * (1 - amount) + (second & 255) * amount);
        return red << 16 | green << 8 | blue;
    }

    private Component progress(Animation animation, int value) {
        return animation.request.texts().progress().replaceText(builder -> builder.matchLiteral("%progress%")
            .replacement(Integer.toString(value)))
            // Older translations separate the percent sign; compact it to fit 100% on the anvil's face.
            .replaceText(builder -> builder.match("\\s+%").replacement("%"));
    }

    private void text(Animation animation, String id, Component text, int y) {
        Component foreground = styledText(id, text);
        boolean progress = id.equals("progress");
        textBlock(animation, id + "_shadow", recolor(foreground, progress ? PROGRESS_SHADOW : 0x9B969F), 1, y + 1, 109);
        textBlock(animation, id, foreground, 0, y, 110);
    }

    private void setText(Animation animation, String id, Component text) {
        Component foreground = styledText(id, text);
        boolean progress = id.equals("progress");
        animation.screen.setLines(id, List.of(foreground));
        animation.screen.setLines(id + "_shadow", List.of(recolor(foreground, progress ? PROGRESS_SHADOW : 0x9B969F)));
    }

    private void textBlock(Animation animation, String id, Component text, int x, int y, int layer) {
        animation.screen.textBlock(id, List.of(text), TextBlockLayout.builder().anchor(ScreenAnchor.CENTER)
            .x(x).y(y + OFFSET_Y).maxWidth(id.startsWith("progress") ? 24 : 208)
            .horizontalAlignment(HorizontalAlignment.CENTER)
            .overflow(TextOverflow.ELLIPSIS).layer(layer).build());
    }

    private Component styledText(String id, Component text) {
        return id.equals("progress") ? progressText(text)
            : panelText(text).colorIfAbsent(TextColor.color(0x57515D));
    }

    private Component progressText(Component text) {
        // Older published labels can mark nested spans bold, making 100% exceed the 24-pixel metal face.
        return text.children(text.children().stream().map(this::progressText).toList())
            .color(TextColor.color(PROGRESS_COLOR)).decoration(TextDecoration.BOLD, false);
    }

    private Component panelText(Component text) {
        var rendered = text.children(text.children().stream().map(this::panelText).toList());
        var color = text.color();
        if (color == null) {
            return rendered;
        }
        // Older published translations can still carry pale colors intended for the former dark panel.
        double brightness = color.red() * 0.299 + color.green() * 0.587 + color.blue() * 0.114;
        if (brightness <= 95) {
            return rendered;
        }
        double shade = 95 / brightness;
        return rendered.color(TextColor.color((int) Math.round(color.red() * shade),
            (int) Math.round(color.green() * shade), (int) Math.round(color.blue() * shade)));
    }

    private Component recolor(Component text, int color) {
        return text.children(text.children().stream().map(child -> recolor(child, color)).toList())
            .color(TextColor.color(color));
    }

    private void sprite(
        Animation animation, String id, String asset, int width, int height, int x, int y, int layer, int tint
    ) {
        animation.screen.textureBlock(id, UiTexture.sprite(Key.key("vexcore:upgrade/" + asset), width, height),
            TextureLayout.builder().anchor(ScreenAnchor.CENTER).x(x).y(y + OFFSET_Y).layer(layer)
                .tint(TextColor.color(tint)).build());
    }

    private void sound(Animation animation, String event) {
        for (var cue : animation.request.settings().sounds().getOrDefault(event, List.of())) {
            if (cue.delayTicks() == 0) {
                play(animation, cue);
            } else {
                animation.sounds.add(new PendingSound(animation.tick + cue.delayTicks(), cue));
            }
        }
    }

    private void startMusic(Animation animation) {
        var music = animation.request.settings().music();
        if (music == null || music.sound().volume() == 0) {
            return;
        }
        animation.musicPlaying = true;
        animation.musicStartedAt = animation.tick;
        // Self-emitted playback keeps the stereo track independent of the player's position and charge pitch.
        animation.player.playSound(Sound.sound(Key.key(music.sound().type()), Sound.Source.MASTER,
            music.sound().volume(), 1.0F));
    }

    private void stopMusic(Animation animation) {
        if (animation.musicPlaying) {
            animation.musicPlaying = false;
            animation.player.stopSound(animation.request.settings().music().sound().type(), SoundCategory.MASTER);
        }
    }

    private void playSounds(Animation animation) {
        Iterator<PendingSound> iterator = animation.sounds.iterator();
        while (iterator.hasNext()) {
            var pending = iterator.next();
            if (pending.tick() <= animation.tick) {
                play(animation, pending.cue());
                iterator.remove();
            }
        }
    }

    private void play(Animation animation, UpgradeAnimationSound cue) {
        var sound = cue.sound();
        if (sound.volume() > 0) {
            float pitch = sound.pitch();
            if (animation.finishAt < 0) {
                double progress = Math.min(1, animation.chargeTick / (double) animation.timeline.duration());
                pitch = (float) Math.min(2, pitch * (0.8 + progress * 0.5));
            }
            animation.player.playSound(animation.player.getLocation(), sound.type(), sound.volume(), pitch);
        }
    }

    private void stop(Animation animation, UpgradeAnimationOutcome result) {
        if (!animation.stopped.compareAndSet(false, true)) {
            return;
        }
        animations.remove(animation.player.getUniqueId(), animation);
        var completion = result;
        try {
            stopMusic(animation);
        } catch (RuntimeException failure) {
            completion = UpgradeAnimationOutcome.ERROR;
        }
        try {
            if (animation.task != null) {
                animation.task.cancel();
            }
        } catch (RuntimeException failure) {
            completion = UpgradeAnimationOutcome.ERROR;
        }
        try {
            if (animation.screen != null) {
                animation.screen.close();
            }
        } catch (RuntimeException failure) {
            completion = UpgradeAnimationOutcome.ERROR;
        } finally {
            animation.completion.complete(completion);
        }
    }

    private record PendingSound(int tick, UpgradeAnimationSound cue) {
    }

    private static final class Animation {

        private final Player player;
        private final UpgradeAnimationRequest request;
        private final UpgradeAnimationTimeline timeline;
        private final CompletableFuture<UpgradeAnimationOutcome> completion = new CompletableFuture<>();
        private final AtomicBoolean stopped = new AtomicBoolean();
        private final List<PendingSound> sounds = new ArrayList<>();
        private volatile boolean resolved;
        private volatile boolean resolveFailure;
        private volatile Boolean result;
        private volatile boolean skipRequested;
        private ScreenUi screen;
        private World world;
        private VexTask task;
        private int tick;
        private int chargeTick;
        private int lastProgress;
        private int renderedProgress = -1;
        private int stepAt;
        private int resolveAt;
        private int holdAt = -1;
        private int runes;
        private int finishAt = -1;
        private int finishProgress;
        private int musicStartedAt;
        private boolean musicPlaying;
        private boolean resolving;
        private boolean presentationHidden;

        private Animation(Player player, UpgradeAnimationRequest request, long seed) {
            this.player = player;
            this.request = request;
            timeline = request.settings().pacingFor(request.successChance())
                .map(pacing -> new UpgradeAnimationTimeline(pacing, seed))
                .orElseGet(() -> new UpgradeAnimationTimeline(request.settings().chargeTicks(), seed));
        }
    }
}
