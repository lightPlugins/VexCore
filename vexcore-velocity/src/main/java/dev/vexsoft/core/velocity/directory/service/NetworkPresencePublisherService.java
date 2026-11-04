package dev.vexsoft.core.velocity.directory.service;

import dev.vexsoft.core.api.service.registry.VexService;

/** Publishes Velocity's player directory independently of backend plugin-message carriers. */
public interface NetworkPresencePublisherService extends VexService {

    /** Starts periodic, leased snapshots after the Core proxy runtime is initialized. */
    void start();
}
