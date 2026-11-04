package dev.vexsoft.core.reward.notification;

import dev.vexsoft.core.execution.PlayerExecutionContext;
import java.util.List;

/** Receives frozen receipts after the enclosing player transaction and durable settlement succeed. */
@FunctionalInterface
public interface RewardDeliveryObserver {

    /** Observes a committed delivery; observers must not grant rewards or mutate the receipt. */
    void delivered(List<RewardNotification> entries, PlayerExecutionContext context);
}
