package dev.vexsoft.core.paper.service.network;

import dev.vexsoft.core.api.network.ServerId;
import dev.vexsoft.core.api.service.registry.VexService;

/** Supplies the Velocity registration ID of the current Paper server. */
public interface ServerIdentityService extends VexService {

  /** Returns the configured backend server ID. */
  ServerId getServerId();
}
