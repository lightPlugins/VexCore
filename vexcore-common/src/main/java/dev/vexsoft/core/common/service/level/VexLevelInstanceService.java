package dev.vexsoft.core.common.service.level;

import dev.vexsoft.core.api.configuration.ConfigurationSection;
import dev.vexsoft.core.api.localization.LanguageContainer;
import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.level.LevelClaimService;
import dev.vexsoft.core.api.service.level.LevelInstanceService;
import dev.vexsoft.core.api.service.level.LevelService;
import dev.vexsoft.core.api.service.localization.LocalizationService;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.api.service.requirement.RequirementRegistry;
import dev.vexsoft.core.api.service.reward.PlayerRewardTransactionService;
import dev.vexsoft.core.api.service.reward.RewardRegistry;
import dev.vexsoft.core.api.service.stats.contribution.StatContributionRegistry;
import dev.vexsoft.core.common.requirement.level.LevelRequirement;
import dev.vexsoft.core.common.reward.level.LevelExperienceReward;
import dev.vexsoft.core.execution.PlayerExecutionContext;
import dev.vexsoft.core.level.ClaimedLevelOverflowPolicy;
import dev.vexsoft.core.level.LevelClaimMode;
import dev.vexsoft.core.level.LevelExperienceGain;
import dev.vexsoft.core.level.LevelInstance;
import dev.vexsoft.core.level.LevelInstanceSnapshot;
import dev.vexsoft.core.level.LevelPlayerData;
import dev.vexsoft.core.level.LevelProgress;
import dev.vexsoft.core.level.LevelProgressAccess;
import dev.vexsoft.core.level.LevelRequirementDefinition;
import dev.vexsoft.core.level.LevelSnapshot;
import dev.vexsoft.core.level.claim.LevelClaimBatchResult;
import dev.vexsoft.core.level.claim.LevelClaimResult;
import dev.vexsoft.core.reward.RewardBehavior;
import dev.vexsoft.core.reward.RewardContribution;
import dev.vexsoft.core.reward.RewardContributions;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;

/** Owner-scoped instance runtime using VexCore player transactions and sequential claims. */
@Dependencies({LevelClaimService.class, LevelService.class, PlayerRewardTransactionService.class})
public final class VexLevelInstanceService implements LevelInstanceService {

    private static final System.Logger LOGGER = System.getLogger(VexLevelInstanceService.class.getName());

    private final LevelClaimService claims;
    private final VexServiceRegistry services;
    private volatile boolean registered;
    private final PlayerRewardTransactionService transactions;
    private final LevelService levels;
    private final CopyOnWriteArrayList<ExperienceRegistration> experienceListeners = new CopyOnWriteArrayList<>();
    private final ThreadLocal<Map<String, Integer>> compiling = new ThreadLocal<>();
    private volatile Map<String, LevelInstance> instances = Map.of();

    public VexLevelInstanceService(final VexServiceRegistry services) {
        this.services = services;
        claims = services.require(LevelClaimService.class);
        transactions = services.require(PlayerRewardTransactionService.class);
        levels = services.require(LevelService.class);
    }

    @Override
    public synchronized void registerExecutionTypes() {
        if (!registered) {
            services.require(RewardRegistry.class).register("level-xp", LevelExperienceReward.class);
            services.require(RequirementRegistry.class).register("level", LevelRequirement.class);
            services.find(StatContributionRegistry.class).ifPresent(registry ->
                registry.register(LevelStatContribution.SOURCE_KEY, LevelStatContribution.class));
            registered = true;
        }
    }

    @Override
    public void load(final Collection<? extends ConfigurationSection> configurations) {
        Map<String, Integer> references = new LinkedHashMap<>();
        for (ConfigurationSection section : configurations) {
            String id = section.getString("id");
            if (section.getInt("leveling.max-level", 100) > 10000) {
                throw new IllegalArgumentException("Level instances support at most level 10000");
            }
            if (id == null || references.putIfAbsent(id, section.getInt("leveling.max-level", 100)) != null) {
                throw new IllegalArgumentException("Missing or duplicate level instance ID: " + id);
            }
        }
        compiling.set(references);
        try {
            var replacement = new ArrayList<LevelInstance>();
            for (ConfigurationSection section : configurations) {
                try {
                    replacement.add(new LevelInstance(
                        section.getString("id"), section.getString("name-key"),
                        levels.compile(section)
                    ));
                } catch (IllegalArgumentException failure) {
                    throw new IllegalArgumentException(
                        "Invalid level instance '" + section.getString("id") + "': " + failure.getMessage(), failure);
                }
            }
            replace(replacement);
        } finally {
            compiling.remove();
        }
    }

