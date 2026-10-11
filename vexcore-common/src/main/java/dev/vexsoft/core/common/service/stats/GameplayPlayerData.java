package dev.vexsoft.core.common.service.stats;

import dev.vexsoft.core.api.player.DataContainerKey;
import dev.vexsoft.core.api.player.DataContainerRegistry;
import dev.vexsoft.core.api.player.PlayerDataDefinition;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.common.service.data.ScopedPlayerDataKeys;
import dev.vexsoft.core.stats.StatDefinition;
import java.util.Objects;

/** Declares the persistent values backing player stats. */
@Dependencies
public final class GameplayPlayerData implements PlayerDataDefinition {

    static final DataContainerKey<StatData> STATS = DataContainerKey.of("stats", StatData.class, StatData::new);
    private static final ScopedPlayerDataKeys<StatData> SCOPES = new ScopedPlayerDataKeys<>(STATS, StatData::new);

    static DataContainerKey<StatData> key(final StatDefinition definition) {
        return SCOPES.key(definition.getDataScope());
    }

    public GameplayPlayerData(final VexServiceRegistry services) {
        Objects.requireNonNull(services, "services");
    }

    @Override
    public void register(final DataContainerRegistry registry) {
        registry.register(STATS);
    }
}
