package dev.vexsoft.core.api.globaldata;

/** Collects the global-data keys owned by one plugin. */
@FunctionalInterface
public interface GlobalDataRegistry {

  /** Registers one global-data key. */
  void register(GlobalDataKey<?> key);
}
