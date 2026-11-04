package dev.vexsoft.core.paper.module;

import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.mob.service.VexMobService;
import dev.vexsoft.core.paper.mob.service.registry.MobRegistryCoordinatorService;
import dev.vexsoft.core.paper.mob.service.registry.VexMobRegistry;
import dev.vexsoft.core.paper.mob.service.registry.VexMobRegistryCoordinatorService;
import dev.vexsoft.core.paper.mob.service.runtime.MobRuntimeCoordinatorService;
import dev.vexsoft.core.paper.mob.service.runtime.VexMobRuntimeCoordinatorService;
import dev.vexsoft.core.paper.mob.service.spawner.MobSpawnerRegistryCoordinatorService;
import dev.vexsoft.core.paper.mob.service.spawner.MobSpawnerRuntimeCoordinatorService;
import dev.vexsoft.core.paper.mob.service.spawner.VexMobSpawnerRegistry;
import dev.vexsoft.core.paper.mob.service.spawner.VexMobSpawnerRegistryCoordinatorService;
import dev.vexsoft.core.paper.mob.service.spawner.VexMobSpawnerRuntimeCoordinatorService;
import dev.vexsoft.core.paper.service.mob.MobRegistry;
import dev.vexsoft.core.paper.service.mob.MobService;
import dev.vexsoft.core.paper.service.mob.MobSpawnerRegistry;

/** Installs the non-persistent custom mob registry and runtime. */
public final class MobModule implements VexModule {

    private VexServiceRegistry services;

    @Override
    public void enable(final VexServiceRegistry registry) {
        services = registry.scoped(this);
        services.register(MobRuntimeCoordinatorService.class, VexMobRuntimeCoordinatorService.class);
        services.register(MobRegistryCoordinatorService.class, VexMobRegistryCoordinatorService.class);
        services.register(MobRegistry.class, VexMobRegistry.class);
        services.register(MobService.class, VexMobService.class);
        services.register(MobSpawnerRuntimeCoordinatorService.class, VexMobSpawnerRuntimeCoordinatorService.class);
        services.register(MobSpawnerRegistryCoordinatorService.class, VexMobSpawnerRegistryCoordinatorService.class);
        services.register(MobSpawnerRegistry.class, VexMobSpawnerRegistry.class);
        services.registerQueuedServices();
    }

    @Override
    public void start() {
        services.require(MobRuntimeCoordinatorService.class).start();
        services.require(MobSpawnerRuntimeCoordinatorService.class).start();
    }

    @Override
    public void disable() {
        if (services != null) {
            services.require(MobSpawnerRuntimeCoordinatorService.class).shutdown();
            services.require(MobRuntimeCoordinatorService.class).shutdown();
            services.unregisterOwnedServices();
        }
    }

    @Override
    public String getServiceOwnerName() {
        return "vexcore-mob-runtime";
    }
}
