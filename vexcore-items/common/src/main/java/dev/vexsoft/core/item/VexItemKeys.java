package dev.vexsoft.core.item;

import lombok.experimental.UtilityClass;
import org.bukkit.NamespacedKey;

/**
 * Contains the persistent keys shared by every Vex item
 */
@UtilityClass
public class VexItemKeys {
  public static final NamespacedKey ITEM_ID = new NamespacedKey("vexcore", "item_id");
}
