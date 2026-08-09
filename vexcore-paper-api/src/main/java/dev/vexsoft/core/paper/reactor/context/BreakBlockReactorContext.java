package dev.vexsoft.core.paper.reactor.context;

import dev.vexsoft.core.reactor.context.PlayerReactorContext;

/** Complete context supplied by the built-in {@code break-block} trigger. */
public interface BreakBlockReactorContext extends
    PlayerReactorContext,
    BlockReactorContext,
    ItemReactorContext,
    CancellableReactorContext {
}
