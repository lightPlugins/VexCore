package dev.vexsoft.core.paper.service.reward;

import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.execution.PlayerExecutionContext;
import dev.vexsoft.core.paper.reward.RewardAnnouncement;
import dev.vexsoft.core.reward.PreparedRewards;
import java.util.Map;

/** Presents one message and sound for each announcement type in a successful reward event. */
public interface RewardAnnouncementService extends VexService {

    /** Announces a successfully granted batch using this plugin's localization and supplied sound definitions. */
    void announce(PreparedRewards rewards, PlayerExecutionContext context, Map<String, RewardAnnouncement> types);
}
