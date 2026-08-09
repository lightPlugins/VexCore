package dev.vexsoft.core.paper.items.version;

import dev.vexsoft.core.paper.service.items.ItemComponentAdapterService;
import java.util.Set;

/**
 * Declares the item component adapter used by compatible Minecraft versions
 */
public interface ItemVersionDefinition {

  /** Returns the base Minecraft version represented by this definition */
  String getAdapterVersion();

  /** Returns every Minecraft version explicitly supported by this definition */
  Set<String> getSupportedVersions();

  /** Returns the version-specific item component adapter */
  Class<? extends ItemComponentAdapterService> getComponentAdapter();
}