    @Override
    public void validateReference(final String id, final int minimumLevel) {
        Map<String, Integer> pending = compiling.get();
        Integer maximum = pending == null
            ? (instances.containsKey(id) ? instances.get(id).definition().curve().getMaximumLevel() : null)
            : pending.get(id);
        if (maximum == null || minimumLevel < 0 || minimumLevel > maximum) {
            throw new IllegalArgumentException(
                "Unknown instance or invalid required level: " + id + '/' + minimumLevel);
        }
    }

    @Override
    public boolean meets(final VexPlayer player, final LevelRequirementDefinition requirement) {
        return requirement == null || instances.containsKey(requirement.system())
            && snapshot(player, requirement.system()).progress().level() >= requirement.level();
    }

    @Override
    public Component describeRequirement(final VexPlayer player, final LevelRequirementDefinition requirement) {
        if (requirement == null) {
            return Component.empty();
        }
        LocalizationService localizations = services.require(LocalizationService.class);
        if (!instances.containsKey(requirement.system())) {
            var language = player.getContainer(LanguageContainer.class).getLanguage().getKey();
            return localizations.resolve(language, "levels.requirement.unavailable", Map.of()).getComponent();
        }
        return LevelExecutionText.render(
            localizations, this, new PlayerExecutionContext(player, Map.of()),
            requirement.system(), "levels.requirement." + (meets(player, requirement) ? "satisfied" : "missing"),
            Map.of(
                "current", Integer.toString(snapshot(player, requirement.system()).progress().level()),
                "required", Integer.toString(requirement.level())
            )
        );
    }

    @Override
    public void replace(final Collection<LevelInstance> definitions) {
        Map<String, LevelInstance> replacement = new LinkedHashMap<>();

        for (LevelInstance instance : definitions) {
            if (instance.definition().claimMode() != LevelClaimMode.MANUAL
                || instance.definition().overflowPolicy() != ClaimedLevelOverflowPolicy.KEEP) {
                throw new IllegalArgumentException("Level instances require MANUAL claims and KEEP overflow policy");
            }
            if (replacement.putIfAbsent(instance.id(), instance) != null) {
                throw new IllegalArgumentException("Duplicate level instance: " + instance.id());
            }
            for (var rule : instance.definition().rules()) {
                if (!rule.costs().entries().isEmpty() || !rule.requirements().entries().isEmpty()) {
                    throw new IllegalArgumentException("Instance rewards are unlocked by XP, without claim costs");
                }
                for (var entry : rule.rewards().entries()) {
                    if (entry.reward().getBehavior() == RewardBehavior.ACTION
                        && !entry.reward().supportsPlayerRollback()) {
                        throw new IllegalArgumentException(
                            "Level rewards must support player rollback: " + entry.key());
                    }
                }
            }
        }

        instances = Collections.unmodifiableMap(replacement);
        if (registered) {
            services.find(StatContributionRegistry.class).ifPresent(registry ->
                registry.refreshAll(LevelStatContribution.SOURCE_KEY));
        }
    }

    @Override
    public RewardContributions calculateContributions(final VexPlayer player) {
        Map<String, RewardContribution> result = new LinkedHashMap<>();
        for (LevelInstance instance : instances.values()) {
            claims.calculateContributions(player, access(instance), instance.definition(), Map.of()).values()
                .forEach((key, contribution) -> result.merge(key, contribution, RewardContribution::merge));
        }
        return new RewardContributions(result);
    }

    @Override
    public Map<String, LevelInstance> getInstances() {
        return instances;
    }

    @Override
    public LevelInstance require(final String id) {
        LevelInstance instance = instances.get(id);
        if (instance == null) {
            throw new IllegalArgumentException("Unknown level instance: " + id);
        }
        return instance;
    }

    @Override
    public LevelInstanceSnapshot snapshot(final VexPlayer player, final String id) {
        return snapshot(player, require(id));
    }

    @Override
    public LevelSnapshot addExperience(final VexPlayer player, final String id, final double amount) {
        LevelInstance instance = require(id);
        if (!Double.isFinite(amount) || amount <= 0) {
            throw new IllegalArgumentException("Experience must be finite and positive");
        }
        synchronized (player) {
            double previous = access(instance).read(player).getExperience();
            double total = previous + amount;
            if (!Double.isFinite(total)) {
                throw new IllegalArgumentException("Experience overflow");
            }
            player.update(
                LevelPlayerData.KEY, data -> {
                    progress(data, instance).setExperience(total);
                }
            );

            // Skip comparison and notification allocation when no consumer observes XP gains.
            if (experienceListeners.isEmpty() || total <= previous) {
                return instance.definition().curve().calculate(total);
            }

            var change = instance.definition().curve().compare(previous, total);
            var gain = new LevelExperienceGain(player.getUniqueId(), id, total - previous, change);
            player.afterCommit(() -> notifyExperience(gain));

            return change.current();
        }
    }

