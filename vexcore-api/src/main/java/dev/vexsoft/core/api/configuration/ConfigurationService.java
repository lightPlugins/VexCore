package dev.vexsoft.core.api.configuration;

import dev.vexsoft.core.api.service.VexService;
import java.nio.file.Path;
import java.util.Map;

/**
 * Loads and caches configuration files owned by the current plugin
 */
public interface ConfigurationService extends VexService {

  /** Returns the owner associated with this configuration service */
  public ConfigurationOwner getOwner();

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
