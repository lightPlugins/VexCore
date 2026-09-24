package dev.vexsoft.core.common.service.level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vexsoft.core.api.configuration.ConfigurationSection;
import dev.vexsoft.core.api.localization.Language;
import dev.vexsoft.core.api.localization.LanguageContainer;
import dev.vexsoft.core.api.localization.LanguageKey;
import dev.vexsoft.core.api.localization.LocalizedMessage;
import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.cost.CostService;
import dev.vexsoft.core.api.service.expression.ExpressionService;
import dev.vexsoft.core.api.service.level.LevelClaimService;
import dev.vexsoft.core.api.service.level.LevelInstanceService;
import dev.vexsoft.core.api.service.level.LevelService;
import dev.vexsoft.core.api.service.localization.LocalizationService;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.api.service.requirement.RequirementRegistry;
import dev.vexsoft.core.api.service.requirement.RequirementService;
import dev.vexsoft.core.api.service.reward.PlayerRewardTransactionService;
import dev.vexsoft.core.api.service.reward.RewardRegistry;
import dev.vexsoft.core.api.service.reward.RewardService;
import dev.vexsoft.core.common.configuration.ConfigurateConfigurationSection;
import dev.vexsoft.core.common.requirement.level.LevelRequirement;
import dev.vexsoft.core.common.service.cost.VexCostService;
import dev.vexsoft.core.common.service.execution.ExecutionComponentCoordinatorService;
import dev.vexsoft.core.common.service.execution.VexExecutionComponentCoordinatorService;
import dev.vexsoft.core.common.service.expression.VexExpressionService;
import dev.vexsoft.core.common.service.requirement.VexRequirementRegistry;
import dev.vexsoft.core.common.service.requirement.VexRequirementService;
import dev.vexsoft.core.common.service.reward.VexRewardRegistry;
import dev.vexsoft.core.common.service.reward.VexRewardService;
import dev.vexsoft.core.cost.CompiledCosts;
import dev.vexsoft.core.execution.PlayerExecutionContext;
import dev.vexsoft.core.level.ClaimedLevelOverflowPolicy;
import dev.vexsoft.core.level.CompiledLevelDefinition;
import dev.vexsoft.core.level.CompiledLevelRule;
import dev.vexsoft.core.level.LevelClaimMode;
import dev.vexsoft.core.level.LevelExperienceGain;
import dev.vexsoft.core.level.LevelInstance;
import dev.vexsoft.core.level.LevelInterval;
import dev.vexsoft.core.level.LevelPlayerData;
import dev.vexsoft.core.level.LevelRequirementDefinition;
import dev.vexsoft.core.requirement.CompiledRequirements;
import dev.vexsoft.core.reward.CompiledReward;
import dev.vexsoft.core.reward.CompiledRewards;
import dev.vexsoft.core.reward.RewardBehavior;
import dev.vexsoft.core.reward.RewardContribution;
import dev.vexsoft.core.reward.RewardResult;
import dev.vexsoft.core.stats.StatKey;
import dev.vexsoft.core.stats.StatModifier;
import dev.vexsoft.core.stats.contribution.StatRewardContribution;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

/** Exercises real level compilation, reward transactions and manual claims together. */
public final class VexLevelInstanceServiceTest {

    @Test
    void experienceNotificationsIncludeTheLevelChangeAndCanBeCancelled() {
        Fixture fixture = new Fixture();
        fixture.install(reward(context -> RewardResult.success()), 2, 1);
        List<LevelExperienceGain> gains = new ArrayList<>();

        try (var subscription = fixture.levels.subscribeExperience(gains::add)) {
            fixture.levels.addExperience(fixture.player, "player", 250);

            assertEquals(1, gains.size());
            LevelExperienceGain gain = gains.getFirst();
            assertEquals(fixture.player.getUniqueId(), gain.playerId());
            assertEquals("player", gain.instanceId());
            assertEquals(250, gain.amount());
            assertEquals(1, gain.change().previous().level());
            assertEquals(3, gain.change().current().level());
            assertEquals(List.of(2, 3), gain.change().gainedLevels());
            assertTrue(gain.change().lostLevels().isEmpty());
        }

        fixture.levels.addExperience(fixture.player, "player", 25);
        assertEquals(1, gains.size());
    }

