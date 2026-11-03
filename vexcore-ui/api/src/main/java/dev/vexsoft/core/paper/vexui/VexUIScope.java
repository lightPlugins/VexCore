package dev.vexsoft.core.paper.vexui;

/** Lifetime of the currently visible view; navigation, search and session retirement invalidate it. */
public interface VexUIScope {

    /** Returns whether this exact view activation still owns the visible session. */
    boolean isActive();

    /** Dispatches a mutation to the entity scheduler only if this activation is still current. */
    void update(Runnable mutation);

    /** Registers cleanup, invoked once on invalidation; cleanup must not access Bukkit world state. */
    VexUISubscription onClose(Runnable cleanup);
}
