package dev.vexsoft.core.api.service.reward;

import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.registry.VexService;
import java.util.function.BooleanSupplier;

/** Coordinates persistent and platform inventory rollback for player rewards. */
public interface PlayerRewardTransactionService extends VexService {

    /** Restores player data and platform inventory when the operation fails or throws. */
    boolean execute(VexPlayer player, BooleanSupplier operation);
}
