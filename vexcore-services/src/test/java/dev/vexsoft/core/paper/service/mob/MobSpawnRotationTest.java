package dev.vexsoft.core.paper.service.mob;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import org.bukkit.Location;
import org.bukkit.entity.Mob;
import org.junit.jupiter.api.Test;

/** Reproduces Paper's separate head/body rotation before a model snapshots its carrier. */
public final class MobSpawnRotationTest {

    @Test
    void modelsReceiveConfiguredBodyYawAndHeadPitchInsteadOfTheDefaultBodyDirection() {
        for (float yaw : new float[]{-90.0F, -133.5F, 0.0F, 90.0F, 180.0F}) {
            for (float pitch : new float[]{-40.0F, 0.0F, 35.0F}) {
                var location = new Location(null, -5, 100, 327.5, yaw, pitch);
                var headRotation = new Location(null, -5, 100, 327.5);
                var bodyYaw = new float[]{0.0F};
                Mob carrier = (Mob) Proxy.newProxyInstance(Mob.class.getClassLoader(), new Class<?>[]{Mob.class},
                    (instance, method, arguments) -> switch (method.getName()) {
                        case "setRotation" -> {
                            // CraftEntity.setRotation updates the entity/head, but not LivingEntity.yBodyRot.
                            headRotation.setYaw((float) arguments[0]);
                            headRotation.setPitch((float) arguments[1]);
                            yield null;
                        }
                        case "setBodyYaw" -> {
                            bodyYaw[0] = (float) arguments[0];
                            yield null;
                        }
                        default -> throw new AssertionError("Unexpected rotation side effect: " + method.getName());
                    });

                VexMobRuntimeCoordinatorService.initializeRotation(carrier, location);

                assertEquals(yaw, headRotation.getYaw());
                assertEquals(yaw, bodyYaw[0], "Model Engine must see the configured body yaw at attachment time");
                assertEquals(pitch, headRotation.getPitch());
                assertEquals(new Location(null, -5, 100, 327.5, yaw, pitch), location);
            }
        }
    }
}
