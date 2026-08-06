package dev.vexsoft.core.velocity.plugin;

import java.util.Objects;
import org.slf4j.Logger;

/** Adds a configurable plugin prefix to Velocity console messages */
public final class VexProxyLogger {

  private final Logger delegate;
  private final String prefix;

  /** Creates a prefix-aware wrapper around Velocity's plugin logger */
  public VexProxyLogger(final Logger delegate, final String prefix) {
    this.delegate = Objects.requireNonNull(delegate, "delegate");
    this.prefix = Objects.requireNonNull(prefix, "prefix");
  }

  /** Logs an informational message with this plugin's prefix */
  public void info(final String message) {
    delegate.info("{}{}", prefix, message);
  }

  /** Logs a warning with this plugin's prefix */
  public void warning(final String message) {
    delegate.warn("{}{}", prefix, message);
  }

  /** Logs a warning and its cause with this plugin's prefix */
  public void warning(final String message, final Throwable cause) {
    delegate.warn("{}{}", prefix, message, cause);
  }

  /** Logs an error with this plugin's prefix */
  public void severe(final String message) {
    delegate.error("{}{}", prefix, message);
  }

  /** Logs an error and its cause with this plugin's prefix */
  public void severe(final String message, final Throwable cause) {
    delegate.error("{}{}", prefix, message, cause);
  }
}
