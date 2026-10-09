package dev.vexsoft.core.paper.block;

import java.util.Map;
import org.bukkit.NamespacedKey;

/** Provider-independent block identity and persistent properties. */
public record ResourceBlock(String key, Map<String, String> properties) {

    /** Validates the provider key and copies its properties. */
    public ResourceBlock {
        if (key == null || !key.contains(":") || NamespacedKey.fromString(key) == null) {
            throw new IllegalArgumentException("Expected a namespaced block key: " + key);
        }
        properties = Map.copyOf(properties);
    }
}
