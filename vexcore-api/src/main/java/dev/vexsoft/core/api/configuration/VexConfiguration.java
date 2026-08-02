package dev.vexsoft.core.api.configuration;

import java.nio.file.Path;

/**
 * Represents a YAML configuration backed by a file on disk
 */
public interface VexConfiguration extends ConfigurationSection {
  /** Returns the absolute path of the backing YAML file */
  public Path getFile();

  /** Reloads this configuration from its backing file */
  public void reload();

  /** Saves the current configuration state to its backing file */
  public void save();
}
