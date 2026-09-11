package dev.vexsoft.core.paper.service.platform;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.platform.ServerPlatform;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

/** Identifies the running server platform and checks region and entity thread ownership. */
@Dependencies
public final class VexPlatformService implements PlatformService {

    public VexPlatformService(final VexServiceRegistry services) {
        Objects.requireNonNull(services, "services");
    }

    @Override
    public ServerPlatform getPlatform() {
        return PlatformDetector.getPlatform();
    }

    @Override
    public boolean isFolia() {
        return PlatformDetector.isFolia();
    }

    @Override
    public boolean isOwnedByCurrentRegion(final Location location) {
        return Bukkit.isOwnedByCurrentRegion(Objects.requireNonNull(location, "location"));
    }

    @Override
    public boolean isOwnedByCurrentRegion(final Entity entity) {
        return Bukkit.isOwnedByCurrentRegion(Objects.requireNonNull(entity, "entity"));
    }

    @Override
    public boolean isGlobalTickThread() {
        return Bukkit.isGlobalTickThread();
    }
}
