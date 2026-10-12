package dev.vexsoft.core.paper.service.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vexsoft.core.paper.profile.PaperProfileState;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Verifies portable snapshots and wall-clock expiry without accessing Bukkit state. */
class PaperProfileStateTest {

    @Test
    void savedEffectsExpireWhileInfiniteEffectsRemainInfinite() {
        var empty = PaperProfileState.empty();
        var state = new PaperProfileState(empty.inventory(), empty.enderChest(), empty.cursor(), 0, 0, 0, 0,
            0, 300, 20, 5, 0, List.of(), List.of(), 1_000, null);
        var timed = new PaperProfileState.Effect("minecraft:speed", 100, 0, false, true, true);
        var infinite = new PaperProfileState.Effect("minecraft:speed", -1, 0, false, true, true);
        assertEquals(60, state.remainingDuration(timed, 3_000));
        assertEquals(0, state.remainingDuration(timed, 10_000));
        assertEquals(100, state.remainingDuration(timed, 0));
        assertEquals(-1, state.remainingDuration(infinite, 10_000));
    }

    @Test
    void oldSnapshotsAndWorldKeyPositionsRemainJacksonRoundTripSafe() throws Exception {
        var mapper = new ObjectMapper();
        var empty = PaperProfileState.empty();
        var legacyState = new PaperProfileState(empty.inventory().subList(0, 41), empty.enderChest(), empty.cursor(),
            0, 0, 0, 0, 0, 300);
        var legacy = mapper.valueToTree(legacyState);
        ((ObjectNode) legacy).remove(List.of("capturedAt", "position", "possessionIdentities"));
        var decoded = mapper.treeToValue(legacy, PaperProfileState.class);
        assertEquals(41, decoded.inventory().size());
        assertEquals(List.of(), decoded.possessionIdentities());
        assertNull(decoded.position());
        var position = new PaperProfileState.Position("game:isles", 1.5, 80, 2.5, 45, 0);
        assertEquals(position, mapper.readValue(mapper.writeValueAsBytes(position), PaperProfileState.Position.class));
        var oldPosition = (ObjectNode) mapper.valueToTree(position);
        oldPosition.remove("server");
        assertEquals("", mapper.treeToValue(oldPosition, PaperProfileState.Position.class).server());
        var networkPosition = new PaperProfileState.Position("game:isles", 1.5, 80, 2.5, 45, 0, "isles-2");
        assertEquals(networkPosition, mapper.readValue(mapper.writeValueAsBytes(networkPosition), PaperProfileState.Position.class));
    }
}
