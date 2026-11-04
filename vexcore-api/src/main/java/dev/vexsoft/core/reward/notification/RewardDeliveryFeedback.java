package dev.vexsoft.core.reward.notification;

import dev.vexsoft.core.execution.PlayerExecutionContext;
import java.util.HashMap;

/** Controls delivery audio independently of reward grants and their personal receipt cards. */
public enum RewardDeliveryFeedback {
    IMMEDIATE,
    EXTERNAL;

    private static final String VARIABLE = "reward-delivery-feedback";

    /** Returns a copied context whose delivery audio is handled by this policy. */
    public PlayerExecutionContext applyTo(PlayerExecutionContext context) {
        var variables = new HashMap<>(context.variables());
        variables.put(VARIABLE, this);
        return new PlayerExecutionContext(context.player(), variables);
    }

    /** Reads an explicit policy, otherwise retaining immediate pickup feedback. */
    public static RewardDeliveryFeedback from(PlayerExecutionContext context) {
        return context.getVariable(VARIABLE) instanceof RewardDeliveryFeedback feedback ? feedback : IMMEDIATE;
    }
}
