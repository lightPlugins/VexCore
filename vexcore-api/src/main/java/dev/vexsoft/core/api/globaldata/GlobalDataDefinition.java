package dev.vexsoft.core.api.globaldata;

/** Declares the global-data keys supplied by one plugin. */
public interface GlobalDataDefinition {

  /** Registers every global-data key supplied by this definition. */
  void register(GlobalDataRegistry registry);
}
