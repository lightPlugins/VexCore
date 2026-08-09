package dev.vexsoft.core.common.service.placeholder;

import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.placeholder.PlaceholderService;
import java.util.Objects;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;

/** Resolves Vex placeholders in Adventure component text nodes. */
@UtilityClass
public final class PlaceholderComponents {

  private static final Pattern TOKEN = Pattern.compile("%[A-Za-z0-9_]+%");

  /** Resolves text tokens without flattening component styling. */
  public static Component resolve(
      final PlaceholderService placeholders,
      final VexPlayer player,
      final Component component
  ) {
    PlaceholderService checkedPlaceholders = Objects.requireNonNull(placeholders, "placeholders");
    VexPlayer checkedPlayer = Objects.requireNonNull(player, "player");
    return Objects.requireNonNull(component, "component").replaceText(
        TextReplacementConfig.builder()
            .match(TOKEN)
            .replacement((match, builder) -> {
              String source = match.group();
              String resolved = checkedPlaceholders.resolve(checkedPlayer, source);
              return source.equals(resolved) ? builder : Component.text(resolved);
            })
            .build()
    );
  }
}
