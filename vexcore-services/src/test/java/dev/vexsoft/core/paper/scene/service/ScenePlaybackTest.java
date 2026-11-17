package dev.vexsoft.core.paper.scene.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexClassFactory;
import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.common.service.registry.DefaultServiceRegistry;
import dev.vexsoft.core.paper.packets.display.FakeDisplayHandle;
import dev.vexsoft.core.paper.packets.display.FakeDisplayKind;
import dev.vexsoft.core.paper.packets.display.text.FakeTextDisplayRequest;
import dev.vexsoft.core.paper.packets.service.display.TextDisplayPacketService;
import dev.vexsoft.core.paper.packets.service.player.CameraPacketService;
import dev.vexsoft.core.paper.scene.SceneCaption;
import dev.vexsoft.core.paper.scene.SceneCinematic;
import dev.vexsoft.core.paper.scene.SceneCinematic.Pose;
import dev.vexsoft.core.paper.scene.SceneCinematic.Shot;
import dev.vexsoft.core.paper.scene.SceneDefinition;
import dev.vexsoft.core.paper.scene.ScenePlayback;
import dev.vexsoft.core.paper.scheduler.VexTask;
import dev.vexsoft.core.paper.screenui.ScreenUi;
import dev.vexsoft.core.paper.screenui.layout.ScreenAnchor;
import dev.vexsoft.core.paper.screenui.layout.TextBlockLayout;
import dev.vexsoft.core.paper.service.actionbar.ActionBarService;
import dev.vexsoft.core.paper.service.scene.SceneService;
import dev.vexsoft.core.paper.service.scheduler.ScheduleService;
import dev.vexsoft.core.paper.service.screenui.ScreenUiService;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

final class ScenePlaybackTest {

    @Test
    void revealKeepsTheNarrationAndLaterCaptionsOnTheSameClock() {
        var fixture = new Fixture();
        var playback = fixture.play();
        fixture.at(250);
        assertEquals("ab", fixture.caption("caption-0"));
        assertEquals("ab", fixture.caption("shadow-0"));
        var captionLayout = fixture.layouts.get("caption-0");
        var shadowLayout = fixture.layouts.get("shadow-0");
        assertEquals(captionLayout.x() + 1, shadowLayout.x());
        assertEquals(captionLayout.y() + 1, shadowLayout.y());
        assertTrue(shadowLayout.layer() < captionLayout.layer());
        assertTrue(fixture.coordinator.reveal(fixture.player));
        assertEquals("abcd", fixture.caption("caption-0"));
        assertEquals(0, fixture.stops);
        assertEquals(1, fixture.soundStarts);

        fixture.at(1_250);
        assertEquals("wx", fixture.caption("caption-0"));
        fixture.at(2_000);
        assertEquals(ScenePlayback.Result.COMPLETED, playback.completion().toCompletableFuture().join());
        assertTrue(fixture.closed);
        assertFalse(fixture.coordinator.reveal(fixture.player));
    }

    @Test
    void oneOwnerClosingReleasesPlaybackAndStopsOnlyItsNarration() {
        var fixture = new Fixture();
        var playback = fixture.play();
        assertTrue(fixture.coordinator.play(() -> "other", fixture.screens, fixture.player, fixture.scene, null).isEmpty());
        fixture.coordinator.closeOwner(fixture.owner);
        assertEquals(ScenePlayback.Result.INTERRUPTED, playback.completion().toCompletableFuture().join());
        assertEquals(1, fixture.stops);
        assertTrue(fixture.closed);
    }

