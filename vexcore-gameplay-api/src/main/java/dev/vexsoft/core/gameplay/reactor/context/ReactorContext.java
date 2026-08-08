package dev.vexsoft.core.gameplay.reactor.context;

/** Supplies the runtime data produced by one trigger invocation. */
public interface ReactorContext {

  /**
   * Resolves a hyphen-separated expression variable.
   *
   * @param name variable name without percent delimiters
   * @return resolved value, or {@code null} when it is unavailable
   */
  default Object getVariable(final String name) {
    return null;
  }
}
