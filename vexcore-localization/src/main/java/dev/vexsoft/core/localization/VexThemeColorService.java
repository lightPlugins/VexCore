package dev.vexsoft.core.localization;

import dev.vexsoft.core.api.configuration.ConfigurationService;
import dev.vexsoft.core.api.configuration.VexConfiguration;
import dev.vexsoft.core.api.service.Dependencies;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import dev.vexsoft.core.api.theme.ThemeColorService;
import java.util.LinkedHashMap;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

@Dependencies(ConfigurationService.class)
public final class VexThemeColorService implements ThemeColorService {

  private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9_-]*");
  private static final Pattern HEX_COLOR = Pattern.compile("#[0-9a-f]{6}");
  private static final Set<String> RESERVED_THEME_NAMES = Set.of(
      "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple",
      "gold", "gray", "dark_gray", "blue", "green", "aqua", "red", "light_purple",
      "yellow", "white", "color", "colour", "gradient", "rainbow", "reset",
      "bold", "b", "italic", "em", "i", "underlined", "u", "strikethrough", "st",
      "obfuscated", "obf", "font", "click", "hover", "insertion", "newline", "br"
  );
  private static final Path THEMES_DIRECTORY = Path.of("themes");
  private static final Path DEFAULT_THEME = THEMES_DIRECTORY.resolve("tailwind.yml");

  private final MiniMessage miniMessage = MiniMessage.miniMessage();
  private final ConfigurationService configurations;
  private volatile ThemeSnapshot snapshot;

  public VexThemeColorService(final VexServiceRegistry services) {
    configurations = Objects.requireNonNull(services, "services").require(ConfigurationService.class);
    snapshot = loadSnapshot();
  }

  @Override
  public Optional<TextColor> findColor(
      final String theme,
      final String color,
      final int shade
  ) {
    Map<String, List<TextColor>> selectedTheme = snapshot.themes().get(normalize(theme));
    if (selectedTheme == null) {
      return Optional.empty();
    }
    List<TextColor> shades = selectedTheme.get(normalize(color));
    if (shades == null || shade < 1 || shade > shades.size()) {
      return Optional.empty();
    }
    return Optional.of(shades.get(shade - 1));
  }

  @Override
  public TextColor requireColor(final String theme, final String color, final int shade) {
    return findColor(theme, color, shade).orElseThrow(() -> new IllegalArgumentException(
        "Unknown theme color: " + normalize(theme) + ":" + normalize(color) + ":" + shade
    ));
  }

  @Override
  public Map<String, Map<String, List<TextColor>>> getThemes() {
    return snapshot.themes();
  }

  @Override
  public Component deserialize(final String input) {
    return miniMessage.deserialize(Objects.requireNonNull(input, "input"), snapshot.resolver());
  }

  @Override
  public void reload() {
    snapshot = loadSnapshot();
  }

  private ThemeSnapshot loadSnapshot() {
    configurations.load(DEFAULT_THEME, "themes/tailwind.yml");
    Map<Path, VexConfiguration> files = configurations.loadDirectory(THEMES_DIRECTORY);
    Map<String, Map<String, List<TextColor>>> themes = new LinkedHashMap<>();
    TagResolver.Builder tags = TagResolver.builder();
    files.forEach((path, configuration) -> {
      String theme = themeName(path);
      if (themes.containsKey(theme)) {
        throw new IllegalArgumentException("Duplicate theme name: " + theme);
      }
      Map<String, List<TextColor>> colors = parseColors(theme, configuration);
      themes.put(theme, colors);
      tags.tag(theme, (arguments, context) -> {
        String color = arguments.popOr("Expected a color name for theme '" + theme + "'").value();
        List<TextColor> shades = colors.get(normalize(color));
        if (shades == null) {
          throw context.newException("Unknown color '" + color + "' in theme '" + theme + "'");
        }
        int shade = arguments.hasNext()
            ? parseShade(arguments.pop().value(), theme, color)
            : shades.size() / 2 + 1;
        if (arguments.hasNext() || shade > shades.size()) {
          throw context.newException("Invalid shade for theme color '" + theme + ":" + color + "'");
        }
        return Tag.styling(shades.get(shade - 1));
      });
    });
    if (themes.isEmpty()) {
      throw new IllegalArgumentException("The themes directory must contain at least one YAML file");
    }
    return new ThemeSnapshot(immutableThemes(themes), tags.build());
  }

  private Map<String, List<TextColor>> parseColors(
      final String theme,
      final VexConfiguration source
  ) {
    Map<String, List<TextColor>> colors = new LinkedHashMap<>();
    for (String rawName : source.getKeys(false)) {
      String name = normalize(rawName);
      validateName("color", name);
      List<String> rawColors = source.getStringList(rawName);
      if (rawColors.isEmpty()) {
        String singleColor = source.getString(rawName);
        rawColors = singleColor == null ? List.of() : List.of(singleColor);
      }
      if (rawColors.isEmpty()) {
        throw new IllegalArgumentException("Theme color '" + theme + ":" + name + "' is empty");
      }
      colors.put(name, rawColors.stream().map(value -> parseColor(theme, name, value)).toList());
    }
    if (colors.isEmpty()) {
      throw new IllegalArgumentException("Theme '" + theme + "' must define at least one color");
    }
    return Map.copyOf(colors);
  }

  private TextColor parseColor(final String theme, final String name, final String value) {
    String normalized = value.trim().toLowerCase(Locale.ROOT);
    if (!HEX_COLOR.matcher(normalized).matches()) {
      throw new IllegalArgumentException("Invalid theme color '" + theme + ":" + name + "': " + value);
    }
    return Objects.requireNonNull(TextColor.fromHexString(normalized));
  }

  private String themeName(final Path path) {
    String fileName = Objects.requireNonNull(path.getFileName()).toString().toLowerCase(Locale.ROOT);
    String theme = fileName.substring(0, fileName.length() - ".yml".length());
    validateName("theme", theme);
    if (RESERVED_THEME_NAMES.contains(theme)) {
      throw new IllegalArgumentException("Theme name is reserved by MiniMessage: " + theme);
    }
    return theme;
  }

  private int parseShade(final String value, final String theme, final String color) {
    try {
      int shade = Integer.parseInt(value);
      if (shade < 1) {
        throw new NumberFormatException();
      }
      return shade;
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("Invalid shade for theme color '" + theme + ":" + color + "': " + value);
    }
  }

  private void validateName(final String type, final String name) {
    if (!NAME.matcher(name).matches()) {
      throw new IllegalArgumentException("Invalid " + type + " name: " + name);
    }
  }

  private String normalize(final String name) {
    return Objects.requireNonNull(name, "name").trim().toLowerCase(Locale.ROOT);
  }

  private Map<String, Map<String, List<TextColor>>> immutableThemes(
      final Map<String, Map<String, List<TextColor>>> themes
  ) {
    return Map.copyOf(themes);
  }

  private record ThemeSnapshot(
      Map<String, Map<String, List<TextColor>>> themes,
      TagResolver resolver
  ) {
  }
}
