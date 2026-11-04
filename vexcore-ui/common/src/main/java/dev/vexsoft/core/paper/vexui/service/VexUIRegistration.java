package dev.vexsoft.core.paper.vexui.service;

import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.vexui.VexUIService;
import dev.vexsoft.core.paper.vexui.debug.VexUIDebugShop;
import dev.vexsoft.core.paper.vexui.debug.VexUIDebugShopService;
import dev.vexsoft.core.paper.vexui.dialog.VexUIDialogService;
import dev.vexsoft.core.paper.vexui.dialog.VexUIPaperDialogService;
import dev.vexsoft.core.paper.vexui.session.VexUICoordinatorService;
import dev.vexsoft.core.paper.vexui.session.VexUISessionCoordinator;

/** Shared production registrations used by Core and dependent plugin scopes. */
public final class VexUIRegistration {

    private VexUIRegistration() {
    }

    /** Queues the global viewer coordinator and Paper transport before the Core-owned public service. */
    public static void core(final VexServiceRegistry services) {
        services.register(VexUICoordinatorService.class, VexUISessionCoordinator.class);
        services.register(VexUIDialogService.class, VexUIPaperDialogService.class);
        owner(services);
        services.register(VexUIDebugShopService.class, VexUIDebugShop.class);
    }

    /** Queues an owner-local facade that inherits Core's coordinator and transport. */
    public static void owner(final VexServiceRegistry services) {
        services.register(VexUIService.class, VexUIRuntimeService.class);
    }
}