    @Test
    void cinematicUsesOneClientGlidePerShotAndCatchesUpLinearlyAfterLateCuts() {
        var fixture = new Fixture();
        var cinematic = new SceneCinematic(List.of(
            new Shot(0, new Pose(0, 80, 0, 170, 0), new Pose(10, 84, 0, -170, 10)),
            new Shot(1_000, new Pose(20, 85, 4, 45, 0), new Pose(30, 87, 4, 45, 0))
        ), 4_600L);
        var scene = new SceneDefinition(fixture.scene.durationMillis(), fixture.scene.narration(),
            fixture.scene.layout(), fixture.scene.captions(), cinematic);
        var camera = new SceneCameraPlayback(fixture.player, cinematic, scene.durationMillis(),
            fixture.displays, fixture.cameras, fixture.actionBars);
        var playback = fixture.coordinator.play(fixture.owner, fixture.screens, fixture.player, scene, camera).orElseThrow();
        assertEquals(List.of("spawn", "cut", "attach", "glide", "playSound"), fixture.cameraEvents);
        assertEquals(List.of(true), fixture.suppressions);
        assertEquals(List.of(4_600L), fixture.playerTimes);
        assertEquals(10, fixture.glideDestination.getX(), 0.0001);
        assertEquals(-170, fixture.glideDestination.getYaw(), 0.0001);

        fixture.at(400);
        fixture.at(450);
        fixture.at(800);
        assertEquals(List.of(20), fixture.glideTicks);
        fixture.at(1_200);
        assertEquals(22, fixture.cameraPosition.getX(), 0.0001);
        assertEquals(30, fixture.glideDestination.getX(), 0.0001);
        assertEquals(List.of(20, 16), fixture.glideTicks);
        assertEquals(List.of("spawn", "cut", "attach", "glide", "playSound", "cut", "glide"), fixture.cameraEvents);
        assertEquals(0, fixture.soundStarts);

        fixture.at(2_000);
        assertEquals(ScenePlayback.Result.COMPLETED, playback.completion().toCompletableFuture().join());
        assertEquals(List.of("spawn", "cut", "attach", "glide", "playSound", "cut", "glide", "reset", "remove"),
            fixture.cameraEvents);
        assertEquals(List.of(true, false), fixture.suppressions);
        assertEquals(List.of(4_600L), fixture.playerTimes);
    }

    @Test
    void longTravelKeepsTheNativeGlidesBelowTheClientsSnapDistance() {
        var fixture = new Fixture();
        var cinematic = new SceneCinematic(List.of(
            new Shot(0, new Pose(0, 80, 0, 0, 0), new Pose(120, 80, 0, 0, 0))
        ), null);
        var camera = new SceneCameraPlayback(fixture.player, cinematic, 10_000,
            fixture.displays, fixture.cameras, fixture.actionBars);
        camera.start();
        assertEquals(60, fixture.glideDestination.getX(), 0.0001);
        camera.advance(4_999);
        assertEquals(List.of(100), fixture.glideTicks);
        camera.advance(5_000);
        assertEquals(120, fixture.glideDestination.getX(), 0.0001);
        assertEquals(List.of(100, 100), fixture.glideTicks);
        assertEquals(List.of("spawn", "cut", "attach", "glide", "glide"), fixture.cameraEvents);
        camera.close();
    }

    @Test
    void theActualRegistryResolvesSceneInterfacesAndConstructsTheListener() throws ReflectiveOperationException {
        var fixture = new Fixture();
        var registry = new DefaultServiceRegistry();
        var core = registry.scoped(() -> "VexCore");
        var register = DefaultServiceRegistry.class.getDeclaredMethod("register", ServiceOwner.class, Class.class,
            VexService.class);
        register.setAccessible(true);
        register.invoke(registry, core.getOwner(), ScheduleService.class, fixture.schedules);
        register.invoke(registry, core.getOwner(), ScreenUiService.class, fixture.screens);
        register.invoke(registry, core.getOwner(), TextDisplayPacketService.class, fixture.displays);
        register.invoke(registry, core.getOwner(), CameraPacketService.class, fixture.cameras);
        register.invoke(registry, core.getOwner(), ActionBarService.class, fixture.actionBars);
        core.register(SceneCoordinatorService.class, VexSceneCoordinatorService.class);
        core.registerQueuedServices();

        var scoped = core.scoped(fixture.owner);
        scoped.register(SceneService.class, VexSceneService.class);
        scoped.registerQueuedServices();
        VexClassFactory.create(VexSceneListener.class, core, "Listener");
        var playback = scoped.require(SceneService.class).play(fixture.player, fixture.scene).orElseThrow();
        scoped.unregisterOwnedServices();
        assertEquals(ScenePlayback.Result.INTERRUPTED, playback.completion().toCompletableFuture().join());
        core.unregisterOwnedServices();
    }

    private static final class Fixture {

