package dev.vexsoft.core.paper.service.packets;

import static org.junit.jupiter.api.Assertions.*;

import com.destroystokyo.paper.profile.*;
import dev.vexsoft.core.api.service.registry.*;
import dev.vexsoft.core.common.service.cache.VexCacheService;
import dev.vexsoft.core.paper.packets.dummy.SkinTexture;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

public final class VexSkinServiceTest {

    @Test
    public void existingSignedProfileAvoidsNetworkAndUpdatesCache() {
        var fixture = new Fixture();

        fixture.properties = Set.of(new ProfileProperty("textures", "skin", "signature"));

        assertEquals("signature", fixture.service.resolve(fixture.player).join().orElseThrow().signature());

        fixture.properties = Set.of();

        assertEquals("skin", fixture.service.resolve(fixture.player).join().orElseThrow().value());
        assertEquals(0, fixture.loads);

        fixture.service.close();
    }

    @Test
    public void concurrentMissesShareLoadAndCallerCancellationIsIsolated() {
        var fixture = new Fixture();
        var first = fixture.service.resolve(fixture.player);
        var second = fixture.service.resolve(fixture.player);

        assertEquals(1, fixture.loads);

        first.cancel(false);

        assertFalse(fixture.pending.isCancelled());

        fixture.pending.complete(Optional.of(new SkinTexture("skin", null)));

        assertTrue(second.join().isPresent());
        assertTrue(fixture.service.resolve(fixture.player).join().isPresent());
        assertEquals(1, fixture.loads);

        fixture.service.close();
    }

    @Test
    public void failureIsCachedAndCloseRejectsFurtherLoads() {
        var fixture = new Fixture();
        var first = fixture.service.resolve(fixture.player);

        fixture.pending.completeExceptionally(new IllegalStateException("offline"));

        assertTrue(first.join().isEmpty());
        assertTrue(fixture.service.resolve(fixture.player).join().isEmpty());
        assertEquals(1, fixture.loads);

        fixture.service.close();

        assertTrue(fixture.service.resolve(fixture.player).join().isEmpty());
        assertEquals(1, fixture.loads);
    }

    private static final class Fixture {

        final UUID id = UUID.randomUUID();
        Set<ProfileProperty> properties = Set.of();
        int loads;
        final CompletableFuture<Optional<SkinTexture>> pending = new CompletableFuture<>();
        final PlayerProfile profile = proxy(
            PlayerProfile.class,
            (proxyObject, invokedMethod, arguments) -> invokedMethod.getName().equals("getProperties") ? properties
                : null
        );
        final Player player = proxy(
            Player.class,
            (proxyObject, invokedMethod, arguments) -> switch (invokedMethod.getName()) {
                case "getUniqueId" -> id;
                case "getPlayerProfile" -> profile;
                default -> null;
            }
        );
        final VexServiceRegistry registry =
            proxy(VexServiceRegistry.class, (proxyObject, invokedMethod, arguments) -> (ServiceOwner) () -> "test");
        final VexSkinService service = new VexSkinService(
            new VexCacheService(registry),
            uuid -> {
                loads++;

                return pending;
            }
        );
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }
}
