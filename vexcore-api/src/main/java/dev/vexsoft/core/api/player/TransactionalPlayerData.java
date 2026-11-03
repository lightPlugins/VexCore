package dev.vexsoft.core.api.player;

/** Allows frequently mutated containers to supply a direct rollback snapshot instead of JSON conversion. */
public interface TransactionalPlayerData<T> {

    /** Returns an independent snapshot with no shared mutable state. */
    T copyForTransaction();
}
