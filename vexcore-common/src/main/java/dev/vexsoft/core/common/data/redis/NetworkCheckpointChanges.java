package dev.vexsoft.core.common.data.redis;

import java.util.Map;
import java.util.Set;

/** Final journal changes at one cut; untouched records remain at the previous committed value. */
public record NetworkCheckpointChanges(long generation, String position, Map<String, String> values, Set<String> removed) {

    public NetworkCheckpointChanges {
        values = Map.copyOf(values);
        removed = Set.copyOf(removed);
        if (values.keySet().stream().anyMatch(removed::contains)) {
            throw new IllegalArgumentException("A checkpoint record cannot be written and removed together");
        }
    }
}