    @Override
    public ExperienceSubscription subscribeExperience(final Consumer<LevelExperienceGain> listener) {
        var registration = new ExperienceRegistration(Objects.requireNonNull(listener, "listener"));
        experienceListeners.add(registration);

        return registration;
    }

    @Override
    public LevelClaimBatchResult claim(final VexPlayer player, final String id, final boolean all) {
        LevelInstance instance = require(id);
        var results = new ArrayList<LevelClaimResult>();

        synchronized (player) {
            // Bound this batch to the level reached before rewards; XP rewards cannot extend the batch forever.
            int available = snapshot(player, instance).progress().level();
            while (true) {
                LevelInstanceSnapshot state = snapshot(player, instance);
                int next = state.nextRewardLevel();
                if (next < 0 || next > available) {
                    break;
                }
                LevelProgressAccess<LevelPlayerData> access = access(instance);
                boolean successful = transactions.execute(
                    player, () -> {
                        // Skip empty levels in the same transaction as the reward-bearing claim.
                        access.updateClaimedLevel(player, next - 1);
                        LevelClaimResult result = claims.claimNext(player, access, instance.definition(), Map.of());
                        results.add(result);
                        return result.isSuccessful();
                    }
                );
                if (!successful || !all) {
                    break;
                }
            }
        }
        if (results.stream().anyMatch(LevelClaimResult::isSuccessful)) {
            services.find(StatContributionRegistry.class).ifPresent(registry ->
                registry.refresh(player, LevelStatContribution.SOURCE_KEY));
        }
        return new LevelClaimBatchResult(results);
    }

    private static LevelInstanceSnapshot snapshot(final VexPlayer player, final LevelInstance instance) {
        LevelProgress progress = access(instance).read(player);
        LevelSnapshot level = instance.definition().curve().calculate(progress.getExperience());
        int next = -1;
        int count = 0;
        for (int target = progress.getClaimedLevel() + 1;
             target <= instance.definition().curve().getMaximumLevel(); target++) {
            boolean hasRewards = instance.definition().getRules(target).stream()
                .anyMatch(rule -> !rule.rewards().entries().isEmpty());
            if (hasRewards) {
                if (next < 0) {
                    next = target;
                }
                if (target <= level.level()) {
                    count++;
                }
            }
        }
        return new LevelInstanceSnapshot(level, progress.getClaimedLevel(), next, count);
    }

    private void notifyExperience(final LevelExperienceGain gain) {
        for (ExperienceRegistration registration : experienceListeners) {
            try {
                registration.listener.accept(gain);
            } catch (RuntimeException failure) {
                LOGGER.log(
                    System.Logger.Level.ERROR,
                    "Level experience listener failed for " + gain.playerId() + '/' + gain.instanceId(),
                    failure
                );
            }
        }
    }

    private static LevelPlayerData.Progress progress(final LevelPlayerData data, final LevelInstance instance) {
        return data.getInstances().computeIfAbsent(
            instance.id(), ignored -> {
                var progress = new LevelPlayerData.Progress();
                progress.setClaimedLevel(instance.definition().curve().getMinimumLevel());
                return progress;
            }
        );
    }

    private static LevelProgressAccess<LevelPlayerData> access(final LevelInstance instance) {
        return new LevelProgressAccess<>(
            LevelPlayerData.KEY,
            data -> {
                LevelPlayerData.Progress stored = data.getInstances().get(instance.id());
                int minimum = instance.definition().curve().getMinimumLevel();
                return new Progress(
                    stored == null ? 0 : stored.getExperience(),
                    stored == null ? minimum : Math.max(minimum, stored.getClaimedLevel())
                );
            },
            (data, level) -> progress(data, instance).setClaimedLevel(level)
        );
    }

    private record Progress(double experience, int claimedLevel) implements LevelProgress {

        @Override
        public double getExperience() {
            return experience;
        }

        @Override
        public int getClaimedLevel() {
            return claimedLevel;
        }
    }

    private final class ExperienceRegistration implements ExperienceSubscription {

        private final Consumer<LevelExperienceGain> listener;

        private ExperienceRegistration(final Consumer<LevelExperienceGain> listener) {
            this.listener = listener;
        }

        @Override
        public void close() {
            experienceListeners.remove(this);
        }
    }
}
