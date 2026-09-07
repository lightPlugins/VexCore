package dev.vexsoft.core.paper.service.packets;
import static org.junit.jupiter.api.Assertions.*;
import dev.vexsoft.core.api.service.registry.*;
import dev.vexsoft.core.common.service.cache.VexCacheService;
import dev.vexsoft.core.paper.packets.dummy.SkinTexture;
import com.destroystokyo.paper.profile.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

public final class VexSkinServiceTest {
  @Test public void existingSignedProfileAvoidsNetworkAndUpdatesCache() {
    var f=new Fixture();f.properties=Set.of(new ProfileProperty("textures","skin","signature"));
    assertEquals("signature",f.service.resolve(f.player).join().orElseThrow().signature());
    f.properties=Set.of();assertEquals("skin",f.service.resolve(f.player).join().orElseThrow().value());
    assertEquals(0,f.loads);f.service.close();
  }
  @Test public void concurrentMissesShareLoadAndCallerCancellationIsIsolated() {
    var f=new Fixture();var first=f.service.resolve(f.player);var second=f.service.resolve(f.player);
    assertEquals(1,f.loads);first.cancel(false);assertFalse(f.pending.isCancelled());
    f.pending.complete(Optional.of(new SkinTexture("skin",null)));
    assertTrue(second.join().isPresent());assertTrue(f.service.resolve(f.player).join().isPresent());
    assertEquals(1,f.loads);f.service.close();
  }
  @Test public void failureIsCachedAndCloseRejectsFurtherLoads() {
    var f=new Fixture();var first=f.service.resolve(f.player);f.pending.completeExceptionally(new IllegalStateException("offline"));
    assertTrue(first.join().isEmpty());assertTrue(f.service.resolve(f.player).join().isEmpty());assertEquals(1,f.loads);
    f.service.close();assertTrue(f.service.resolve(f.player).join().isEmpty());assertEquals(1,f.loads);
  }
  private static final class Fixture {
    final UUID id=UUID.randomUUID();Set<ProfileProperty> properties=Set.of();int loads;
    final CompletableFuture<Optional<SkinTexture>> pending=new CompletableFuture<>();
    final PlayerProfile profile=proxy(PlayerProfile.class,(p,m,a)->m.getName().equals("getProperties")?properties:null);
    final Player player=proxy(Player.class,(p,m,a)->switch(m.getName()) {case "getUniqueId"->id;case "getPlayerProfile"->profile;default->null;});
    final VexServiceRegistry registry=proxy(VexServiceRegistry.class,(p,m,a)->(ServiceOwner)()->"test");
    final VexSkinService service=new VexSkinService(new VexCacheService(registry),uuid->{loads++;return pending;});
  }
  @SuppressWarnings("unchecked") private static <T>T proxy(Class<T> type,InvocationHandler handler) {
    return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},handler);
  }
}
