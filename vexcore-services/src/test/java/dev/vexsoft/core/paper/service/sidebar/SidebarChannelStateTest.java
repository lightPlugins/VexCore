package dev.vexsoft.core.paper.service.sidebar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.vexsoft.core.api.service.registry.ServiceOwner;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

final class SidebarChannelStateTest {

    private static final long NOW = 1_000L;

    @Test
    void highestPriorityWinsAcrossPersistentAndTemporaryChannels() {
        SidebarChannelState state = new SidebarChannelState();
        ServiceOwner owner = () -> "monolith";
        SidebarFrame fallback = frame("fallback");
        SidebarFrame mining = frame("mining");

        state.setPersistent(owner, "default", fallback, 0, 1);
        state.setPersistent(owner, "mining", mining, 200, 2);
        state.showTemporary(owner, "notice", frame("notice"), 100, 3, NOW + 100);

        assertEquals(mining, state.select(NOW).frame());

        state.clearPersistent(owner, "mining");

        assertEquals(frame("notice"), state.select(NOW).frame());
        assertEquals(fallback, state.select(NOW + 100).frame());
    }

    @Test
    void clearingAnOwnerRemovesItsFallback() {
        SidebarChannelState state = new SidebarChannelState();
        ServiceOwner owner = () -> "monolith";

        state.setPersistent(owner, "default", frame("default"), 0, 1);

        state.clear(owner);

        assertNull(state.select(NOW).frame());
    }

    private static SidebarFrame frame(final String title) {
        return new SidebarFrame(Component.text(title), List.of(Component.text("line")));
    }
}
