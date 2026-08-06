package dev.vexsoft.core.api.theme;

import dev.vexsoft.core.api.service.VexService;
import java.util.Map;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

/**
 * Provides the globally configured colors used by the VexSoft plugin ecosystem.
 *
 * <p>Theme colors can be used as MiniMessage tags such as
 * {@code <primary>Text</primary>} when rendering through this service.
 */
public interface ThemeColorService extends VexService {

  /** Returns the configured color with the given case-insensitive name. */
  Optional<TextColor> findColor(String name);

  /** Returns the configured color or throws when the name is unknown. */
  TextColor requireColor(String name);

  /** Returns an immutable snapshot of all configured theme colors. */
  Map<String, TextColor> getColors();

  /** Deserializes MiniMessage text with the configured theme color tags. */
  Component deserialize(String input);

  /** Reloads and validates the theme colors from {@code theme-colors.yml}. */
  void reload();
}
