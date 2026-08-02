package dev.vexsoft.core.command;

import dev.vexsoft.core.api.service.VexService;

public interface CommandService extends VexService {

  /** Creates and registers every command declared by the given class */
  public <T> T register(Class<T> commandType);
}
