package dev.vexsoft.core.api.localization;

import java.util.List;
import java.util.Objects;
import lombok.Getter;
import net.kyori.adventure.text.Component;

public final class LocalizedMessage {

  @Getter
  private final List<Component> lines;
  @Getter
  private final boolean list;

  private LocalizedMessage(final List<Component> lines, final boolean list) {
    if (lines.isEmpty()) {
      throw new IllegalArgumentException("Localized message must contain at least one line");
    }
    this.lines = List.copyOf(lines);
    this.list = list;
  }

  /** Creates a localized message containing one component */
  public static LocalizedMessage single(final Component component) {
    return new LocalizedMessage(List.of(Objects.requireNonNull(component, "component")), false);
  }

  /** Creates a localized message containing a component list */
  public static LocalizedMessage list(final List<Component> components) {
    return new LocalizedMessage(Objects.requireNonNull(components, "components"), true);
  }

  /** Returns the single component or fails when this message came from a list */
  public Component getComponent() {
    if (list) {
      throw new IllegalStateException("Localized message contains a component list");
    }
    return lines.getFirst();
  }

  /** Returns every component produced by this message */
  public List<Component> getComponents() {
    return lines;
  }
}
