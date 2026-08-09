package dev.vexsoft.core.api.service.placeholder;

import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.placeholder.PlaceholderContext;
import dev.vexsoft.core.placeholder.VexPlaceholder;
import net.kyori.adventure.text.Component;

/** Registers and resolves player-bound placeholders owned by one plugin scope. */
public interface PlaceholderService extends VexService {

  /** Creates and registers an annotated placeholder class for this service owner. */
  <T extends VexPlaceholder> T register(Class<T> placeholderType);

  /** Resolves registered placeholders for a loaded player. */
  String resolve(VexPlayer player, String input);

  /** Resolves registered and request-local placeholders. */
  String resolve(PlaceholderContext context, String input);

  /** Resolves placeholders inside text components while preserving their styling. */
  Component resolve(VexPlayer player, Component component);

  /** Removes every placeholder registered by this service owner. */
  void clear();
}
