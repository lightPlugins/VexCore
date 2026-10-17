package dev.vexsoft.core.paper.service.upgrade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.common.service.registry.DefaultServiceRegistry;
import dev.vexsoft.core.paper.scheduler.VexTask;
import dev.vexsoft.core.paper.screenui.ScreenUi;
import dev.vexsoft.core.paper.screenui.TextBlockLayout;
import dev.vexsoft.core.paper.screenui.TextureLayout;
import dev.vexsoft.core.paper.screenui.UiTexture;
import dev.vexsoft.core.paper.service.scheduler.ScheduleService;
import dev.vexsoft.core.paper.service.screenui.ScreenUiService;
import dev.vexsoft.core.paper.upgrade.UpgradeAnimationOutcome;
import dev.vexsoft.core.paper.upgrade.UpgradeAnimationPacing;
import dev.vexsoft.core.paper.upgrade.UpgradeAnimationRequest;
import dev.vexsoft.core.paper.upgrade.UpgradeAnimationSettings;
import dev.vexsoft.core.paper.upgrade.UpgradeAnimationTexts;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.LongStream;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

/** Exercises the real registry/factory, start-time durability, visual breakthroughs, and idempotent skips. */
final class VexUpgradeAnimationServiceTest {

    private static final UpgradeAnimationSettings SETTINGS = new UpgradeAnimationSettings(40, 2, 20, 40, Map.of());

    @Test
    void successWaitsForDurableResolutionAndCompletesExactlyOnceAfterTheFinalAnimation() throws Exception {
        try (var fixture = new Fixture()) {
            var completion = fixture.play();
            var callbacks = new AtomicInteger();
            completion.thenRun(callbacks::incrementAndGet);
            fixture.waitForResolution();
            int retained = fixture.screen.progress.getLast();
            fixture.tick(20);
            assertEquals(1, fixture.resolutions);
            assertFalse(completion.isDone());
            assertEquals(retained, fixture.screen.progress.getLast());
            assertFalse(fixture.screen.progress.contains(100));
            fixture.resolution.complete(true);
            for (int tick = 0; tick < 50 && !fixture.screen.progress.contains(100); tick++) {
                fixture.tick(1);
            }
            assertTrue(fixture.screen.progress.contains(100));
            assertTrue(fixture.screen.progress.contains(99));
            assertFalse(completion.isDone());
            fixture.tick(SETTINGS.finishTicks());
            assertEquals(UpgradeAnimationOutcome.SUCCESS, completion.join());
            assertTrue(fixture.screen.closed);
            assertTrue(fixture.timer.cancelled);
            assertFalse(fixture.service.active(fixture.player));
            fixture.tick(20);
            fixture.service.cancel(fixture.player);
            assertEquals(1, callbacks.get());
            assertEquals(1, fixture.resolutions);
        }
    }

