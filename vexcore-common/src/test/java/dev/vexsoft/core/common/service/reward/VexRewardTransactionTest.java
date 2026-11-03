package dev.vexsoft.core.common.service.reward;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.vexsoft.core.api.player.DataContainerKey;
import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.player.TransactionalPlayerData;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.common.service.execution.ExecutionComponentCoordinatorService;
import dev.vexsoft.core.execution.PlayerExecutionContext;
import dev.vexsoft.core.reward.CompiledReward;
import dev.vexsoft.core.reward.CompiledRewards;
import dev.vexsoft.core.reward.RewardBehavior;
import dev.vexsoft.core.reward.RewardResult;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.NoArgsConstructor;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

public final class VexRewardTransactionTest {

    private static final DataContainerKey<Balance> BALANCE =
        DataContainerKey.of("balance", Balance.class, Balance::new);

    @Test
    void deliveryReceiptsWaitForBothTransactionAndDurabilityAndUnsubscribe() throws Exception {
        var player = new VexPlayer(UUID.randomUUID(), "Alex");
        var context = new PlayerExecutionContext(player, Map.of());
        var service = service();
        var selected = service.prepareActions(new CompiledRewards(List.of(
            new CompiledRewards.Entry("item", reward(ignored -> RewardResult.success())))), context);
        var deliveries = new AtomicInteger();
        var subscription = service.observeDeliveries((entries, ignored) -> deliveries.addAndGet(entries.size()));
        assertFalse(service.executeAtomically(context, () -> {
            service.grantPrepared(selected, context);
            assertEquals(0, deliveries.get());
            return false;
        }));
        assertEquals(0, deliveries.get());
        player.beginDurableEffects();
        service.executeAtomically(context, () -> service.grantPrepared(selected, context).isSuccessful());
        assertEquals(0, deliveries.get());
        player.finishDurableEffects(false);
        assertEquals(0, deliveries.get());
        player.beginDurableEffects();
        service.executeAtomically(context, () -> service.grantPrepared(selected, context).isSuccessful());
        player.finishDurableEffects(true);
        assertEquals(1, deliveries.get());
        subscription.close();
        service.grantPrepared(selected, context);
        assertEquals(1, deliveries.get());
    }

    @Test
    void failingObserversDoNotTurnSuccessfulGrantsIntoFailures() {
        var context = new PlayerExecutionContext(new VexPlayer(UUID.randomUUID(), "Alex"), Map.of());
        var service = service();
        service.observeDeliveries((entries, ignored) -> {
            throw new IllegalStateException("Disconnected UI");
        });
        var result = service.grantActions(new CompiledRewards(List.of(
            new CompiledRewards.Entry("item", reward(ignored -> RewardResult.success())))), context);
        org.junit.jupiter.api.Assertions.assertTrue(result.isSuccessful());
    }

    @Test
    public void directSnapshotsCopyOnlyOnceAndStillRestoreEveryWriteOnFailure() {
        var player = new VexPlayer(UUID.randomUUID(), "Alex");
        var key = DataContainerKey.of("direct", DirectData.class, DirectData::new);
        player.install(key, new DirectData());
        DirectData.copies.set(0);
        assertFalse(service().executeAtomically(new PlayerExecutionContext(player, Map.of()), () -> {
            player.update(key, data -> {
                data.amount = 12;
            });
            player.update(key, data -> {
                data.amount = 24;
            });
            return false;
        }));
        assertEquals(1, DirectData.copies.get());
        assertEquals(0, player.read(key, data -> data.amount).intValue());
    }

    public static final class DirectData implements TransactionalPlayerData<DirectData> {

        private static final AtomicInteger copies = new AtomicInteger();
        private int amount;

        @Override
        public DirectData copyForTransaction() {
            copies.incrementAndGet();
            var copy = new DirectData();
            copy.amount = amount;
            return copy;
        }
    }

    @Test
    public void failedSecondRewardRollsBackFirstRewardAndNotifications() {
        VexPlayer player = new VexPlayer(UUID.randomUUID(), "Alex");

        player.install(BALANCE, new Balance());
        AtomicInteger notifications = new AtomicInteger();
        VexRewardService service = service();
        PlayerExecutionContext context = new PlayerExecutionContext(player, Map.of());
        CompiledRewards rewards = new CompiledRewards(List.of(
            new CompiledRewards.Entry(
                "first",
                reward(ignored -> {
                    player.update(
                        BALANCE,
                        balance -> {
                            balance.amount += 10;
                        }
                    );
                    player.afterCommit(notifications::incrementAndGet);

                    return RewardResult.success();
                })
            ),
            new CompiledRewards.Entry("second", reward(ignored -> RewardResult.failed("Inventory full")))
        ));

        for (int retry = 0; retry < 3; retry++) {
            assertFalse(service.executeAtomically(
                context,
                () -> service.grantActions(rewards, context).isSuccessful()
            ));
            assertEquals(0, player.<Balance, Integer>read(BALANCE, balance -> balance.amount).intValue());
        }

        assertEquals(0, notifications.get());
    }

    @Test
    public void exceptionAfterMutationRestoresTheSavedSnapshot() {
        VexPlayer player = new VexPlayer(UUID.randomUUID(), "Alex");

        player.install(BALANCE, new Balance());
        VexRewardService service = service();

        assertThrows(
            IllegalStateException.class,
            () -> service.executeAtomically(
                new PlayerExecutionContext(player, Map.of()),
                () -> {
                    player.update(
                        BALANCE,
                        balance -> {
                            balance.amount = 100;
                        }
                    );
                    throw new IllegalStateException("Delivery failed after mutation");
                }
            )
        );
        assertEquals(0, player.<Balance, Integer>read(BALANCE, balance -> balance.amount).intValue());
    }

    private static CompiledReward reward(final java.util.function.Function<PlayerExecutionContext, RewardResult> grant) {
        return new CompiledReward() {
            @Override
            public RewardBehavior getBehavior() {
                return RewardBehavior.ACTION;
            }

            @Override
            public RewardResult grant(final PlayerExecutionContext context) {
                return grant.apply(context);
            }

            @Override
            public Component describe(final PlayerExecutionContext context) {
                return Component.empty();
            }
        };
    }

    private static VexRewardService service() {
        Object components = Proxy.newProxyInstance(
            ExecutionComponentCoordinatorService.class.getClassLoader(),
            new Class<?>[]{ExecutionComponentCoordinatorService.class},
            (proxy, method, arguments) -> {
                throw new UnsupportedOperationException(method.getName());
            }
        );
        VexServiceRegistry registry = (VexServiceRegistry) Proxy.newProxyInstance(
            VexServiceRegistry.class.getClassLoader(),
            new Class<?>[]{VexServiceRegistry.class},
            (proxy, method, arguments) -> {
                if (method.getName().equals("require") && arguments[0] == ExecutionComponentCoordinatorService.class) {
                    return components;
                }

                throw new UnsupportedOperationException(method.getName());
            }
        );

        return new VexRewardService(registry);
    }

    @NoArgsConstructor
    public static final class Balance {

        private int amount;

    }
}
