package dev.vexsoft.core.api.service.network;

import dev.vexsoft.core.api.network.NetworkPlayer;
import dev.vexsoft.core.api.service.registry.VexService;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Shares a leased, proxy-authored directory without requiring a player connection on the reading backend. */
public interface NetworkPresenceService extends VexService {

    /** Reads a recent authoritative proxy snapshot; expired or absent publishers fail closed. */
    CompletableFuture<List<NetworkPlayer>> snapshot();

    /** Publishes this process's proxy snapshot while holding the single network-directory writer lease. */
    CompletableFuture<Void> publish(List<NetworkPlayer> players);

    /** Withdraws this publisher without invalidating a newer proxy instance's snapshot. */
    CompletableFuture<Void> withdraw();
}
