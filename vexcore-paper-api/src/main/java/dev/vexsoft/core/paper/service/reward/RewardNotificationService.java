package dev.vexsoft.core.paper.service.reward;

import dev.vexsoft.core.api.service.registry.VexService;

/** Owner-scoped bridge from committed rewards into Core's shared reward feed. */
public interface RewardNotificationService extends VexService {

    /** Enables or disables future personal reward notifications for this plugin owner. */
    void setEnabled(boolean enabled);
}
