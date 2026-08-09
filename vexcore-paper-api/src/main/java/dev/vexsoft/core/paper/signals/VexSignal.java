package dev.vexsoft.core.paper.signals;

import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.key.Key;

/**
 * Describes an immutable fact published through the local signal system.
 */
public interface VexSignal {

  /** Returns the stable namespaced identifier of this signal. */
  Key getKey();

  /** Returns the player or other subject associated with this signal, when present. */
  Optional<UUID> getSubject();

  /** Returns the positive quantity contributed by this signal. */
  long getAmount();

  /** Returns the immutable attributes used to describe and filter this signal. */
  SignalAttributes getAttributes();
}
