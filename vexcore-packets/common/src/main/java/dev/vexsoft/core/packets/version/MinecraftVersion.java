package dev.vexsoft.core.packets.version;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/** Comparable, normalized Minecraft version composed of at least two numeric parts. */
@Getter
@EqualsAndHashCode
public final class MinecraftVersion implements Comparable<MinecraftVersion> {

  private static final Pattern NUMERIC_PART = Pattern.compile("[0-9]+");

  private final List<Integer> parts;
  private final String value;

  private MinecraftVersion(final List<Integer> parts) {
    this.parts = List.copyOf(parts);
    this.value = String.join(".", parts.stream().map(String::valueOf).toList());
  }

  /** Creates a version from any number of numeric components */
  public static MinecraftVersion of(final String value) {
    String checked = Objects.requireNonNull(value, "value").trim();
    String[] rawParts = checked.split("\\.", -1);
    if (rawParts.length < 2) {
      throw new IllegalArgumentException("Minecraft version requires at least two components: " + value);
    }
    List<Integer> parts = new ArrayList<>(rawParts.length);
    for (String rawPart : rawParts) {
      if (!NUMERIC_PART.matcher(rawPart).matches()) {
        throw new IllegalArgumentException("Invalid Minecraft version: " + value);
      }
      try {
        parts.add(Integer.parseInt(rawPart));
      } catch (NumberFormatException exception) {
        throw new IllegalArgumentException("Invalid Minecraft version: " + value, exception);
      }
    }
    while (parts.size() > 2 && parts.getLast() == 0) {
      parts.removeLast();
    }
    return new MinecraftVersion(parts);
  }

  /** Returns the numeric component at the requested position */
  public int getPart(final int index) {
    return index < parts.size() ? parts.get(index) : 0;
  }

  @Override
  public int compareTo(final MinecraftVersion other) {
    Objects.requireNonNull(other, "other");
    int length = Math.max(parts.size(), other.parts.size());
    for (int index = 0; index < length; index++) {
      int comparison = Integer.compare(getPart(index), other.getPart(index));
      if (comparison != 0) {
        return comparison;
      }
    }
    return 0;
  }

  @Override
  public String toString() {
    return value;
  }
}
