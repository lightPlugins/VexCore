package dev.vexsoft.core.api.configuration;

import dev.vexsoft.core.api.service.VexService;
import java.nio.file.Path;
import java.util.Map;

/**
 * Loads and caches configuration files owned by the current plugin
 */
public interface ConfigurationService extends VexService {

  /** Returns the owner associated with this configuration service */
  ConfigurationOwner getOwner();

  /** Loads a YAML file and merges defaults from the matching bundled resource */
  VexConfiguration load(String relativePath);

  /** Loads a YAML file and merges defaults from the matching bundled resource */
  VexConfiguration load(Path relativePath);

  /** Loads a YAML file and optionally merges its matching bundled defaults */
  VexConfiguration load(Path relativePath, boolean loadDefaults);

  /** Loads a YAML file and merges defaults from a specific bundled resource */
  VexConfiguration load(Path relativePath, String defaultsResource);

  /** Loads every public YAML file found below a relative directory */
  Map<Path, VexConfiguration> loadDirectory(Path relativeDirectory);
}
