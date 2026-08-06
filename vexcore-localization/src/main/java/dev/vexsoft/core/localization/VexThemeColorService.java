package dev.vexsoft.core.localization;

import dev.vexsoft.core.api.configuration.ConfigurationService;
import dev.vexsoft.core.api.configuration.VexConfiguration;
import dev.vexsoft.core.api.service.Dependencies;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import dev.vexsoft.core.api.theme.ThemeColorService;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

@Dependencies(ConfigurationService.class)
public final class VexThemeColorService implements ThemeColorService {

  private static final Pattern COLOR_NAME = Pattern.compile("[a-z][a-z0-9_-]*");
  private static final Set<String> RESERVED_NAMES = Set.of(
      "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple",
      "gold", "gray", "dark_gray", "blue", "green", "aqua", "red", "light_purple",
      "yellow", "white", "color", "colour", "gradient", "rainbow", "reset",
      "bold", "b", "italic", "em", "i", "underlined", "u", "strikethrough", "st",
      "obfuscated", "obf", "font", "click", "hover", "insertion", "newline", "br"
  );

  private final MiniMessage miniMessage = MiniMessage.miniMessage();
  private final VexConfiguration configuration;
  private volatile ThemeSnapshot snapshot;

  public VexThemeColorService(final VexServiceRegistry services) {
    configuration = Objects.requireNonNull(services, "services")
        .require(ConfigurationService.class)
        .load("theme-colors.yml");
    snapshot = parse(configuration);
  }

  @Override
  public Optional<TextColor> findColor(final String name) {
    return Optional.ofNullable(snapshot.colors().get(normalize(name)));
  }

  @Override
  public TextColor requireColor(final String name) {
    String normalized = normalize(name);
    TextColor color = snapshot.colors().get(normalized);
    if (color == null) {
      throw new IllegalArgumentException("Unknown theme color: " + normalized);
    }
    return color;
  }

  @Override
  public Map<String, TextColor> getColors() {
    return snapshot.colors();
  }

  @Override
  public Component deserialize(final String input) {
    return miniMessage.deserialize(Objects.requireNonNull(input, "input"), snapshot.resolver());
  }

  @Override
  public void reload() {
    configuration.reload();
    ThemeSnapshot reloaded = parse(configuration);
    snapshot = reloaded;
  }

  private ThemeSnapshot parse(final VexConfiguration source) {
    Map<String, TextColor> colors = new LinkedHashMap<>();
    TagResolver.Builder tags = TagResolver.builder();
    for (String rawName : source.getKeys(false)) {
      String name = normalize(rawName);
      validateName(name);
      String rawColor = source.getString(rawName);
      TextColor color = parseColor(name, rawColor);
      colors.put(name, color);
      tags.tag(name, Tag.styling(color));
    }
    if (colors.isEmpty()) {
      throw new IllegalArgumentException("theme-colors.yml must define at least one color");
    }
    return new ThemeSnapshot(Map.copyOf(colors), tags.build());
  }

  private TextColor parseColor(final String name, final String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Theme color '" + name + "' must have a value");
    }
    String normalized = value.trim().toLowerCase(Locale.ROOT);
    TextColor color = normalized.startsWith("#")
        ? TextColor.fromHexString(normalized)
        : NamedTextColor.NAMES.value(normalized);
    if (color == null || normalized.startsWith("#") && !normalized.matches("#[0-9a-f]{6}")) {
      throw new IllegalArgumentException("Invalid theme color '" + name + "': " + value);
    }
    return color;
  }

  private void validateName(final String name) {
    if (!COLOR_NAME.matcher(name).matches()) {
      throw new IllegalArgumentException("Invalid theme color name: " + name);
    }
    if (RESERVED_NAMES.contains(name)) {
      throw new IllegalArgumentException("Theme color name is reserved by MiniMessage: " + name);
    }
  }

  private String normalize(final String name) {
    return Objects.requireNonNull(name, "name").trim().toLowerCase(Locale.ROOT);
  }

  private record ThemeSnapshot(Map<String, TextColor> colors, TagResolver resolver) {
  }
}
