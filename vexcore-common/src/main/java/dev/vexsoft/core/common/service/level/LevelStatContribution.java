package dev.vexsoft.core.common.service.level;

import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.level.LevelInstanceService;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.stats.StatKey;
import dev.vexsoft.core.stats.StatModifier;
import dev.vexsoft.core.stats.contribution.StatContributionProvider;
import dev.vexsoft.core.stats.contribution.StatRewardContribution;
import java.util.Map;

/** Reconstructs stat rewards only from manually claimed level rewards across active instances. */
@Dependencies(LevelInstanceService.class)
public final class LevelStatContribution implements StatContributionProvider {

    public static final String SOURCE_KEY = "level-instances";
    private final LevelInstanceService levels;

    public LevelStatContribution(final VexServiceRegistry services) {
        levels = services.require(LevelInstanceService.class);
    }

    @Override
    public Map<StatKey, StatModifier> calculate(final VexPlayer player) {
        return levels.calculateContributions(player).find("stats", StatRewardContribution.class)
            .map(StatRewardContribution::modifiers).orElse(Map.of());
    }
}
