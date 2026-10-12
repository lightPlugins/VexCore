package dev.vexsoft.core.paper.service.network;

import dev.vexsoft.core.api.service.registry.VexService;

/** Publishes local readiness and loaded-world occupancy for shared network admission. */
public interface BackendPresenceService extends VexService {

    /** Starts bounded, asynchronous heartbeats after the Paper plugin has enabled. */
    void start();
}
