package dev.vexsoft.core.gameplay.stat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.ServiceOwner;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import dev.vexsoft.core.service.DefaultServiceRegistry;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VexStatSystemTest {

  private static final StatKey STRENGTH = StatKey.of("vexitems", "strength");

  @Test
  void calculatesArrayBackedValuesAndRestoresPermanentDataAfterRegistrationReturns() {
    Fixture fixture = new Fixture();
    Stat first = fixture.stats.register(definition(STRENGTH, 10D));
    VexStatContainer container = fixture.newContainer();
    PlayerStat strength = container.getStat(first);

    strength.addPermanent(30D);
    StatModifierHandle flat = strength.addModifier(StatModifier.flat(20D));
    StatModifierHandle additive = strength.addModifier(StatModifier.additiveMultiplier(0.25D));
    StatModifierHandle total = strength.addModifier(StatModifier.totalMultiplier(1.10D));

    assertEquals(82.5D, strength.getValue(), 0.000_001D);
    assertEquals(30D, strength.getPermanent());
    assertEquals(30D, fixture.player.require(GameplayPlayerData.STATS)
        .getPermanentValues().get(STRENGTH.toString()));

    assertTrue(fixture.stats.unregister(STRENGTH));
    assertFalse(first.isRegistered());
    assertFalse(flat.isActive());
    assertFalse(additive.isActive());
    assertFalse(total.isActive());
    assertThrows(IllegalStateException.class, strength::getValue);

    Stat second = fixture.stats.register(definition(STRENGTH, 10D));
    PlayerStat restored = container.getStat(second);

    assertNotSame(first, second);
    assertEquals(first.getRuntimeId(), second.getRuntimeId());
    assertEquals(30D, restored.getPermanent());
    assertEquals(40D, restored.getValue());
  }

  @Test
  void synchronizesDynamicDefinitionsAndBatchesRecalculation() {
    Fixture fixture = new Fixture();
    VexStatContainer container = fixture.newContainer();
    Stat strength = fixture.stats.synchronize(List.of(definition(STRENGTH, 5D))).getFirst();
    PlayerStat playerStat = container.getStat(strength);

    try (StatUpdateBatch ignored = container.beginUpdate()) {
      playerStat.addPermanent(10D);
      playerStat.addPermanent(15D);
      assertEquals(5D, playerStat.getValue());
    }

    assertEquals(30D, playerStat.getValue());
    fixture.stats.synchronize(List.of());
    assertTrue(fixture.stats.find(STRENGTH).isEmpty());
    assertTrue(container.findStat(strength).isEmpty());
  }

  private static StatDefinition definition(final StatKey key, final double defaultValue) {
    return StatDefinition.builder(key)
        .defaultValue(defaultValue)
        .minimum(0D)
        .build();
  }

  private static final class Fixture {

    private final VexServiceRegistry services;
    private final StatRegistry stats;
    private final VexPlayer player = new VexPlayer(UUID.randomUUID(), "Alex");

    private Fixture() {
      DefaultServiceRegistry root = new DefaultServiceRegistry();
      services = root.scoped(new Owner());
      services.register(
          StatRegistryCoordinatorService.class,
          VexStatRegistryCoordinatorService.class
      );
      services.register(StatRegistry.class, VexStatRegistry.class);
      services.registerQueuedServices();
      stats = services.require(StatRegistry.class);
      player.install(GameplayPlayerData.STATS, new StatData());
    }

    private VexStatContainer newContainer() {
      return new VexStatContainer(
          player,
          services.require(StatRegistryCoordinatorService.class)
      );
    }
  }

  private static final class Owner implements ServiceOwner {

    @Override
    public String getServiceOwnerName() {
      return "VexItems";
    }
  }
}
