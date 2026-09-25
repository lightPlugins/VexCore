package dev.vexsoft.core.api.service.configuration;

import dev.vexsoft.core.api.service.registry.VexService;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Stores and serves owner-scoped, immutable configuration snapshots shared through the database. */
public interface PublishedConfigurationService extends VexService {

    /** Registers one configuration type and loads its current snapshot when present. */
    <T> void register(Class<T> type, String system);

    /** Checks whether this owner registered a configuration type. */
    boolean isRegistered(Class<?> type);

    /** Returns the currently published snapshot, if one exists. */
    <T> Optional<T> find(Class<T> type);

    /** Returns the currently published snapshot or fails when none exists. */
    <T> T require(Class<T> type);

    /** Publishes a validated snapshot and its source commit, returning the active version. */
    <T> CompletableFuture<PublishedConfigurationVersion> publish(Class<T> type, T value, String commit);

    /** Calls a listener after a new snapshot has been loaded locally. */
    <T> AutoCloseable subscribe(Class<T> type, Consumer<T> listener);
}
