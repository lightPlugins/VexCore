package dev.vexsoft.core.api.configuration;

import dev.vexsoft.core.api.service.ServiceOwner;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Optional;

public interface ConfigurationOwner extends ServiceOwner {
  /** Returns the root directory used to store this owner's configuration files */
  public Path configurationDirectory();

  /** Opens a bundled configuration resource when it exists */
  public Optional<InputStream> configurationResource(String resourcePath);

  /** Reports a non-fatal warning produced while processing configuration data */
  public void configurationWarning(String message, Throwable cause);
}
