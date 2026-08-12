package dev.vexsoft.core.api.service.level;

import dev.vexsoft.core.api.configuration.ConfigurationSection;
import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.level.CompiledLevelDefinition;
import dev.vexsoft.core.level.LevelDefinition;

/** Compiles reusable level curves and their claim rules. */
public interface LevelService extends VexService {

  /** Compiles a configuration-independent definition. */
  CompiledLevelDefinition compile(LevelDefinition definition);

  /** Parses and compiles a section containing {@code leveling} and {@code levels}. */
  CompiledLevelDefinition compile(ConfigurationSection section);
}
