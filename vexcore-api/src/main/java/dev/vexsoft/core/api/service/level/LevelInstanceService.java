package dev.vexsoft.core.api.service.level;

import dev.vexsoft.core.api.configuration.ConfigurationSection;
import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.level.LevelInstance;
import dev.vexsoft.core.level.LevelInstanceSnapshot;
import dev.vexsoft.core.level.LevelRequirementDefinition;
import dev.vexsoft.core.level.LevelSnapshot;
import dev.vexsoft.core.level.claim.LevelClaimBatchResult;
import dev.vexsoft.core.reward.RewardContributions;
import java.util.Collection;
import java.util.Map;
import net.kyori.adventure.text.Component;

/** Owns independent XP and manual reward progression for configured level instances. */
public interface LevelInstanceService extends VexService {

    /** Registers the level-xp reward and level requirement for this catalog owner once. */
    void registerExecutionTypes();

    /** Compiles a complete catalog, validating cross-instance references before publishing it. */
    void load(Collection<? extends ConfigurationSection> configurations);

    /** Validates a reference against the active or currently compiling catalog. */
    void validateReference(String id, int minimumLevel);

    /** Tests an optional minimum-level requirement independently of claimed rewards. */
    boolean meets(VexPlayer player, LevelRequirementDefinition requirement);

    /** Renders a level requirement using the catalog owner's localization templates. */
    Component describeRequirement(VexPlayer player, LevelRequirementDefinition requirement);

    /** Reconstructs contribution rewards from claimed levels across the active catalog. */
    RewardContributions calculateContributions(VexPlayer player);

    /** Replaces the validated catalog without deleting any stored player progress. */
    void replace(Collection<LevelInstance> instances);

    /** Returns the immutable active catalog. */
    Map<String, LevelInstance> getInstances();

    /** Resolves an active instance or rejects an unknown ID. */
    LevelInstance require(String id);

    /** Derives the reached level and the number of unclaimed reward-bearing levels. */
    LevelInstanceSnapshot snapshot(VexPlayer player, String id);

    /** Adds finite positive XP and immediately derives the new level, independently of claims. */
    LevelSnapshot addExperience(VexPlayer player, String id, double amount);

    /** Claims the next reward-bearing level, or all currently available rewards when requested. */
    LevelClaimBatchResult claim(VexPlayer player, String id, boolean all);
}
