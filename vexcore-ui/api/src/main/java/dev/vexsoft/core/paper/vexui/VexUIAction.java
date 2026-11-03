package dev.vexsoft.core.paper.vexui;

/** Handles one action on the viewer's entity scheduler. */
@FunctionalInterface
public interface VexUIAction {

    /** Applies a domain operation or changes view state; refresh is coalesced automatically. */
    void execute(VexUISession session);
}
