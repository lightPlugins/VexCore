package dev.vexsoft.core.common.service.globaldata;

import dev.vexsoft.core.api.globaldata.GlobalDataDefinition;
import dev.vexsoft.core.api.globaldata.GlobalDataKey;
import dev.vexsoft.core.api.service.globaldata.GlobalDataService;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexClassFactory;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.UnaryOperator;

/** Owner-scoped global-data facade. */
@Dependencies(GlobalDataCoordinatorService.class)
public final class VexGlobalDataService implements GlobalDataService, AutoCloseable {

  private final VexServiceRegistry services;
  private final GlobalDataCoordinatorService coordinator;

  public VexGlobalDataService(final VexServiceRegistry services) {
    this.services = Objects.requireNonNull(services, "services");
    coordinator = services.require(GlobalDataCoordinatorService.class);
  }

  @Override
  public void register(final Class<? extends GlobalDataDefinition> definitionType) {
    GlobalDataDefinition definition = VexClassFactory.create(
        Objects.requireNonNull(definitionType, "definitionType"),
        services,
        "Global data definition"
    );
    coordinator.register(services.getOwner(), definition);
  }

  @Override
  public <T> CompletableFuture<T> get(final GlobalDataKey<T> key) {
    return coordinator.get(services.getOwner(), key);
  }

  @Override
  public <T> CompletableFuture<T> refresh(final GlobalDataKey<T> key) {
    return coordinator.refresh(services.getOwner(), key);
  }

  @Override
  public <T> CompletableFuture<Void> set(final GlobalDataKey<T> key, final T value) {
    return coordinator.set(services.getOwner(), key, value);
  }

  @Override
  public <T> CompletableFuture<T> update(
      final GlobalDataKey<T> key,
      final UnaryOperator<T> updater
  ) {
    return coordinator.update(services.getOwner(), key, updater);
  }

  @Override
  public CompletableFuture<Boolean> reset(final GlobalDataKey<?> key) {
    return coordinator.reset(services.getOwner(), key);
  }

  @Override
  public void close() {
    coordinator.unregister(services.getOwner());
  }
}
