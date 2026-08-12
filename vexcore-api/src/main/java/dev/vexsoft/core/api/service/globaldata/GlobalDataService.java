package dev.vexsoft.core.api.service.globaldata;

import dev.vexsoft.core.api.globaldata.GlobalDataDefinition;
import dev.vexsoft.core.api.globaldata.GlobalDataKey;
import dev.vexsoft.core.api.service.registry.VexService;
import java.util.concurrent.CompletableFuture;
import java.util.function.UnaryOperator;

/** Registers and accesses plugin-owned global data. */
public interface GlobalDataService extends VexService {

  /** Creates and registers a global-data definition through the plugin scope. */
  void register(Class<? extends GlobalDataDefinition> definitionType);

  /** Loads a registered value, or its default when no value has been stored. */
  <T> CompletableFuture<T> get(GlobalDataKey<T> key);

  /** Invalidates the cached value and reloads it from persistent storage. */
  <T> CompletableFuture<T> refresh(GlobalDataKey<T> key);

  /** Stores a registered value and returns when persistence has completed. */
  <T> CompletableFuture<Void> set(GlobalDataKey<T> key, T value);

  /** Atomically transforms a registered value, retrying concurrent database changes. */
  <T> CompletableFuture<T> update(GlobalDataKey<T> key, UnaryOperator<T> updater);

  /** Removes a stored value so that subsequent reads return its default. */
  CompletableFuture<Boolean> reset(GlobalDataKey<?> key);
}