    @Test
    void experienceNotificationsWaitForSuccessfulRewardCommit() {
        Fixture fixture = new Fixture();
        List<LevelExperienceGain> gains = new ArrayList<>();
        fixture.install(
            reward(context -> {
                fixture.levels.addExperience(context.player(), "mining", 150);
                assertTrue(gains.isEmpty());
                return RewardResult.success();
            }), 2, 1
        );
        fixture.levels.addExperience(fixture.player, "player", 100);

        try (var subscription = fixture.levels.subscribeExperience(gains::add)) {
            assertEquals(1, fixture.levels.claim(fixture.player, "player", false).getClaimedCount());
        }

        assertEquals(1, gains.size());
        assertEquals("mining", gains.getFirst().instanceId());
        assertEquals(2, gains.getFirst().change().current().level());
        assertEquals(150, fixture.levels.snapshot(fixture.player, "mining").progress().experience());
    }

    @Test
    void failingExperienceListenerDoesNotStopOtherListenersOrXpGrant() {
        Fixture fixture = new Fixture();
        fixture.install(reward(context -> RewardResult.success()), 2, 1);
        List<LevelExperienceGain> gains = new ArrayList<>();

        try (
            var failing = fixture.levels.subscribeExperience(gain -> {
                throw new IllegalStateException("listener failed");
            });
            var collecting = fixture.levels.subscribeExperience(gains::add)
        ) {
            fixture.levels.addExperience(fixture.player, "player", 100);
        }

        assertEquals(1, gains.size());
        assertEquals(100, fixture.levels.snapshot(fixture.player, "player").progress().experience());
    }

    @Test
    void reachedLevelsAndRequirementsDoNotWaitForRewardClaims() {
        Fixture fixture = new Fixture();
        fixture.install(reward(context -> RewardResult.success()), 2, 1);
        fixture.levels.addExperience(fixture.player, "player", 250);

        var state = fixture.levels.snapshot(fixture.player, "player");
        assertEquals(3, state.progress().level());
        assertEquals(1, state.claimedLevel());
        assertEquals(2, state.claimableCount());
        assertEquals(0.5, state.progress().progress());
        assertEquals(1, fixture.levels.snapshot(fixture.player, "mining").progress().level());
        var requirement = new LevelRequirement(fixture.registry).compile(Map.of("system", "player", "level", 3));
        assertTrue(requirement.test(fixture.context()).satisfied());
        assertEquals(250, fixture.levels.snapshot(fixture.player, "player").progress().experience());
        assertFalse(fixture.levels.meets(fixture.player, new LevelRequirementDefinition("player", 4)));
    }

    @Test
    void skipsEmptyLevelsAndClaimsEachRewardExactlyOnce() {
        Fixture fixture = new Fixture();
        fixture.install(
            reward(context -> {
                fixture.levels.addExperience(context.player(), "mining", 10);
                return RewardResult.success();
            }), 3, 2
        );
        fixture.levels.addExperience(fixture.player, "player", 400);

        assertEquals(2, fixture.levels.snapshot(fixture.player, "player").claimableCount());
        assertEquals(1, fixture.levels.claim(fixture.player, "player", false).getClaimedCount());
        assertEquals(3, fixture.levels.snapshot(fixture.player, "player").claimedLevel());
        assertEquals(1, fixture.levels.claim(fixture.player, "player", true).getClaimedCount());
        assertEquals(0, fixture.levels.claim(fixture.player, "player", true).getClaimedCount());
        assertEquals(20, fixture.levels.snapshot(fixture.player, "mining").progress().experience());
        assertEquals(5, fixture.levels.snapshot(fixture.player, "player").progress().level());
    }

