package dev.vexsoft.core.paper.items;

import java.util.List;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.bukkit.Color;

/**
 * Contains custom model values without exposing Paper's experimental builder
 */
@Value
@Builder(toBuilder = true)
public class VexCustomModelData {
  @Singular("floatValue")
  List<Float> floatValues;
  @Singular("flagValue")
  List<Boolean> flagValues;
  @Singular("stringValue")
  List<String> stringValues;
  @Singular("colorValue")
  List<Color> colorValues;
}
