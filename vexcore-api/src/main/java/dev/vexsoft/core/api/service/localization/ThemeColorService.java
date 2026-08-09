package dev.vexsoft.core.api.service.localization;

import dev.vexsoft.core.api.service.registry.VexService;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

/**
 * Provides the globally configured colors used by the VexSoft plugin ecosystem.
 *
 * <p>Theme colors can be used as MiniMessage tags such as
 * {@code <tailwind:red:6>Text</tailwind:red:6>} when rendering through this service.
 */
public interface ThemeColorService extends VexService {

  /** Returns a shade from the specified case-insensitive theme and color family. */
  Optional<TextColor> findColor(String theme, String color, int shade);

  /** Returns a shade or throws when the theme, color family, or shade is unknown. */
  TextColor requireColor(String theme, String color, int shade);

  /** Returns the immutable, precomputed snapshot of all loaded themes. */
  Map<String, Map<String, List<TextColor>>> getThemes();

  /** Deserializes MiniMessage text with the configured theme color tags. */
  Component deserialize(String input);

  /** Reloads and validates every YAML theme from the {@code themes} directory. */
  void reload();
}