    @Test
    void failedRewardRollsBackEarlierXpAndClaimCursorOnEveryRetry() {
        Fixture fixture = new Fixture();
        CompiledReward first = reward(context -> {
            fixture.levels.addExperience(context.player(), "mining", 20);
            return RewardResult.success();
        });
        CompiledReward failing = reward(context -> RewardResult.failed("full"));
        fixture.levels.replace(List.of(
            instance("player", List.of(first, failing), 3, 1),
            instance("mining", List.of(), 2, 1)
        ));
        fixture.levels.addExperience(fixture.player, "player", 400);

        List<LevelExperienceGain> gains = new ArrayList<>();
        try (var subscription = fixture.levels.subscribeExperience(gains::add)) {
            for (int attempt = 0; attempt < 3; attempt++) {
                assertEquals(0, fixture.levels.claim(fixture.player, "player", true).getClaimedCount());
                assertEquals(0, fixture.levels.snapshot(fixture.player, "mining").progress().experience());
                assertEquals(1, fixture.levels.snapshot(fixture.player, "player").claimedLevel());
                assertEquals(3, fixture.levels.snapshot(fixture.player, "player").nextRewardLevel());
            }
        }
        assertTrue(gains.isEmpty());
    }

    @Test
    void simultaneousClaimsCannotDuplicateRewards() throws Exception {
        Fixture fixture = new Fixture();
        fixture.install(
            reward(context -> {
                fixture.levels.addExperience(context.player(), "mining", 1);
                return RewardResult.success();
            }), 2, 1
        );
        fixture.levels.addExperience(fixture.player, "player", 400);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> fixture.levels.claim(fixture.player, "player", true));
            var second = executor.submit(() -> fixture.levels.claim(fixture.player, "player", true));
            assertEquals(4, first.get().getClaimedCount() + second.get().getClaimedCount());
        }
        assertEquals(4, fixture.levels.snapshot(fixture.player, "mining").progress().experience());
    }

    @Test
    void batchDoesNotRecursivelyClaimLevelsUnlockedByItsOwnXpRewards() {
        Fixture fixture = new Fixture();
        fixture.install(
            reward(context -> {
                fixture.levels.addExperience(context.player(), "player", 100);
                return RewardResult.success();
            }), 2, 1
        );
        fixture.levels.addExperience(fixture.player, "player", 100);
        assertEquals(1, fixture.levels.claim(fixture.player, "player", true).getClaimedCount());
        assertEquals(3, fixture.levels.snapshot(fixture.player, "player").progress().level());
        assertEquals(1, fixture.levels.snapshot(fixture.player, "player").claimableCount());
    }

    @Test
    void serializationAndCatalogRemovalPreserveProgressAndClaims() throws Exception {
        Fixture fixture = new Fixture();
        fixture.install(reward(context -> RewardResult.success()), 2, 1);
        fixture.levels.addExperience(fixture.player, "player", 1000);
        fixture.levels.claim(fixture.player, "player", false);
        ObjectMapper mapper = new ObjectMapper();
        byte[] saved = fixture.player.read(
            LevelPlayerData.KEY, data -> {
                try {
                    return mapper.writeValueAsBytes(data);
                } catch (Exception failure) {
                    throw new IllegalStateException(failure);
                }
            }
        );
        var catalog = List.copyOf(fixture.levels.getInstances().values());
        fixture.levels.replace(List.of());
        assertFalse(fixture.levels.meets(fixture.player, new LevelRequirementDefinition("player", 1)));
        fixture.player.install(LevelPlayerData.KEY, mapper.readValue(saved, LevelPlayerData.class));
        fixture.levels.replace(catalog);

        var state = fixture.levels.snapshot(fixture.player, "player");
        assertEquals(1000, state.progress().experience());
        assertTrue(state.progress().maximumLevel());
        assertEquals(2, state.claimedLevel());
        assertEquals(3, state.claimableCount());
    }

    @Test
    void validatesForwardReferencesAndKeepsCatalogWhenReloadFails() {
        Fixture fixture = new Fixture();
        fixture.levels.registerExecutionTypes();
        ConfigurationSection player =
            configuration("player", Map.of("level-xp", Map.of("system", "mining", "amount", 5)));
        fixture.levels.load(List.of(player, configuration("mining", Map.of())));
        fixture.levels.addExperience(fixture.player, "player", 100);
        fixture.levels.claim(fixture.player, "player", false);
        assertEquals(5, fixture.levels.snapshot(fixture.player, "mining").progress().experience());

        ConfigurationSection invalid =
            configuration("invalid", Map.of("level-xp", Map.of("system", "absent", "amount", 5)));
        assertThrows(IllegalArgumentException.class, () -> fixture.levels.load(List.of(invalid)));
        assertEquals(2, fixture.levels.getInstances().size());
        assertThrows(
            IllegalArgumentException.class, () -> new LevelRequirement(fixture.registry)
                .compile(Map.of("system", "player", "level", 6))
        );
    }

    @Test
    void rejectsInvalidXpWithoutMutatingProgress() {
        Fixture fixture = new Fixture();
        fixture.install(reward(context -> RewardResult.success()), 2, 1);
        for (double invalid : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(
                IllegalArgumentException.class,
                () -> fixture.levels.addExperience(fixture.player, "player", invalid)
            );
        }
        assertEquals(0, fixture.levels.snapshot(fixture.player, "player").progress().experience());
    }

    @Test
    void targetLoreProgressUsesOnlyTheDisplayedLevelInterval() {
        var curve = instance("player", List.of(), 2, 1).definition().curve();
        assertEquals(new LevelInterval(50, 100, 50, 0.5), LevelInterval.calculate(curve, 250, 4));
        assertEquals(new LevelInterval(0, 100, 100, 0), LevelInterval.calculate(curve, 250, 5));
        assertEquals(new LevelInterval(100, 100, 0, 1), LevelInterval.calculate(curve, 250, 2));
    }

    @Test
    void statRewardsBecomeContributionsOnlyAfterClaiming() {
        Fixture fixture = new Fixture();
        StatKey stat = StatKey.parse("test:damage");
        fixture.install(
            new CompiledReward() {
                @Override
                public RewardBehavior getBehavior() {
                    return RewardBehavior.CONTRIBUTION;
                }

                @Override
                public RewardContribution contribute(final PlayerExecutionContext context) {
                    return new StatRewardContribution(Map.of(stat, StatModifier.flat(5)));
                }

                @Override
                public Component describe(final PlayerExecutionContext context) {
                    return Component.empty();
                }
            }, 2, 1
        );
        fixture.levels.addExperience(fixture.player, "player", 200);
        assertTrue(fixture.levels.calculateContributions(fixture.player).values().isEmpty());
        fixture.levels.claim(fixture.player, "player", false);
        assertEquals(
            5, fixture.levels.calculateContributions(fixture.player)
                .find("stats", StatRewardContribution.class).orElseThrow().modifiers().get(stat).amount()
        );
        fixture.levels.claim(fixture.player, "player", true);
        assertEquals(
            10, fixture.levels.calculateContributions(fixture.player)
                .find("stats", StatRewardContribution.class).orElseThrow().modifiers().get(stat).amount()
        );
    }

    private static ConfigurationSection configuration(final String id, final Map<String, Object> rewards) {
        return ConfigurateConfigurationSection.from(Map.of(
            "id", id, "name-key", "levels.instances." + id + ".name",
            "leveling", Map.of("min-level", 1, "max-level", 5, "experience", Map.of("required", "100")),
            "levels", List.of(Map.of("min-level", 2, "rewards", rewards))
        ));
    }

    private static LevelInstance instance(
        final String id,
        final List<CompiledReward> rewards,
        final int start,
        final int step
    ) {
        List<CompiledRewards.Entry> entries = java.util.stream.IntStream.range(0, rewards.size())
            .mapToObj(index -> new CompiledRewards.Entry(
                rewards.get(index).getBehavior() == RewardBehavior.CONTRIBUTION
                    ? "stats" : "reward-" + index, rewards.get(index)
            )).toList();
        return new LevelInstance(
            id, "levels.instances." + id + ".name", new CompiledLevelDefinition(
            new VexCompiledLevelCurve(1, 5, new double[]{0, 100, 200, 300, 400}), LevelClaimMode.MANUAL,
            ClaimedLevelOverflowPolicy.KEEP, List.of(new CompiledLevelRule(
            start, step,
            new CompiledRequirements(List.of()), new CompiledCosts(List.of()), new CompiledRewards(entries)
        ))
        )
        );
    }

    private static CompiledReward reward(final Function<PlayerExecutionContext, RewardResult> grant) {
        return new CompiledReward() {
            @Override
            public RewardBehavior getBehavior() {
                return RewardBehavior.ACTION;
            }

            @Override
            public boolean supportsPlayerRollback() {
                return true;
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

    private static final class Fixture {

        private final Map<Class<?>, Object> services = new HashMap<>();
        private final VexServiceRegistry registry = (VexServiceRegistry) Proxy.newProxyInstance(
            VexServiceRegistry.class.getClassLoader(), new Class<?>[]{VexServiceRegistry.class},
            (proxy, method, arguments) -> switch (method.getName()) {
                case "require" -> services.get(arguments[0]);
                case "find" -> Optional.ofNullable(services.get(arguments[0]));
                case "isAvailable" -> services.containsKey(arguments[0]);
                case "getOwner" -> (ServiceOwner) () -> "test";
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
        private final VexPlayer player = new VexPlayer(UUID.randomUUID(), "Alex", type -> 0);
        private final VexLevelInstanceService levels;

        private Fixture() {
            services.put(
                ExecutionComponentCoordinatorService.class,
                new VexExecutionComponentCoordinatorService(registry)
            );
            services.put(RewardRegistry.class, new VexRewardRegistry(registry));
            services.put(RequirementRegistry.class, new VexRequirementRegistry(registry));
            services.put(RewardService.class, new VexRewardService(registry));
            services.put(RequirementService.class, new VexRequirementService(registry));
            services.put(CostService.class, new VexCostService(registry));
            services.put(ExpressionService.class, new VexExpressionService(registry));
            services.put(LevelService.class, new VexLevelService(registry));
            services.put(LevelClaimService.class, new VexLevelClaimService(registry));
            services.put(
                PlayerRewardTransactionService.class, (PlayerRewardTransactionService) (owner, operation) ->
                    ((RewardService) services.get(RewardService.class))
                        .executeAtomically(new PlayerExecutionContext(owner, Map.of()), operation)
            );
            services.put(
                LocalizationService.class, Proxy.newProxyInstance(
                    LocalizationService.class.getClassLoader(),
                    new Class<?>[]{LocalizationService.class}, (proxy, method, arguments) ->
                        LocalizedMessage.single(Component.text(arguments[1].toString()))
                )
            );
            levels = new VexLevelInstanceService(registry);
            services.put(LevelInstanceService.class, levels);
            player.install(LevelPlayerData.KEY, new LevelPlayerData());
            LanguageContainer language = (LanguageContainer) Proxy.newProxyInstance(
                LanguageContainer.class.getClassLoader(),
                new Class<?>[]{LanguageContainer.class}, (proxy, method, arguments) ->
                    new Language(LanguageKey.EN_EN, Component.text("English"), true)
            );
            player.installContainer(0, LanguageContainer.class, language);
        }

        private PlayerExecutionContext context() {
            return new PlayerExecutionContext(player, Map.of());
        }

        private void install(final CompiledReward reward, final int start, final int step) {
            levels.replace(List.of(
                instance("player", List.of(reward), start, step),
                instance("mining", List.of(), 2, 1)
            ));
        }
    }
}
