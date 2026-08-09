package dev.vexsoft.core.paper.module;

import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.service.items.ItemComponentAdapterService;
import dev.vexsoft.core.paper.items.version.ItemVersionDefinition;
import dev.vexsoft.core.paper.item.ItemVersions;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public final class ItemModule implements VexModule {

  private final Plugin plugin;
  private VexServiceRegistry services;
  private ItemVersionDefinition definition;

  public ItemModule(final Plugin plugin) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
  }

  @Override
  public void enable(final VexServiceRegistry registry) {
    services = registry.scoped(this);
    definition = ItemVersions.select(services);
    services.register(ItemComponentAdapterService.class, definition.getComponentAdapter());
    services.registerQueuedServices();
  }

  @Override
  public void start() {
    if (definition == null) {
      throw new IllegalStateException("ItemModule has not been loaded yet");
    }
    plugin.getLogger().info(
        "Item support for Minecraft " + Bukkit.getMinecraftVersion()
            + " started successfully using adapter " + definition.getAdapterVersion()
    );
  }

  @Override
  public String getServiceOwnerName() {
    return "vexcore-items";
  }
}