        private final UUID id = UUID.randomUUID();
        private final ServiceOwner owner = () -> "test";
        private final Map<String, Component> text = new HashMap<>();
        private final Map<String, TextBlockLayout> layouts = new HashMap<>();
        private final List<String> cameraEvents = new ArrayList<>();
        private final List<Integer> glideTicks = new ArrayList<>();
        private final List<Boolean> suppressions = new ArrayList<>();
        private final List<Long> playerTimes = new ArrayList<>();
        private final World world = proxy(World.class, (self, method, args) -> null);
        private final FakeDisplayHandle target = new FakeDisplayHandle(owner, id, 12, UUID.randomUUID(),
            FakeDisplayKind.TEXT);
        private Location cameraPosition;
        private Location glideDestination;
        private final SceneDefinition scene = new SceneDefinition(2_000,
            Sound.sound(Key.key("test:intro"), Sound.Source.MASTER, 1.0F, 1.0F),
            TextBlockLayout.builder().anchor(ScreenAnchor.BOTTOM_CENTER).y(-105).build(),
            List.of(new SceneCaption(0, 500, List.of(Component.text("abcd"))),
                new SceneCaption(1_000, 500, List.of(Component.text("wxyz")))));
        private Runnable tick;
        private long time;
        private int soundStarts;
        private int stops;
        private boolean closed;
        private final Player player = proxy(Player.class, (self, method, args) -> {
            return switch (method.getName()) {
                case "getUniqueId" -> id;
                case "isOnline" -> true;
                case "isDead" -> false;
                case "getWorld" -> world;
                case "sendActionBar" -> null;
                case "setPlayerTime" -> {
                    assertEquals(false, args[1]);
                    playerTimes.add((Long) args[0]);
                    yield null;
                }
                case "playSound" -> {
                    assertEquals(scene.narration(), args[0]);
                    soundStarts++;
                    yield null;
                }
                case "stopSound" -> {
                    assertEquals(scene.narration(), args[0]);
                    stops++;
                    yield null;
                }
                default -> throw new AssertionError(method.getName());
            };
        });
        private final TextDisplayPacketService displays = proxy(TextDisplayPacketService.class, (self, method, args) ->
            switch (method.getName()) {
                case "spawn" -> {
                    cameraEvents.add("spawn");
                    var request = (FakeTextDisplayRequest) args[1];
                    assertEquals(0, request.getTeleportDuration());
                    cameraPosition = request.getLocation();
                    yield target;
                }
                case "remove" -> {
                    cameraEvents.add("remove");
                    yield null;
                }
                default -> throw new AssertionError(method.getName());
            });
        private final CameraPacketService cameras = proxy(CameraPacketService.class, (self, method, args) -> {
            cameraEvents.add(method.getName());
            if (method.getName().equals("cut")) {
                assertEquals(target, args[1]);
                cameraPosition = (Location) args[2];
            }
            if (method.getName().equals("glide")) {
                assertEquals(target, args[1]);
                glideDestination = (Location) args[2];
                glideTicks.add((Integer) args[3]);
            }
            return null;
        });
        private final ActionBarService actionBars = proxy(ActionBarService.class, (self, method, args) -> {
            suppressions.add((Boolean) args[2]);
            return null;
        });
        private final ScreenUi screen = proxy(ScreenUi.class, (self, method, args) -> {
            return switch (method.getName()) {
                case "textBlock", "setLines" -> {
                    text.put((String) args[0], (Component) ((List<?>) args[1]).getFirst());
                    if (args.length == 3) {
                        layouts.put((String) args[0], (TextBlockLayout) args[2]);
                    }
                    yield null;
                }
                case "remove" -> text.remove(args[0]);
                case "isClosed" -> closed;
                case "close" -> {
                    closed = true;
                    yield null;
                }
                default -> throw new AssertionError(method.getName());
            };
        });
        private final ScreenUiService screens = proxy(ScreenUiService.class, (self, method, args) -> screen);
        private final ScheduleService schedules = proxy(ScheduleService.class, (self, method, args) -> {
            var task = proxy(VexTask.class, (taskSelf, operation, values) -> null);
            switch (method.getName()) {
                case "runForTimer" -> tick = (Runnable) args[3];
                case "runFor" -> ((Runnable) args[1]).run();
                default -> throw new AssertionError(method.getName());
            }
            return Optional.of(task);
        });
        private final VexSceneCoordinatorService coordinator = new VexSceneCoordinatorService(schedules, () -> time);

        private ScenePlayback play() {
            return coordinator.play(owner, screens, player, scene, null).orElseThrow();
        }

        private String caption(final String key) {
            return PlainTextComponentSerializer.plainText().serialize(text.get(key));
        }

        private void at(final long millis) {
            time = TimeUnit.MILLISECONDS.toNanos(millis);
            tick.run();
        }
    }

    private static <T> T proxy(final Class<T> type, final InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
