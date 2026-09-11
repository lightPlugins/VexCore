package dev.vexsoft.core.paper.module;

import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.nms.NmsVersions;
import dev.vexsoft.core.paper.nms.service.NmsMobAdapterService;
import dev.vexsoft.core.paper.nms.version.NmsVersionDefinition;
import org.bukkit.plugin.Plugin;

/** Installs version-specific native entity control. */
public final class NmsModule implements VexModule {

    private final Plugin plugin;
    private VexServiceRegistry services;
    private NmsVersionDefinition definition;

    public NmsModule(final Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void enable(final VexServiceRegistry registry) {
        services = registry.scoped(this);
        definition = NmsVersions.select();
        services.register(NmsMobAdapterService.class, definition.getMobAdapter());
        services.registerQueuedServices();
    }

    @Override
    public void start() {
        plugin.getLogger()
            .info("NMS support for Minecraft " + definition.getAdapterVersion() + " started successfully");
    }

    @Override
    public void disable() {
        if (services != null) {
            services.unregisterOwnedServices();
        }
    }

    @Override
    public String getServiceOwnerName() {
        return "vexcore-nms";
    }
}
