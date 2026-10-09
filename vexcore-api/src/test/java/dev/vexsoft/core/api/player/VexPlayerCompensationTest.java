package dev.vexsoft.core.api.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Checks external compensation order and its isolation from committed notifications. */
final class VexPlayerCompensationTest {

    @Test
    void failedOperationsCompensateInReverseOrderAndDiscardAnnouncements() {
        var player = new VexPlayer(UUID.randomUUID(), "Alex");
        List<String> effects = new ArrayList<>();
        assertFalse(player.atomic(value -> value, () -> {
            player.afterRollback(() -> effects.add("second-deposit"));
            player.afterRollback(() -> effects.add("first-deposit"));
            player.afterCommit(() -> effects.add("announcement"));
            return false;
        }));
        assertEquals(List.of("first-deposit", "second-deposit"), effects);
        assertTrue(player.atomic(value -> value, () -> {
            player.afterRollback(() -> effects.add("unexpected-refund"));
            player.afterCommit(() -> effects.add("announcement"));
            return true;
        }));
        assertEquals(List.of("first-deposit", "second-deposit", "announcement"), effects);
    }
}
