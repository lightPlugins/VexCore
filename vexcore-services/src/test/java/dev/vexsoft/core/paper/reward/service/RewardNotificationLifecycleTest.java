package dev.vexsoft.core.paper.reward.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.api.localization.Language;
import dev.vexsoft.core.api.localization.LanguageContainer;
import dev.vexsoft.core.api.localization.LanguageKey;
import dev.vexsoft.core.api.localization.LocalizedMessage;
import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.localization.LocalizationService;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexClassFactory;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.api.service.reward.RewardService;
import dev.vexsoft.core.common.execution.service.ExecutionComponentCoordinatorService;
import dev.vexsoft.core.common.execution.service.VexExecutionComponentCoordinatorService;
import dev.vexsoft.core.common.reward.service.VexRewardService;
import dev.vexsoft.core.common.service.registry.DefaultServiceRegistry;
import dev.vexsoft.core.execution.PlayerExecutionContext;
import dev.vexsoft.core.paper.scheduler.VexTask;
import dev.vexsoft.core.paper.screenui.ScreenUi;
import dev.vexsoft.core.paper.screenui.reward.RewardCard;
import dev.vexsoft.core.paper.screenui.reward.RewardCardLayout;
import dev.vexsoft.core.paper.service.reward.RewardNotificationService;
import dev.vexsoft.core.paper.service.reward.RewardToastService;
import dev.vexsoft.core.paper.service.scheduler.ScheduleService;
import dev.vexsoft.core.paper.service.screenui.ScreenUiService;
import dev.vexsoft.core.paper.vexui.VexUIService;
import dev.vexsoft.core.paper.vexui.action.VexUIClickSound;
import dev.vexsoft.core.reward.CompiledReward;
import dev.vexsoft.core.reward.PreparedRewards;
import dev.vexsoft.core.reward.RewardBehavior;
import dev.vexsoft.core.reward.RewardOptions;
import dev.vexsoft.core.reward.notification.RewardDeliveryFeedback;
import dev.vexsoft.core.reward.notification.RewardNotification;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.experimental.Delegate;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

/** Uses the production scoped registry, constructors and extension factory with stubbed platform boundaries. */
public final class RewardNotificationLifecycleTest {

    @Test
    void ownerBridgesShareOneCoreFeedAndShutdownUnsubscribesAndCancelsTasks() {
        var root = new DefaultServiceRegistry().scoped(() -> "core");
        root.register(ExecutionComponentCoordinatorService.class, VexExecutionComponentCoordinatorService.class);
        root.register(ScreenUiService.class, Screens.class);
        root.register(ScheduleService.class, Scheduler.class);
        root.register(LocalizationService.class, Localization.class);
        root.register(VexUIService.class, MenuUi.class);
        root.register(RewardToastService.class, VexRewardToastService.class);
        root.registerQueuedServices();
        var first = owner(root, "isles");
        var second = owner(root, "other-plugin");
        assertSame(first.require(RewardToastService.class), second.require(RewardToastService.class));
        VexClassFactory.create(Probe.class, first, "Reward presentation probe");

        UUID id = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        var world = proxy(World.class, (instance, method, args) -> switch (method.getName()) {
            case "getUID" -> worldId;
            case "getGameTime" -> 0L;
            default -> throw new AssertionError(method.getName());
        });
        var sounds = new ArrayList<Sound>();
        var viewer = proxy(Player.class, (instance, method, args) -> switch (method.getName()) {
            case "getUniqueId" -> id;
            case "getWorld" -> world;
            case "isOnline" -> true;
            case "locale" -> Locale.GERMANY;
            case "playSound" -> {
                sounds.add((Sound) args[0]);
                yield null;
            }
            default -> throw new AssertionError(method.getName());
        });
        var player = new VexPlayer(id, "Alex", type -> type == LanguageContainer.class ? 0 : -1);
        player.bindPlatformPlayer(viewer);
        player.installContainer(0, LanguageContainer.class, proxy(LanguageContainer.class,
            (instance, method, args) -> new Language(LanguageKey.DE_DE, Component.text("Deutsch"), true)));
        var selection = new PreparedRewards(List.of(new PreparedRewards.Entry("custom", RewardOptions.guaranteed(),
            new CompiledReward() {
                @Override
                public RewardBehavior getBehavior() {
                    return RewardBehavior.ACTION;
                }

                @Override
                public Component describe(PlayerExecutionContext context) {
                    return Component.text("Committed reward");
                }

                @Override
                public List<RewardNotification> notifications(PlayerExecutionContext context) {
                    return List.of(new RewardNotification("diamond", describe(context), "minecraft:diamond",
                        BigDecimal.ONE));
                }
            })));
        var context = new PlayerExecutionContext(player, Map.of());
        var rewards = first.require(RewardService.class);
        assertFalse(rewards.executeAtomically(context, () -> {
            rewards.notifyDelivered(selection, context);
            return false;
        }));
        var screens = (Screens) root.require(ScreenUiService.class);
        assertEquals(0, screens.opened);
        assertTrue(sounds.isEmpty(), "Rolled-back deliveries must stay silent");
        rewards.notifyDelivered(selection, context);
        second.require(RewardService.class).notifyDelivered(selection, context);
        rewards.notifyDelivered(selection, context);
        assertEquals(1, screens.opened);
        assertEquals(List.of(VexUIClickSound.pickUp().asSound(), VexUIClickSound.pickUp().asSound(),
            VexUIClickSound.pickUp().asSound()), sounds, "Each delivery plays even when all three merge into one card");
        assertEquals(BigDecimal.valueOf(3), screens.lastCard.reward().amount());
        var scheduler = (Scheduler) root.require(ScheduleService.class);
        assertEquals(1, scheduler.cancelled.size());
        first.unregisterOwnedServices();
        rewards.notifyDelivered(selection, context);
        assertEquals(3, screens.writes);
        assertEquals(3, sounds.size(), "Retired bridges must not play late delivery sounds");
        second.require(RewardService.class).notifyDelivered(selection, RewardDeliveryFeedback.EXTERNAL.applyTo(context));
        assertEquals(4, screens.writes, "External audio must retain the committed receipt card");
        assertEquals(3, sounds.size(), "An explicitly external pickup must not also play immediate audio");
        root.require(RewardToastService.class).clear(player);
        assertTrue(screens.closed.get());
        assertTrue(scheduler.cancelled.getFirst().get());
        second.unregisterOwnedServices();
        root.unregisterOwnedServices();
    }

