package dev.vexsoft.core.api.configuration;

import java.nio.file.Path;
import java.util.Map;

public interface PluginConfigurations {
  /** Returns the owner associated with this configuration facade */
  public ConfigurationOwner owner();

  /** Loads a YAML file and merges defaults from the matching bundled resource */
  public VexConfiguration load(String relativePath);

  /** Loads a YAML file and merges defaults from the matching bundled resource */
  public VexConfiguration load(Path relativePath);

  /** Loads a YAML file and optionally merges its matching bundled defaults */
  public VexConfiguration load(Path relativePath, boolean loadDefaults);

  /** Loads a YAML file and merges defaults from a specific bundled resource */
  public VexConfiguration load(Path relativePath, String defaultsResource);

  /** Loads every public YAML file found below a relative directory */
  public Map<Path, VexConfiguration> loadDirectory(Path relativeDirectory);
}
