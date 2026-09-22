package dev.vexsoft.core.level;

import dev.vexsoft.core.api.player.DataContainerKey;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/** Persistent progress for the level instances owned by one plugin. */
@Getter
public final class LevelPlayerData {

    public static final DataContainerKey<LevelPlayerData> KEY =
        DataContainerKey.of("level_instances", LevelPlayerData.class, LevelPlayerData::new);
    private Map<String, Progress> instances = new LinkedHashMap<>();

    /** Copies deserialized instance entries while preserving mutable player-owned progress values. */
    public void setInstances(final Map<String, Progress> instances) {
        this.instances = instances == null ? new LinkedHashMap<>() : new LinkedHashMap<>(instances);
    }

    /** Serializable mutable progress, accessed only inside player read/update callbacks. */
    @Getter
    @Setter
    public static final class Progress {

        private double experience;
        private int claimedLevel;
    }
}
