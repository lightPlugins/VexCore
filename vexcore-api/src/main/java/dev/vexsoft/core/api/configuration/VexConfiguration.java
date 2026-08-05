package dev.vexsoft.core.api.configuration;

import java.nio.file.Path;

/**
 * Represents a YAML configuration backed by a file on disk
 */
public interface VexConfiguration extends ConfigurationSection {
  /** Returns the absolute path of the backing YAML file */
  Path getFile();

  /** Reloads this configuration from its backing file */
  void reload();

  /** Saves the current configuration state to its backing file */
  void save();
}
