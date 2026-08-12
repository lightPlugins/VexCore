package dev.vexsoft.core.common.service.globaldata;

import dev.vexsoft.core.api.globaldata.GlobalDataDefinition;
import dev.vexsoft.core.api.globaldata.GlobalDataKey;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexService;
import java.util.concurrent.CompletableFuture;
import java.util.function.UnaryOperator;

/** Coordinates global-data registrations, persistence, and cache consistency. */
public interface GlobalDataCoordinatorService extends VexService {

  /** Registers one definition for an owner. */
  void register(ServiceOwner owner, GlobalDataDefinition definition);

  /** Loads one registered value. */
  <T> CompletableFuture<T> get(ServiceOwner owner, GlobalDataKey<T> key);

  /** Invalidates and reloads one registered value from persistent storage. */
  <T> CompletableFuture<T> refresh(ServiceOwner owner, GlobalDataKey<T> key);

  /** Stores one registered value. */
  <T> CompletableFuture<Void> set(ServiceOwner owner, GlobalDataKey<T> key, T value);

  /** Atomically transforms one registered value. */
  <T> CompletableFuture<T> update(
      ServiceOwner owner,
      GlobalDataKey<T> key,
      UnaryOperator<T> updater
  );

  /** Removes one registered stored value. */
  CompletableFuture<Boolean> reset(ServiceOwner owner, GlobalDataKey<?> key);

  /** Removes runtime registrations while retaining stored values. */
  void unregister(ServiceOwner owner);
}
