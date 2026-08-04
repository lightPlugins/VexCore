package dev.vexsoft.core.item.version;

import dev.vexsoft.core.item.internal.ItemComponentAdapterService;
import java.util.Set;

/**
 * Declares the item component adapter used by compatible Minecraft versions
 */
public interface ItemVersionDefinition {

  /** Returns the base Minecraft version represented by this definition */
  public String getAdapterVersion();

  /** Returns every Minecraft version explicitly supported by this definition */
  public Set<String> getSupportedVersions();

  /** Returns the version-specific item component adapter */
  public Class<? extends ItemComponentAdapterService> getComponentAdapter();
}
