package dev.vexsoft.core.api.player;

import dev.vexsoft.core.api.service.VexService;
import java.util.Optional;
import java.util.UUID;

public interface PlayerService extends VexService {

  /** Finds a currently loaded Vex player */
  public Optional<VexPlayer> find(UUID uniqueId);

  /** Returns a currently loaded Vex player or fails when it is unavailable */
  public VexPlayer require(UUID uniqueId);
}
