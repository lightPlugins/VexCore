package dev.vexsoft.core.paper.packets.service.v26_3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import java.util.stream.StreamSupport;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import org.junit.jupiter.api.Test;

final class VexActionBarPacketSuppressionTest {

    @Test
    void suppressesDirectAndBundledActionBarsWhileKeepingClearsAndChat() {
        var adapter = new VexPacketTransportAdapterService(null);
        var text = Component.literal("HUD");
        var actionBar = new ClientboundSetActionBarTextPacket(text);
        var overlay = new ClientboundSystemChatPacket(text, true);
        var chat = new ClientboundSystemChatPacket(text, false);
        var clear = new ClientboundSetActionBarTextPacket(Component.empty());

        assertNull(adapter.suppressActionBar(actionBar));
        assertNull(adapter.suppressActionBar(overlay));
        assertSame(clear, adapter.suppressActionBar(clear));
        assertSame(chat, adapter.suppressActionBar(chat));

        var filtered = assertInstanceOf(ClientboundBundlePacket.class,
            adapter.suppressActionBar(new ClientboundBundlePacket(List.of(actionBar, clear, overlay, chat))));
        assertEquals(List.of(clear, chat), StreamSupport.stream(filtered.subPackets().spliterator(), false).toList());
    }
}
