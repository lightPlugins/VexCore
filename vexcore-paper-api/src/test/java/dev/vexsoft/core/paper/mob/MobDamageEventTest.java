package dev.vexsoft.core.paper.mob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.paper.mob.event.MobDamageEvent;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;

final class MobDamageEventTest {

    @Test
    void customDamageUsesPreResetChargeAndRejectsInvalidValues() {
        MobSnapshot snapshot = new MobSnapshot(
            new MobHandle(UUID.randomUUID(), MobKey.of("test", "fish")),
            UUID.randomUUID(),
            MobScope.global(),
            new Location(null, 0.0D, 0.0D, 0.0D),
            20.0D,
            20.0D,
            1.0D,
            0.2D,
            180.0D,
            Optional.empty()
        );

        assertEquals(1.0D, new MobDamageEvent(snapshot, null, 10.0D).getAttackChargeMultiplier());
        assertEquals(0.2D, new MobDamageEvent(snapshot, null, 10.0D, 0.0D).getAttackChargeMultiplier(), 0.0001D);

        MobDamageEvent event = new MobDamageEvent(snapshot, null, 10.0D, 0.5D);

        assertEquals(0.4D, event.getAttackChargeMultiplier(), 0.0001D);
        assertThrows(IllegalArgumentException.class, () -> event.setDamage(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new MobDamageEvent(snapshot, null, 10.0D, 2.0D));

        event.setDamage(0.0D);
        event.setCancelled(true);

        assertEquals(0.0D, event.getDamage());
        assertTrue(event.isCancelled());
    }
}
