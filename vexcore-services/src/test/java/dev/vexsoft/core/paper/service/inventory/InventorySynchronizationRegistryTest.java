package dev.vexsoft.core.paper.service.inventory;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexClassFactory;
import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.common.service.registry.DefaultServiceRegistry;
import dev.vexsoft.core.paper.packets.service.PacketTransportAdapterService;
import dev.vexsoft.core.paper.service.scheduler.ScheduleService;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;
import org.bukkit.plugin.Plugin;

/** Checks that plugin-scoped inventory services resolve Core's shared packet transport through the real registry. */
final class InventorySynchronizationRegistryTest {

    @Test
    void managedInventoryAndListenerResolveTheSharedTransportBeforeUse() throws Exception {
        var registry = new DefaultServiceRegistry();
        ServiceOwner core = () -> "VexCore";
        var register = DefaultServiceRegistry.class.getDeclaredMethod("register", ServiceOwner.class,
            Class.class, VexService.class);
        register.setAccessible(true);
        Runnable synchronize = () -> {
        };
        for (Class<?> type : new Class<?>[]{ScheduleService.class, PacketTransportAdapterService.class}) {
            Object dependency = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (instance, method, arguments) -> {
                    if (method.getName().equals("deferInventorySynchronization")) {
                        return synchronize;
                    }
                    throw new AssertionError("Constructors must only resolve dependencies: " + method.getName());
                });
            register.invoke(registry, core, type, dependency);
        }
        var owner = (ServiceOwner) Proxy.newProxyInstance(Plugin.class.getClassLoader(),
            new Class<?>[]{Plugin.class, ServiceOwner.class}, (instance, method, arguments) -> "VexIslesTest");
        var services = registry.scoped(owner);
        services.register(InventoryService.class, VexInventoryService.class);
        services.registerQueuedServices();

        var inventories = assertInstanceOf(VexInventoryService.class, services.require(InventoryService.class));
        assertSame(synchronize, inventories.deferSynchronization(null));
        assertInstanceOf(VexInventoryListener.class,
            VexClassFactory.create(VexInventoryListener.class, services, "Inventory listener"));
        services.unregisterOwnedServices();
    }
}