    @Test
    void failureAtTheSelectedMilestoneNeverDisplays100AndCollapsesTheBar() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.resolution.complete(false);
            var completion = fixture.play();
            fixture.tick(42 + SETTINGS.finishTicks());
            assertEquals(UpgradeAnimationOutcome.FAILURE, completion.join());
            assertFalse(fixture.screen.progress.contains(100));
            assertTrue(fixture.screen.progress.getLast() >= 5 && fixture.screen.progress.getLast() <= 99);
            assertTrue(fixture.screen.text.contains(Component.text("Failure")));
            assertEquals("sprite/upgrade/bar_0", fixture.screen.bar.fontPrefix().value());
        }
    }

    @Test
    void cancelRetirementAndShutdownCannotRevealALateResolvedSuccess() throws Exception {
        try (var fixture = new Fixture()) {
            var first = fixture.play();
            fixture.service.cancel(fixture.player);
            fixture.tick(50);
            assertEquals(UpgradeAnimationOutcome.CANCELLED, first.join());
            assertEquals(1, fixture.resolutions);
            fixture.screen = new TestScreen();
            var second = fixture.play();
            fixture.waitForResolution();
            fixture.timer.retired.run();
            fixture.resolution.complete(true);
            fixture.tick(50);
            assertEquals(UpgradeAnimationOutcome.CANCELLED, second.join());
            assertFalse(fixture.screen.progress.contains(100));
            fixture.screen = new TestScreen();
            var third = fixture.play();
            fixture.services.unregisterOwnedServices();
            assertEquals(UpgradeAnimationOutcome.CANCELLED, third.join());
            assertTrue(fixture.screen.closed);
        }
    }

    @Test
    void offlineWorldChangesResolverErrorsAndTimeoutAlwaysReleaseTheScreen() throws Exception {
        try (var fixture = new Fixture()) {
            var completion = fixture.play();
            fixture.online = false;
            fixture.tick(1);
            assertEquals(UpgradeAnimationOutcome.CANCELLED, completion.join());
            fixture.online = true;
            fixture.screen = new TestScreen();
            completion = fixture.play();
            fixture.world = world();
            fixture.tick(1);
            assertEquals(UpgradeAnimationOutcome.CANCELLED, completion.join());
            fixture.screen = new TestScreen();
            completion = fixture.play();
            fixture.tick(100);
            assertEquals(UpgradeAnimationOutcome.ERROR, completion.join());
            assertTrue(fixture.screen.closed);
            fixture.screen = new TestScreen();
            fixture.resolution = CompletableFuture.failedFuture(new IllegalStateException("Persistence failed"));
            completion = fixture.play();
            fixture.tick(42);
            assertEquals(UpgradeAnimationOutcome.ERROR, completion.join());
            assertFalse(fixture.screen.progress.contains(100));
        }
    }

    @Test
    void concurrentRequestsAreRejectedWithoutReplacingTheCurrentRitual() throws Exception {
        try (var fixture = new Fixture()) {
            var first = fixture.play();
            assertTrue(fixture.play().isCompletedExceptionally());
            assertFalse(first.isDone());
            assertTrue(fixture.service.active(fixture.player));
            assertEquals(1, fixture.resolutions);
        }
    }

    @Test
    void irregularMilestonesSweepForwardWithoutOvershootWhileTheBrightAccentOnlyFadesOut() {
        var timeline = new UpgradeAnimationTimeline(220, 1234);
        int previous = 0;
        int previousFill = 0;
        int steps = 0;
        int animatedSteps = 0;
        var stepTicks = new ArrayList<Integer>();
        for (int tick = 0; tick <= 220; tick++) {
            var frame = timeline.frame(tick);
            assertTrue(frame.progress() >= previous && frame.progress() <= 99);
            assertTrue(frame.barFill() >= previousFill, "Charging must never retract between steps");
            assertTrue(frame.barFill() <= Math.round(frame.progress() * 1.8),
                "A sweep must never exceed its target percentage");
            assertTrue(frame.runes() <= 2);
            if (frame.step()) {
                steps++;
                stepTicks.add(tick);
                assertEquals(1, frame.flash(), "The accent appears immediately, without fading in");
                if (frame.barFill() < Math.round(frame.progress() * 1.8)) {
                    animatedSteps++;
                    assertTrue(timeline.frame(tick + 1).barFill() > frame.barFill(),
                        "An unfinished step must visibly grow on the next tick instead of popping into place");
                }
                assertTrue(timeline.frame(tick + 1).flash() < frame.flash(),
                    "The bright accent fades out while the sweep grows");
            }
            previous = frame.progress();
            previousFill = frame.barFill();
        }
        assertTrue(steps >= 7 && steps <= 9);
        assertTrue(animatedSteps > 0);
        assertEquals(99, previous);
        assertEquals(178, timeline.frame(240).barFill());
        assertEquals(0, timeline.frame(240).flash());
        var intervals = new ArrayList<Integer>();
        for (int index = 1; index < stepTicks.size(); index++) {
            intervals.add(stepTicks.get(index) - stepTicks.get(index - 1));
        }
        assertTrue(intervals.stream().distinct().count() > 1);
        assertTrue(intervals.stream().anyMatch(interval -> interval >= 60 && interval <= 80));
        assertThrows(IllegalArgumentException.class,
            () -> new UpgradeAnimationSettings(1, 2, 20, 40, Map.of()));
    }

    @Test
    void stepSweepsStartQuicklyAndDecelerateNearTheirTargetWithoutRetracting() {
        var timeline = new UpgradeAnimationTimeline(new UpgradeAnimationPacing(0, 2, 2, 80, 80, 0, 0), 1234);
        int finalTick = timeline.duration();
        int initialFill = timeline.frame(finalTick - 1).barFill();
        int firstThird = timeline.frame(finalTick, 3).barFill();
        int secondThird = timeline.frame(finalTick, 7).barFill();
        int target = timeline.frame(finalTick, 11).barFill();
        assertTrue(firstThird - initialFill > secondThird - firstThird,
            "The first part of a step must travel faster than the middle");
        assertTrue(secondThird - firstThird > target - secondThird,
            "A step must slow down as it approaches the target");
        assertEquals(178, target);
        assertEquals(target, timeline.frame(finalTick, 30).barFill());
        assertEquals(1, timeline.frame(finalTick, 0).flash());
        assertTrue(timeline.frame(finalTick, 3).flash() > timeline.frame(finalTick, 7).flash());
    }

    @Test
    void everySweepSettlesBeforeTheNextStepAndTheLastSweepFitsEvenAShortHold() {
        for (int seed = 0; seed < 200; seed++) {
            var timeline = new UpgradeAnimationTimeline(220, seed);
            int previousFill = 0;
            for (int tick = 0; tick <= 240; tick++) {
                var frame = timeline.frame(tick);
                assertTrue(frame.barFill() >= previousFill);
                assertTrue(frame.barFill() <= Math.round(frame.progress() * 1.8));
                if (frame.step()) {
                    assertEquals(Math.round(timeline.frame(Math.max(0, tick - 1)).progress() * 1.8), previousFill,
                        "The previous sweep must settle before another step starts");
                }
                previousFill = frame.barFill();
            }
            assertEquals(178, previousFill);
            assertTrue(timeline.frame(220, 0, 2).barFill() < 178);
            assertEquals(178, timeline.frame(220, 1, 2).barFill());
            assertEquals(178, timeline.frame(220, 0, 0).barFill());
        }
    }

    @Test
    void anEarlyFailureLetsItsVisibleSweepFinishBeforeCollapsingWithoutRerolling() throws Exception {
        long seed = LongStream.range(0, 200).filter(candidate -> {
            var timeline = new UpgradeAnimationTimeline(220, candidate);
            int tick = timeline.resolutionTick();
            return tick + 12 < timeline.duration()
                && timeline.frame(tick + 11).progress() == timeline.resolutionProgress()
                && timeline.frame(tick + 4).barFill() < Math.round(timeline.resolutionProgress() * 1.8);
        }).findFirst().orElseThrow();
        var timeline = new UpgradeAnimationTimeline(220, seed);
        try (var fixture = new Fixture(seed)) {
            fixture.settings = new UpgradeAnimationSettings(220, 24, 20, 40, Map.of());
            fixture.resolution.complete(false);
            var completion = fixture.play();
            fixture.tick(timeline.resolutionTick());
            int initialFill = fixture.screen.barFill();
            assertFalse(fixture.screen.text.contains(Component.text("Failure")));
            fixture.tick(1);
            assertTrue(fixture.screen.barFill() > initialFill);
            assertFalse(fixture.screen.text.contains(Component.text("Failure")));
            for (int age = 2; age < 12 && !fixture.screen.text.contains(Component.text("Failure")); age++) {
                fixture.tick(1);
            }
            assertEquals(Math.round(timeline.resolutionProgress() * 1.8), fixture.screen.barFill());
            assertTrue(fixture.screen.text.contains(Component.text("Failure")));
            fixture.tick(fixture.settings.finishTicks());
            assertEquals(UpgradeAnimationOutcome.FAILURE, completion.join());
            assertEquals(1, fixture.resolutions);
            assertFalse(fixture.screen.progress.contains(100));
        }
    }

    @Test
    void seededTimelinesVaryBothFailureMilestonesAndProgressStepsAndAlwaysAllowLateFailure() {
        var resolutionProgress = new HashSet<Integer>();
        var firstSteps = new HashSet<Integer>();
        for (int seed = 0; seed < 200; seed++) {
            var timeline = new UpgradeAnimationTimeline(220, seed);
            resolutionProgress.add(timeline.resolutionProgress());
            assertEquals(timeline.resolutionProgress(), timeline.frame(timeline.resolutionTick()).progress());
            for (int tick = 1; tick <= 220; tick++) {
                if (timeline.frame(tick).step()) {
                    firstSteps.add(timeline.frame(tick).progress());
                    break;
                }
            }
        }
        assertTrue(resolutionProgress.size() > 20);
        assertTrue(resolutionProgress.stream().anyMatch(progress -> progress < 40));
        assertTrue(resolutionProgress.contains(99));
        assertTrue(firstSteps.size() > 10);
        assertEquals(220, UpgradeAnimationSettings.defaults().chargeTicks());
    }

    @Test
    void skipStartsExactlyOneResolverAndWaitsForDurabilityWithoutShowingSuccess() throws Exception {
        for (boolean success : List.of(false, true)) {
            try (var fixture = new Fixture()) {
                var completion = fixture.play();
                fixture.service.skip(fixture.player);
                fixture.service.skip(fixture.player);
                fixture.tick(1);
                assertTrue(fixture.screen.closed);
                assertEquals(1, fixture.resolutions);
                assertFalse(completion.isDone());
                assertFalse(fixture.screen.progress.contains(100));
                fixture.service.skip(fixture.player);
                fixture.tick(3);
                fixture.resolution.complete(success);
                fixture.tick(1);
                assertEquals(success ? UpgradeAnimationOutcome.SUCCESS : UpgradeAnimationOutcome.FAILURE,
                    completion.join());
                assertEquals(1, fixture.resolutions);
                assertTrue(fixture.timer.cancelled);
                assertFalse(fixture.service.active(fixture.player));
            }
        }
    }

    @Test
    void skipReusesAnAlreadyPendingResolverAndAResolvedSuccessDuringTheCharge() throws Exception {
        try (var fixture = new Fixture()) {
            var completion = fixture.play();
            fixture.waitForResolution();
            fixture.service.skip(fixture.player);
            fixture.tick(1);
            assertFalse(completion.isDone());
            fixture.resolution.complete(true);
            fixture.tick(1);
            assertEquals(UpgradeAnimationOutcome.SUCCESS, completion.join());
            assertEquals(1, fixture.resolutions);
        }
        try (var fixture = new Fixture()) {
            fixture.resolution.complete(true);
            var completion = fixture.play();
            fixture.waitForResolution();
            fixture.service.skip(fixture.player);
            fixture.tick(1);
            assertEquals(UpgradeAnimationOutcome.SUCCESS, completion.join());
            assertEquals(1, fixture.resolutions);
        }
    }

    @Test
    void skipDuringTheFinishCompletesImmediatelyAndResolverFailureNeverBecomesSuccess() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.resolution.complete(true);
            var completion = fixture.play();
            fixture.tick(44);
            assertTrue(fixture.screen.progress.contains(100));
            assertFalse(completion.isDone());
            fixture.service.skip(fixture.player);
            fixture.tick(1);
            assertEquals(UpgradeAnimationOutcome.SUCCESS, completion.join());
            assertEquals(1, fixture.resolutions);
        }
        try (var fixture = new Fixture()) {
            var completion = fixture.play();
            fixture.service.skip(fixture.player);
            fixture.tick(1);
            fixture.resolution.completeExceptionally(new IllegalStateException("No durable result"));
            fixture.tick(1);
            assertEquals(UpgradeAnimationOutcome.ERROR, completion.join());
            assertFalse(fixture.screen.progress.contains(100));
        }
    }

    @Test
    void skipBeforeTheStartCallbackDoesNotOpenAScreenOrLoseTheResolver() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.deferStart = true;
            var completion = fixture.play();
            fixture.service.skip(fixture.player);
            fixture.pendingStart.run();
            fixture.resolution.complete(false);
            fixture.tick(1);
            assertEquals(UpgradeAnimationOutcome.FAILURE, completion.join());
            assertTrue(fixture.screen.progress.isEmpty());
            assertEquals(1, fixture.resolutions);
        }
    }

    @Test
    void aPendingAuthoritativeWriteFreezesProgressAtZeroAndInvokesTheResolverAtStart() throws Exception {
        try (var fixture = new Fixture()) {
            var completion = fixture.play();
            assertEquals(1, fixture.resolutions);
            fixture.tick(20);
            assertTrue(fixture.screen.progress.stream().allMatch(progress -> progress == 0));
            assertFalse(completion.isDone());
            fixture.resolution.complete(false);
            fixture.tick(SETTINGS.chargeTicks() + SETTINGS.holdTicks() + SETTINGS.finishTicks());
            assertEquals(UpgradeAnimationOutcome.FAILURE, completion.join());
            assertEquals(1, fixture.resolutions);
        }
    }

    @Test
    void guaranteedVisualBreakthroughOnlyShortcutsAnAlreadyCommittedSuccess() throws Exception {
        for (boolean success : List.of(false, true)) {
            try (var fixture = new Fixture()) {
                fixture.settings = new UpgradeAnimationSettings(40, 2, 20, 40, Map.of(), 1,
                    List.of(new UpgradeAnimationPacing(0, 2, 2, 60, 60, 0, 0)));
                fixture.successChance = success ? 1 : 0;
                fixture.resolution.complete(success);
                var completion = fixture.play();
                fixture.tick(100);
                assertEquals(success ? UpgradeAnimationOutcome.SUCCESS : UpgradeAnimationOutcome.FAILURE,
                    completion.join());
                assertEquals(success, fixture.screen.progress.contains(100));
                if (success) {
                    assertFalse(fixture.screen.progress.contains(99));
                }
                assertEquals(1, fixture.resolutions);
            }
        }
    }

    @Test
    void aZeroVisualBreakthroughChanceKeepsTheFinal99Hold() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.resolution.complete(true);
            var completion = fixture.play();
            fixture.tick(40);
            assertEquals(99, fixture.screen.progress.getLast());
            assertFalse(fixture.screen.progress.contains(100));
            fixture.tick(1);
            assertFalse(fixture.screen.progress.contains(100));
            fixture.tick(1);
            assertEquals(100, fixture.screen.progress.getLast());
            assertFalse(completion.isDone());
        }
    }

    @Test
    void aFailureAt99SharesThePendingLastRuneWithSuccessAndCanStillCollapseAfterTheHold() throws Exception {
        long seed = LongStream.range(0, 200)
            .filter(candidate -> new UpgradeAnimationTimeline(SETTINGS.chargeTicks(), candidate)
                .resolutionProgress() == 99)
            .findFirst().orElseThrow();
        for (boolean success : List.of(false, true)) {
            try (var fixture = new Fixture(seed)) {
                fixture.resolution.complete(success);
                var completion = fixture.play();
                fixture.tick(SETTINGS.chargeTicks());
                assertEquals(99, fixture.screen.progress.getLast());
                assertTrue(fixture.screen.text.contains(Component.text("Holding")));
                assertFalse(fixture.screen.text.contains(Component.text("Success")));
                assertFalse(fixture.screen.text.contains(Component.text("Failure")));
                assertFalse(completion.isDone());
                fixture.tick(SETTINGS.holdTicks() - 1);
                assertEquals(99, fixture.screen.progress.getLast());
                var pendingTint = fixture.screen.textureLayouts.get("rune_2").tint();
                assertFalse(pendingTint.equals(TextColor.color(0xC4B5FD)),
                    "The last rune must not look successfully applied while the result is hidden");
                assertFalse(pendingTint.equals(TextColor.color(0x3F354D)),
                    "The last rune must visibly attempt activation during the hold");
                fixture.tick(1);
                assertEquals(success ? 100 : 99, fixture.screen.progress.getLast());
                assertTrue(fixture.screen.text.contains(Component.text(success ? "Success" : "Failure")));
                assertEquals(success, fixture.screen.progress.contains(100));
                fixture.tick(SETTINGS.finishTicks());
                assertEquals(success ? UpgradeAnimationOutcome.SUCCESS : UpgradeAnimationOutcome.FAILURE,
                    completion.join());
                assertEquals(1, fixture.resolutions, "The final rune must not reroll the authoritative result");
                assertTrue(fixture.timer.cancelled);
            }
        }
    }

    @Test
    void visualBreakthroughChecksRunOncePerReachedStepIncludingTheLastAndNeverDuringPauses() {
        var timeline = new UpgradeAnimationTimeline(220, 37);
        int expected = 0;
        for (int tick = 0; tick <= 260; tick++) {
            if (tick <= 220 && timeline.frame(tick).step()) {
                expected++;
            }
            assertFalse(timeline.checkBreakthrough(tick, 0));
            assertFalse(timeline.checkBreakthrough(tick, 1));
            assertEquals(expected, timeline.breakthroughChecks());
        }
        assertEquals(timeline.steps(), timeline.breakthroughChecks());
        var guaranteed = new UpgradeAnimationTimeline(220, 37);
        for (int tick = 0; tick <= 220; tick++) {
            assertEquals(guaranteed.frame(tick).step(), guaranteed.checkBreakthrough(tick, 1));
        }
    }

    @Test
    void chancePacingIsValidatedRoundTripSafeAndUsesShorterHighChanceProfiles() throws Exception {
        var settings = UpgradeAnimationSettings.defaults();
        var mapper = new ObjectMapper();
        assertEquals(settings, mapper.readValue(mapper.writeValueAsString(settings), UpgradeAnimationSettings.class));
        assertEquals(0.15, settings.instantSuccessChance());
        assertEquals(0.9, settings.pacingFor(1).orElseThrow().minimumSuccessChance());
        assertEquals(0.6, settings.pacingFor(0.6).orElseThrow().minimumSuccessChance());
        assertEquals(0.3, settings.pacingFor(0.3).orElseThrow().minimumSuccessChance());
        assertEquals(0, settings.pacingFor(0).orElseThrow().minimumSuccessChance());
        for (int seed = 0; seed < 100; seed++) {
            for (double chance : List.of(0.95, 0.75, 0.4, 0.1)) {
                var profile = settings.pacingFor(chance).orElseThrow();
                var timeline = new UpgradeAnimationTimeline(profile, seed);
                assertTrue(timeline.steps() >= profile.minSteps() && timeline.steps() <= profile.maxSteps());
                assertTrue(timeline.duration() >= profile.minChargeTicks()
                    && timeline.duration() <= profile.maxChargeTicks());
                if (chance < 0.3) {
                    int previous = 0;
                    boolean longPause = false;
                    for (int tick = 1; tick <= timeline.duration(); tick++) {
                        if (timeline.frame(tick).step()) {
                            int gap = tick - previous;
                            longPause |= gap >= 60 && gap <= 80;
                            previous = tick;
                        }
                    }
                    assertTrue(longPause);
                }
            }
        }
        assertThrows(IllegalArgumentException.class,
            () -> new UpgradeAnimationSettings(40, 2, 20, 40, Map.of(), 1.01, List.of()));
        assertThrows(IllegalArgumentException.class,
            () -> new UpgradeAnimationSettings(40, 2, 20, 40, Map.of(), 0,
                List.of(new UpgradeAnimationPacing(0.9, 2, 3, 60, 80, 0, 0))));
        assertThrows(IllegalArgumentException.class,
            () -> new UpgradeAnimationPacing(0, 2, 3, 60, 80, 60, 80));
        assertThrows(IllegalArgumentException.class, () -> settings.pacingFor(Double.NaN));
    }

    @Test
    void everyTextAndSpriteSharesTheGlobalVerticalShiftAndTheBarUsesItsEmbeddedTrack() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.play();
            assertEquals(-144, fixture.screen.textureLayouts.get("frame").y());
            assertEquals(-79, fixture.screen.textureLayouts.get("core").y());
            assertEquals(0, fixture.screen.textureLayouts.get("rune_0").y());
            assertEquals(29, fixture.screen.textureLayouts.get("bar").y());
            assertEquals(-132, fixture.screen.textLayouts.get("title").y());
            assertEquals(-113, fixture.screen.textLayouts.get("subject").y());
            assertEquals(-96, fixture.screen.textLayouts.get("upgrade").y());
            assertEquals(43, fixture.screen.textLayouts.get("progress").y());
            assertEquals(62, fixture.screen.textLayouts.get("phase").y());
            assertEquals(80, fixture.screen.textLayouts.get("skip_hint").y());
        }
    }

    @Test
    void synchronousResolverCancellationCannotOpenALateScreenOrTimer() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.onResolve = () -> fixture.service.cancel(fixture.player);
            var completion = fixture.play();
            assertEquals(UpgradeAnimationOutcome.CANCELLED, completion.join());
            assertTrue(fixture.screen.progress.isEmpty());
            assertNull(fixture.timer);
            assertFalse(fixture.service.active(fixture.player));
            fixture.resolution.complete(true);
            assertTrue(fixture.screen.progress.isEmpty());
        }
    }

    @Test
    void cancellationDuringScreenAdmissionImmediatelyClosesTheReturnedHandle() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.onOpen = () -> fixture.service.cancel(fixture.player);
            var completion = fixture.play();
            assertEquals(UpgradeAnimationOutcome.CANCELLED, completion.join());
            assertTrue(fixture.screen.closed);
            assertTrue(fixture.screen.progress.isEmpty());
            assertNull(fixture.timer);
        }
    }

    private static World world() {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
            (object, method, arguments) -> null);
    }

    private static final class Fixture implements AutoCloseable {

        private final DefaultServiceRegistry registry = new DefaultServiceRegistry();
        private final ServiceOwner owner = () -> "UpgradeAnimationTest";
        private final VexServiceRegistry services = registry.scoped(owner);
        private final UUID id = UUID.randomUUID();
        private boolean online = true;
        private World world = world();
        private TestScreen screen = new TestScreen();
        private Timer timer;
        private int resolutions;
        private UpgradeAnimationSettings settings = SETTINGS;
        private double successChance = 0.5;
        private Runnable onResolve = () -> {};
        private Runnable onOpen = () -> {};
        private boolean deferStart;
        private Runnable pendingStart;
        private CompletableFuture<Boolean> resolution = new CompletableFuture<>();
        private final Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
            new Class<?>[]{Player.class}, (object, method, arguments) -> switch (method.getName()) {
                case "getUniqueId" -> id;
                case "isOnline" -> online;
                case "getWorld" -> world;
                case "getLocation" -> new Location(world, 10, 64, 20);
                case "playSound" -> null;
                default -> null;
            });
        private final UpgradeAnimationService service;

        private Fixture() throws Exception {
            this(null);
        }

        private Fixture(Long seed) throws Exception {
            var schedules = (ScheduleService) Proxy.newProxyInstance(ScheduleService.class.getClassLoader(),
                new Class<?>[]{ScheduleService.class}, (object, method, arguments) -> {
                    if (method.getName().equals("runFor")) {
                        if (deferStart) {
                            pendingStart = (Runnable) arguments[1];
                        } else {
                            ((Runnable) arguments[1]).run();
                        }
                        return Optional.of(new Timer(() -> {}, () -> {}));
                    }
                    assertEquals("runForTimer", method.getName());
                    assertEquals(1L, arguments[2]);
                    timer = new Timer((Runnable) arguments[3], (Runnable) arguments[4]);
                    return Optional.of(timer);
                });
            var screens = (ScreenUiService) Proxy.newProxyInstance(ScreenUiService.class.getClassLoader(),
                new Class<?>[]{ScreenUiService.class}, (object, method, arguments) -> {
                    assertEquals("open", method.getName());
                    onOpen.run();
                    return screen;
                });
            var install = DefaultServiceRegistry.class.getDeclaredMethod("register", ServiceOwner.class,
                Class.class, VexService.class);
            install.setAccessible(true);
            install.invoke(registry, owner, ScheduleService.class, schedules);
            install.invoke(registry, owner, ScreenUiService.class, screens);
            if (seed == null) {
                services.register(UpgradeAnimationService.class, VexUpgradeAnimationService.class);
                services.registerQueuedServices();
            } else {
                install.invoke(registry, owner, UpgradeAnimationService.class,
                    new VexUpgradeAnimationService(services, () -> seed));
            }
            service = services.require(UpgradeAnimationService.class);
        }

        private CompletableFuture<UpgradeAnimationOutcome> play() {
            var texts = new UpgradeAnimationTexts(Component.text("Title"), Component.text("Arkie"),
                Component.text("+4 to +5"), Component.text("%progress%"), Component.text("Charging"),
                Component.text("Holding"), Component.text("Success"), Component.text("Failure"), Component.text("Skip"));
            return service.play(player, new UpgradeAnimationRequest(texts, settings, () -> {
                resolutions++;
                onResolve.run();
                return resolution;
            }, successChance));
        }

        private void tick(int count) {
            for (int index = 0; index < count; index++) {
                timer.callback.run();
            }
        }

        private void waitForResolution() {
            for (int tick = 0; tick <= SETTINGS.chargeTicks() && resolutions == 0; tick++) {
                tick(1);
            }
            assertTrue(resolutions > 0);
        }

        @Override
        public void close() {
            services.unregisterOwnedServices();
        }
    }

    private static final class TestScreen implements ScreenUi {

        private final List<Integer> progress = new ArrayList<>();
        private final List<Component> text = new ArrayList<>();
        private UiTexture bar;
        private final Map<String, TextureLayout> textureLayouts = new HashMap<>();
        private final Map<String, TextBlockLayout> textLayouts = new HashMap<>();
        private boolean closed;

        @Override
        public void textBlock(String id, List<Component> lines, TextBlockLayout layout) {
            textLayouts.put(id, layout);
            setLines(id, lines);
        }

        @Override
        public void textureBlock(String id, UiTexture texture, TextureLayout layout) {
            textureLayouts.put(id, layout);
            if (id.equals("bar")) {
                bar = texture;
            }
        }

        @Override
        public void setLines(String id, List<Component> lines) {
            text.addAll(lines);
            if (id.equals("progress")) {
                progress.add(Integer.parseInt(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                    .plainText().serialize(lines.getFirst())));
            }
        }

        @Override
        public void remove(String id) {
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public void close() {
            closed = true;
        }

        private int barFill() {
            return Integer.parseInt(bar.fontPrefix().value().substring("sprite/upgrade/bar_".length()));
        }
    }

    private static final class Timer implements VexTask {

        private final Runnable callback;
        private final Runnable retired;
        private boolean cancelled;

        private Timer(Runnable callback, Runnable retired) {
            this.callback = callback;
            this.retired = retired;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public boolean isFinished() {
            return cancelled;
        }

        @Override
        public boolean isRepeating() {
            return true;
        }
    }
}
