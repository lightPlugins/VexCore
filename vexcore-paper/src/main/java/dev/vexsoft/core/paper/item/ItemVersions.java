package dev.vexsoft.core.paper.item;

import dev.vexsoft.core.api.service.VexServiceRegistry;
import dev.vexsoft.core.item.v26_2.V26_2ItemVersionDefinition;
import dev.vexsoft.core.item.version.ItemVersionDefinition;
import lombok.experimental.UtilityClass;
import org.bukkit.Bukkit;

@UtilityClass
public class ItemVersions {

  public static ItemVersionDefinition select(final VexServiceRegistry services) {
    VexItemVersionRegistry versions = new VexItemVersionRegistry(services);
    versions.register(V26_2ItemVersionDefinition.class);
    return versions.require(Bukkit.getMinecraftVersion());
  }
}
