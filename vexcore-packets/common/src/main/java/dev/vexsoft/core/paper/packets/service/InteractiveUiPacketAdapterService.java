package dev.vexsoft.core.paper.packets.service;

import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.paper.packets.interactiveui.InteractiveUiPacketInput;
import dev.vexsoft.core.paper.packets.interactiveui.InteractiveUiPacketTransport;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/** Supplies version-specific camera, mounting, and input packets for interactive canvases. */
public interface InteractiveUiPacketAdapterService extends VexService {

    /** Opens a client-only transport on the player's owning thread, rolling back partial setup on failure. */
    InteractiveUiPacketTransport open(Player player);

    /** Restores client presentation, using captured packets when the player's owning thread is unavailable. */
    void close(Player player, InteractiveUiPacketTransport transport);

    /** Adds or updates the viewer-local scene presentation without accessing world state. */
    void scene(Player player, InteractiveUiPacketTransport transport, Component content);

    /** Adds or updates the independent viewer-local cursor presentation without accessing world state. */
    void cursor(Player player, InteractiveUiPacketTransport transport, Component content);

    /** Queues the latest cursor on the captured connection from any thread, after scene creation is requested. */
    void cursorImmediate(InteractiveUiPacketTransport transport, Component content);

    /** Classifies supported gameplay packets without accessing Bukkit state; unrelated networking is untouched. */
    Optional<InteractiveUiPacketInput> decode(Object packet);

    /** Preserves the viewer-local mining target in outbound block packets without reading world state. */
    Object preserveTarget(InteractiveUiPacketTransport transport, Object packet);

    /** Acknowledges a prediction sequence, using the captured session connection during off-thread shutdown. */
    void acknowledge(Player player, int sequence);
}
