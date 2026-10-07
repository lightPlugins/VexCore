package dev.vexsoft.core.paper.nms.position;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;

/** Checks local floors, roofs, fluids and loaded-chunk boundaries for flight positions. */
final class FlightPositionSafetyTest {

    @Test
    void usesTheSlabSurfaceBelowTheCarrierAndIgnoresTheRoofAboveIt() {
        World world = world(true, false);
        assertEquals(65.0D, FlightPositionSafety.minimumY(new Location(world, 0.5, 64, 0.5), 1.5));
        assertEquals(65.0D, FlightPositionSafety.minimumY(new Location(world, 0.5, 65, 0.5), 1.5));
        assertEquals(68.0D, FlightPositionSafety.minimumY(new Location(world, 0.5, 68, 0.5), 1.5),
            "A carrier already above the minimum must keep its current height");
    }

    @Test
    void waterUsesItsSurfaceAndUnloadedColumnsAreNeverRead() {
        assertEquals(65.5D, FlightPositionSafety.minimumY(new Location(world(true, true), 0.5, 64, 0.5), 1.5));
        assertTrue(Double.isNaN(FlightPositionSafety.minimumY(new Location(world(false, false), 0.5, 64, 0.5), 1.5)));
    }

    @Test
    void recoveryRequiresClearanceForTheEntireCarrierAndStaysInsideWorldBounds() {
        World world = world(true, false);
        assertTrue(FlightPositionSafety.isClear(new Location(world, 0.5, 65.75, 0.5), 0.7, 0.6));
        assertFalse(FlightPositionSafety.isClear(new Location(world, 0.5, 69.75, 0.5), 0.7, 0.6),
            "A clear feet block does not permit the carrier's head to intersect the roof");
        assertFalse(FlightPositionSafety.isClear(new Location(world, 0.5, 319.75, 0.5), 0.7, 0.6));
        assertFalse(FlightPositionSafety.isClear(new Location(world(false, false), 0.5, 65, 0.5), 0.7, 0.6));
        assertFalse(FlightPositionSafety.isClear(new Location(world(true, true), 0.5, 63.5, 0.5), 0.7, 0.6));
    }

    private static World world(final boolean loaded, final boolean water) {
        return proxy(World.class, (instance, method, arguments) -> switch (method.getName()) {
            case "isChunkLoaded" -> loaded;
            case "getMinHeight" -> -64;
            case "getMaxHeight" -> 320;
            case "getBlockAt" -> {
                assertTrue(loaded, "Flight checks must never read an unloaded chunk");
                int x = (int) arguments[0];
                int y = (int) arguments[1];
                int z = (int) arguments[2];
                yield proxy(Block.class, (block, call, values) -> switch (call.getName()) {
                    case "isLiquid" -> water && y == 63;
                    case "isPassable" -> y != 63 && y != 70 || water && y == 63;
                    case "getBoundingBox" -> new BoundingBox(x, y, z, x + 1, y + (y == 63 ? 0.5 : 1), z + 1);
                    default -> throw new AssertionError(call.getName());
                });
            }
            default -> throw new AssertionError(method.getName());
        });
    }

    private static <T> T proxy(final Class<T> type, final InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
