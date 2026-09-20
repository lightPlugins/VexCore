package dev.vexsoft.core.paper.service.interactiveui;

import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.paper.interactiveui.InteractiveUi;
import dev.vexsoft.core.paper.interactiveui.InteractiveUiElement;
import dev.vexsoft.core.paper.interactiveui.InteractiveUiInput;
import dev.vexsoft.core.paper.interactiveui.InteractiveUiState;
import dev.vexsoft.core.paper.packets.interactiveui.InteractiveUiPacketTransport;
import dev.vexsoft.core.paper.scheduler.VexTask;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.entity.Player;

/** Entity-thread canvas state, synchronized against shutdown from another region. */
public final class VexInteractiveUi implements InteractiveUi {

    final VexInteractiveUiCoordinatorService coordinator;
    final ServiceOwner owner;
    final Player player;
    final UUID viewerId;
    final String id;
    final InteractiveUiInputBuffer inputs = new InteractiveUiInputBuffer(true);
    final LinkedHashMap<String, InteractiveUiElement> elements = new LinkedHashMap<>();
    final HashMap<String, Object> elementIdentities = new HashMap<>();
    final InteractiveUiPointer pointer = new InteractiveUiPointer(0, 0);
    final Object cursorLock = new Object();
    volatile boolean closed;
    boolean initializing = true;
    volatile InteractiveUiState snapshot = pointer.state();
    volatile InteractiveUiPacketTransport transport;
    volatile RuntimeException cursorFailure;
    VexTask task;
    Consumer<InteractiveUiInput> callback = ignored -> { };
    boolean dirty = true;
    boolean cursorReady;
    double renderedX = Double.NaN;
    double renderedY = Double.NaN;

    VexInteractiveUi(VexInteractiveUiCoordinatorService coordinator, ServiceOwner owner, Player player, String id) {
        this.coordinator = coordinator;
        this.owner = owner;
        this.player = player;
        this.viewerId = player.getUniqueId();
        this.id = id;
    }

    @Override
    public synchronized void put(InteractiveUiElement element) {
        requireMutable();
        Objects.requireNonNull(element, "element");
        if (!elements.containsKey(element.id()) && elements.size() >= 128) {
            throw new IllegalStateException("At most 128 interactive elements are supported");
        }
        if (element.equals(elements.get(element.id()))) {
            return;
        }
        LinkedHashMap<String, InteractiveUiElement> candidate = new LinkedHashMap<>(elements);

        candidate.put(element.id(), element);
        InteractiveUiRenderer.validateScene(List.copyOf(candidate.values()));
        elementIdentities.computeIfAbsent(element.id(), ignored -> new Object());
        if (!element.interactive()) {
            pointer.cancelCapture(element.id());
            elementIdentities.put(element.id(), new Object());
        }
        elements.put(element.id(), element);
        dirty = true;
    }

    @Override
    public synchronized void remove(String elementId) {
        requireMutable();
        if (elements.remove(elementId) != null) {
            elementIdentities.remove(elementId);
            pointer.cancelCapture(elementId);
            dirty = true;
        }
    }

    @Override
    public synchronized void onInput(Consumer<InteractiveUiInput> handler) {
        requireMutable();
        callback = Objects.requireNonNull(handler, "handler");
    }

    @Override
    public InteractiveUiState state() {
        return snapshot;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        if (!closed) {
            requireThread();
            coordinator.retire(this);
        }
    }

    private void requireMutable() {
        if (closed) {
            throw new IllegalStateException("Interactive UI is closed");
        }
        requireThread();
    }

    private void requireThread() {
        if (!player.getServer().isOwnedByCurrentRegion(player)) {
            throw new IllegalStateException("Interactive UI mutations require the viewer's entity scheduler");
        }
    }
}