    private static VexServiceRegistry owner(VexServiceRegistry root, String name) {
        var scope = root.scoped(() -> name);
        scope.register(RewardNotificationService.class, VexRewardNotificationService.class);
        scope.register(RewardService.class, VexRewardService.class);
        scope.registerQueuedServices();
        return scope;
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }

    @Dependencies(RewardNotificationService.class)
    public static final class Probe {

        public Probe(VexServiceRegistry services) {
            services.require(RewardNotificationService.class);
        }
    }

    @Dependencies
    public static final class Screens implements ScreenUiService {

        private int opened;
        private int writes;
        private RewardCard lastCard;
        private final AtomicBoolean closed = new AtomicBoolean();
        @Delegate
        private final ScreenUiService delegate = proxy(ScreenUiService.class, (instance, method, args) -> {
            if (!method.getName().equals("open")) {
                throw new AssertionError(method.getName());
            }
            opened++;
            return proxy(ScreenUi.class, (screen, call, values) -> switch (call.getName()) {
                case "isClosed" -> closed.get();
                case "rewardCard" -> {
                    assertTrue(values[1] instanceof RewardCard);
                    lastCard = (RewardCard) values[1];
                    assertTrue(values[3] instanceof RewardCardLayout);
                    writes++;
                    yield null;
                }
                case "close" -> {
                    closed.set(true);
                    yield null;
                }
                default -> throw new AssertionError(call.getName());
            });
        });

        public Screens(VexServiceRegistry services) {
        }
    }

    @Dependencies
    public static final class Scheduler implements ScheduleService {

        private final List<AtomicBoolean> cancelled = new ArrayList<>();
        @Delegate
        private final ScheduleService delegate = proxy(ScheduleService.class, (instance, method, args) -> {
            if (method.getName().equals("runFor")) {
                ((Runnable) args[1]).run();
                return Optional.empty();
            }
            if (!method.getName().equals("runForTimer")) {
                throw new AssertionError(method.getName());
            }
            var flag = new AtomicBoolean();
            cancelled.add(flag);
            return Optional.of(proxy(VexTask.class, (task, call, values) -> switch (call.getName()) {
                case "cancel" -> {
                    flag.set(true);
                    yield null;
                }
                case "isCancelled" -> flag.get();
                default -> false;
            }));
        });

        public Scheduler(VexServiceRegistry services) {
        }
    }

    @Dependencies
    public static final class Localization implements LocalizationService {

        public Localization(VexServiceRegistry services) {
        }

        @Override
        public LocalizedMessage resolve(LanguageKey language, String key, Map<String, String> replacements) {
            return LocalizedMessage.single(Component.text("Erhalten"));
        }

        @Override
        public void reload() {
        }
    }

    @Dependencies
    public static final class MenuUi implements VexUIService {

        @Delegate
        private final VexUIService delegate = proxy(VexUIService.class, (instance, method, args) ->
            method.getName().equals("resolveText") ? args[0] : Optional.empty());

        public MenuUi(VexServiceRegistry services) {
        }
    }
}
