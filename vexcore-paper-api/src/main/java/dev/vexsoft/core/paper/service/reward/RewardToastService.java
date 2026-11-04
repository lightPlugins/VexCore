package dev.vexsoft.core.paper.service.reward;

import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.paper.screenui.reward.RewardCard;
import java.util.List;

/** Core-owned, bounded personal reward feed shared across plugin scopes. */
public interface RewardToastService extends VexService {

    /** Enqueues committed, resolved cards on the viewer's entity thread; overflow uses a localized chat receipt. */
    void show(VexPlayer player, List<RewardCard> cards);

    /** Clears this viewer's pending and visible cards on its entity thread. */
    void clear(VexPlayer player);
}
