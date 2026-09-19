package dev.vexsoft.core.common.service.stats;

import dev.vexsoft.core.stats.Stat;
import dev.vexsoft.core.stats.StatDefinition;
import dev.vexsoft.core.stats.StatKey;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;

final class RegisteredStat implements Stat {

    @Getter(AccessLevel.PACKAGE)
    private final String owner;
    @Getter(onMethod_ = @Override)
    private final int runtimeId;
    @Getter(AccessLevel.PACKAGE)
    private final long generation;
    @Getter(onMethod_ = @Override)
    private volatile StatDefinition definition;
    @Getter(onMethod_ = @Override)
    private volatile boolean registered = true;

    RegisteredStat(final String owner, final int runtimeId, final long generation, final StatDefinition definition) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.runtimeId = runtimeId;
        this.generation = generation;
        this.definition = Objects.requireNonNull(definition, "definition");
    }

    @Override
    public StatKey getKey() {
        return definition.getKey();
    }

    void update(final StatDefinition updatedDefinition) {
        definition = Objects.requireNonNull(updatedDefinition, "updatedDefinition");
    }

    void unregister() {
        registered = false;
    }
}
