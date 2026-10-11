package dev.vexsoft.core.common.data.redis;

import java.util.Map;

/** One complete recovery point, including every account, profile and shared value. */
public record NetworkCheckpoint(long generation, String position, Map<String, String> values) {

    public NetworkCheckpoint {
        values = Map.copyOf(values);
    }
}
