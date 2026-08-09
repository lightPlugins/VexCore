package dev.vexsoft.core.paper.reactor.context;

import dev.vexsoft.core.reactor.context.PlayerReactorContext;

/** Complete context supplied by the built-in {@code damage-entity} trigger. */
public interface DamageEntityReactorContext extends
    PlayerReactorContext,
    TargetEntityReactorContext,
    ItemReactorContext,
    MutableDamageReactorContext,
    CancellableReactorContext {
}
