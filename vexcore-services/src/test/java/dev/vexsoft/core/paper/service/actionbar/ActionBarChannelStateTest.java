package dev.vexsoft.core.paper.service.actionbar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.api.service.registry.ServiceOwner;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

final class ActionBarChannelStateTest {

    private static final long NOW = 1_000L;

    @Test
    void suppressionHidesAllLayersAndRestoresCurrentStateAfterOwnerCleanup() {
        ActionBarChannelState state = new ActionBarChannelState();
        ServiceOwner hud = owner("hud");
        ServiceOwner dialogue = owner("dialogue");
        ServiceOwner menu = owner("menu");

        state.setPersistent(hud, "status", Component.text("old"), 0, 1);
        state.setSuppressed(dialogue, "quest", true);
        state.showTemporary(hud, "reward", Component.text("reward"), Integer.MAX_VALUE, 2, NOW + 10);
        state.setPersistent(hud, "status", Component.text("updated"), 0, 3);

        assertEquals(Component.empty(), state.select(NOW).selected().orElseThrow());

        state.setSuppressed(menu, "screen", true);
        state.setSuppressed(dialogue, "quest", false);

        assertEquals(Component.empty(), state.select(NOW + 20).selected().orElseThrow());

        state.clear(menu);

        assertEquals(Component.text("updated"), state.select(NOW + 20).selected().orElseThrow());

        state.setSuppressed(dialogue, "quest", true);
        state.clear(dialogue);

        assertEquals(Component.text("updated"), state.select(NOW + 20).selected().orElseThrow());
    }

    @Test
    void temporaryLineOverridesAndFallsBackToPersistentLine() {
        ActionBarChannelState state = new ActionBarChannelState();
        ServiceOwner owner = owner("monolith");

        state.setPersistent(owner, "status", Component.text("status"), 100, 1);
        state.showTemporary(owner, "reward", Component.text("reward"), 0, 2, NOW + 100);

        assertEquals(Component.text("reward"), state.select(NOW).selected().orElseThrow());
        assertEquals(Component.text("status"), state.select(NOW + 100).selected().orElseThrow());
    }

    @Test
    void priorityWinsAndNewestUpdateBreaksTies() {
        ActionBarChannelState state = new ActionBarChannelState();
        ServiceOwner owner = owner("monolith");

        state.setPersistent(owner, "first", Component.text("first"), 10, 1);
        state.setPersistent(owner, "second", Component.text("second"), 20, 2);
        state.setPersistent(owner, "third", Component.text("third"), 20, 3);

        assertEquals(Component.text("third"), state.select(NOW).selected().orElseThrow());
    }

    @Test
    void clearingOneOwnerKeepsOtherOwnerChannels() {
        ActionBarChannelState state = new ActionBarChannelState();
        ServiceOwner first = owner("first");
        ServiceOwner second = owner("second");

        state.setPersistent(first, "status", Component.text("first"), 100, 1);
        state.setPersistent(second, "status", Component.text("second"), 0, 2);

        state.clear(first);

        assertEquals(Component.text("second"), state.select(NOW).selected().orElseThrow());

        state.clear(second);

        assertTrue(state.select(NOW).selected().isEmpty());
    }

    private static ServiceOwner owner(final String name) {
        return () -> name;
    }
}
