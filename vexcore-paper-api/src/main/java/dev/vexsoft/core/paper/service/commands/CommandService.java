package dev.vexsoft.core.paper.service.commands;

import dev.vexsoft.core.api.service.registry.VexService;

/**
 * Registers annotated command classes for the current plugin
 */
public interface CommandService extends VexService {

  /** Creates and registers every command declared by the given class */
  <T> T register(Class<T> commandType);
}
