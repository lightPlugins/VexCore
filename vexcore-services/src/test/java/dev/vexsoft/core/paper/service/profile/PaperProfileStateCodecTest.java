package dev.vexsoft.core.paper.service.profile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vexsoft.core.api.player.profile.PlayerProfileContext;
import dev.vexsoft.core.api.player.profile.ProfileDomain;
import dev.vexsoft.core.api.player.profile.ProfileSelection;
import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.paper.profile.PaperProfileParticipant;
import dev.vexsoft.core.paper.profile.PaperProfileState;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;

/** Exercises Paper 26.2's complete native layout and isolation when restoring shorter legacy snapshots. */
class PaperProfileStateCodecTest {

    @Test
    void capturesBodyAndSaddleAndAllSeventyOneUniquePossessions() throws Exception {
        var fixture = new Fixture(43);
        for (int slot = 0; slot < fixture.contents.length; slot++) {
            fixture.contents[slot] = new Stack("inventory-" + slot);
        }
        for (int slot = 0; slot < fixture.ender.length; slot++) {
            fixture.ender[slot] = new Stack("ender-" + slot);
        }
        fixture.cursor = new Stack("cursor");

        PaperProfileState captured = PaperProfileStateCodec.capture(fixture.player, participant());
        var mapper = new ObjectMapper();
        PaperProfileState decoded = mapper.readValue(mapper.writeValueAsBytes(captured), PaperProfileState.class);
        assertEquals(43, decoded.inventory().size());
        assertEquals(71, decoded.possessionIdentities().size());
        assertArrayEquals("inventory-41".getBytes(StandardCharsets.UTF_8), decoded.inventory().get(41));
        assertArrayEquals("inventory-42".getBytes(StandardCharsets.UTF_8), decoded.inventory().get(42));
        assertArrayEquals("cursor".getBytes(StandardCharsets.UTF_8), decoded.cursor());
    }

    @Test
    void legacySnapshotExplicitlyClearsBodyAndSaddleOnActivation() {
        var fixture = new Fixture(43);
        fixture.contents[41] = new Stack("old-body");
        fixture.contents[42] = new Stack("old-saddle");
        PaperProfileState legacy = empty(41);

        PaperProfileStateCodec.apply(fixture.player, legacy);

        assertEquals(43, fixture.contents.length);
        assertNull(fixture.contents[41]);
        assertNull(fixture.contents[42]);
        assertEquals(1, fixture.inventoryWrites);
    }

    @Test
    void freshSnapshotClearsEveryNativeSlot() {
        var fixture = new Fixture(43);
        fixture.contents[42] = new Stack("old-saddle");

        PaperProfileStateCodec.apply(fixture.player, PaperProfileState.empty());

        assertEquals(43, PaperProfileState.empty().inventory().size());
        assertEquals(43, fixture.contents.length);
        assertTrue(Arrays.stream(fixture.contents).allMatch(item -> item == null));
        assertEquals(1, fixture.inventoryWrites);
    }

    @Test
    void unsupportedOccupiedSlotFailsBeforeMutatingAnyPlayerState() {
        var fixture = new Fixture(41);
        var slots = IntStream.range(0, 43).mapToObj(slot -> slot == 42 ? new byte[]{1} : new byte[0]).toList();
        var state = new PaperProfileState(slots, PaperProfileState.empty().enderChest(), new byte[0], 0, 0, 0, 0, 0, 300);

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
            () -> PaperProfileStateCodec.apply(fixture.player, state));

        assertTrue(failure.getMessage().contains("42"));
        assertEquals(0, fixture.inventoryWrites);
        assertEquals(0, fixture.otherWrites);
    }

    private static PaperProfileState empty(final int inventorySize) {
        return new PaperProfileState(IntStream.range(0, inventorySize).mapToObj(slot -> new byte[0]).toList(),
            PaperProfileState.empty().enderChest(), new byte[0], 0, 0, 0, 0, 0, 300);
    }

    private static PaperProfileParticipant participant() {
        return new PaperProfileParticipant() {
            @Override
            public ProfileDomain domain() {
                return new ProfileDomain("test");
            }

            @Override
            public CompletableFuture<ProfileSelection> bootstrap(final VexPlayer player) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void prepare(final Player player, final PlayerProfileContext previous) {
            }

            @Override
            public void restore(final Player player, final PlayerProfileContext current) {
            }

            @Override
            public Location spawn(final Player player) {
                return null;
            }

            @Override
            public Optional<String> possessionIdentity(final ItemStack item) {
                return Optional.of(((Stack) item).identity);
            }
        };
    }

    private static <T> T proxy(final Class<T> type, final InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }

    private static final class Fixture {

        private ItemStack[] contents;
        private final ItemStack[] ender = new ItemStack[27];
        private ItemStack cursor;
        private int inventoryWrites;
        private int otherWrites;
        private final Player player;

        private Fixture(final int inventorySize) {
            contents = new ItemStack[inventorySize];
            var inventory = proxy(PlayerInventory.class, (object, method, arguments) -> switch (method.getName()) {
                case "getContents" -> contents;
                case "getSize" -> inventorySize;
                case "getHeldItemSlot" -> 0;
                case "setContents" -> {
                    // Model a platform that does not clear slots absent from the supplied snapshot.
                    ItemStack[] supplied = (ItemStack[]) arguments[0];
                    System.arraycopy(supplied, 0, contents, 0, supplied.length);
                    inventoryWrites++;
                    yield null;
                }
                default -> null;
            });
            var enderChest = proxy(Inventory.class, (object, method, arguments) ->
                method.getName().equals("getContents") ? ender : null);
            player = proxy(Player.class, (object, method, arguments) -> {
                if (method.getName().startsWith("set") || method.getName().equals("addPotionEffects")) {
                    otherWrites++;
                }
                return switch (method.getName()) {
                    case "getInventory" -> inventory;
                    case "getEnderChest" -> enderChest;
                    case "getItemOnCursor" -> cursor;
                    case "getLevel", "getTotalExperience", "getFireTicks" -> 0;
                    case "getRemainingAir", "getMaximumAir" -> 300;
                    case "getFoodLevel" -> 20;
                    case "getExp", "getExhaustion" -> 0F;
                    case "getSaturation" -> 20F;
                    case "getActivePotionEffects" -> List.of();
                    case "addPotionEffects" -> true;
                    default -> null;
                };
            });
        }
    }

    @RequiredArgsConstructor
    private static final class Stack extends ItemStack {

        private final String identity;

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public int getAmount() {
            return 1;
        }

        @Override
        public byte[] serializeAsBytes() {
            return identity.getBytes(StandardCharsets.UTF_8);
        }
    }
}
