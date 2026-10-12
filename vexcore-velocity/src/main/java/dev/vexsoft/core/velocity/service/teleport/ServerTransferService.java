package dev.vexsoft.core.velocity.service.teleport;

import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.common.messaging.teleport.TeleportPreparationResult;
import dev.vexsoft.core.common.messaging.teleport.TeleportTransferRequest;

/** Gates backend connections on the source's asynchronous handover. */
public interface ServerTransferService extends VexService {

    /** Registers the proxy connection listeners once. */
    void start();

    /** Starts an explicit teleport while its authenticated source still owns the player. */
    void transfer(TeleportTransferRequest request, String sourceServer);

    /** Resolves preparation only for its matching request and backend. */
    void prepared(TeleportPreparationResult result, String sourceServer);
}
