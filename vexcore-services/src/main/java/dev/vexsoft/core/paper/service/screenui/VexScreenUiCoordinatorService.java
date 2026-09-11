package dev.vexsoft.core.paper.service.screenui;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.scheduler.VexTask;
import dev.vexsoft.core.paper.screenui.DialoguePanelLayout;
import dev.vexsoft.core.paper.screenui.PreparedDialogue;
import dev.vexsoft.core.paper.screenui.ScreenUi;
import dev.vexsoft.core.paper.screenui.TextBlockLayout;
import dev.vexsoft.core.paper.screenui.TextureLayout;
import dev.vexsoft.core.paper.screenui.UiTexture;
import dev.vexsoft.core.paper.screenui.version.ScreenUiVersionDefinition;
import dev.vexsoft.core.paper.service.scheduler.ScheduleService;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.entity.Player;

/** One shared bossbar and at most one queued render per player; no periodic idle tasks. */
@Dependencies({ScheduleService.class, ScreenUiVersionDefinition.class})
public final class VexScreenUiCoordinatorService implements ScreenUiCoordinatorService, AutoCloseable {

    private final ScheduleService schedules;
    private final ScreenUiVersionDefinition version;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private boolean closed;

    public VexScreenUiCoordinatorService(VexServiceRegistry services) {
        schedules = services.require(ScheduleService.class);
        version = services.require(ScreenUiVersionDefinition.class);
    }

    @Override
    public synchronized ScreenUi open(ServiceOwner owner, Player player, String id) {
        validId(id);
        Objects.requireNonNull(owner, "owner");

        if (closed || !player.isOnline()) {
            throw new IllegalStateException("UI is unavailable");
        }

        Session session = sessions.computeIfAbsent(player.getUniqueId(), ignored -> new Session(player));
        ScreenKey key = new ScreenKey(owner, id);
        Handle existing = session.screens.get(key);

        if (existing != null) {
            return existing;
        }

        if (session.screens.size() >= 8) {
            throw new IllegalStateException("At most eight screens per player");
        }

        Handle handle = new Handle(session, key);

        session.screens.put(key, handle);

        return handle;
    }

    @Override
    public void validateDialogueLayout(DialoguePanelLayout layout) {
        ScreenUiDialogueLayout.validate(layout, version);
    }

    @Override
    public PreparedDialogue prepareDialogue(Component speaker, List<Component> paragraphs, DialoguePanelLayout layout) {
        return ScreenUiDialogueLayout.prepare(speaker, paragraphs, layout, version);
    }

    @Override
    public synchronized Optional<ScreenUi> find(ServiceOwner owner, Player player, String id) {
        Session session = sessions.get(player.getUniqueId());

        return session == null ? Optional.empty() : Optional.ofNullable(session.screens.get(new ScreenKey(owner, id)));
    }

    @Override
    public synchronized void closeOwner(ServiceOwner owner) {
        for (Session session : List.copyOf(sessions.values())) {
            for (Handle handle : List.copyOf(session.screens.values())) {
                if (handle.key.owner() == owner) {
                    handle.close();
                }
            }
        }
    }

    @Override
    public synchronized void discard(Player player) {
        Session session = sessions.get(player.getUniqueId());

        if (session != null && session.player == player) {
            retire(session);
        }
    }

    private void retire(Session session) {
        if (!sessions.remove(session.player.getUniqueId(), session)) {
            return;
        }

        if (session.task != null) {
            session.task.cancel();
        }

        session.task = null;
        session.screens.values().forEach(handle -> {
            handle.closed = true;
            handle.elements.clear();
        });
        session.screens.clear();

        if (session.shown) {
            session.player.hideBossBar(session.bar);
        }

        session.shown = false;
    }

    @Override
    public synchronized void close() {
        closed = true;

        for (Session session : List.copyOf(sessions.values())) {
            retire(session);
        }
    }

    private void request(Session session) {
        if (session.pending) {
            return;
        }

        session.pending = true;

        try {
            var task = schedules.runForLater(
                session.player,
                2,
                () -> render(session),
                () -> {
                    synchronized (this) {
                        retire(session);
                    }
                }
            );

            if (task.isEmpty()) {
                retire(session);
            } else {
                session.task = task.get();
            }
        } catch (RuntimeException failure) {
            retire(session);
            throw failure;
        }
    }

    private synchronized void render(Session session) {
        if (closed || sessions.get(session.player.getUniqueId()) != session) {
            return;
        }

        session.pending = false;
        session.task = null;

        if (!session.player.isOnline()) {
            retire(session);

            return;
        }

        try {
            List<Element> elements = session.screens.values()
                .stream()
                .flatMap(handle -> handle.elements.values().stream())
                .sorted(Comparator.comparingInt(Element::layer))
                .toList();

            if (elements.isEmpty()) {
                if (session.shown) {
                    session.player.hideBossBar(session.bar);
                }

                session.shown = false;

                return;
            }

            var builder = Component.text();

            elements.forEach(element -> builder.append(element.rendered()));
            Component content = builder.build();

            if (!content.equals(session.bar.name())) {
                session.bar.name(content);
            }

            if (!session.shown) {
                session.player.showBossBar(session.bar);
                session.shown = true;
            }
        } catch (RuntimeException failure) {
            retire(session);
            throw failure;
        }
    }

    private static void validId(String id) {
        if (id == null || !id.matches("[a-zA-Z0-9_.:/-]{1,80}")) {
            throw new IllegalArgumentException("Invalid UI id");
        }
    }

    private static int cost(Component component) {
        int result = 1 + (component instanceof TextComponent text ? text.content().length() : 0);

        for (Component child : component.children()) {
            result += cost(child);
        }

        return result;
    }

    /** Identifies one owner's screen inside a player session. */
    public record ScreenKey(ServiceOwner owner, String id) {

    }

    /** Stores an element's rendered content, layer, and optional text layout. */
    public record Element(int layer, Component rendered, TextBlockLayout textLayout) {

    }

    /** Holds a player's screens, shared boss bar, and pending render task. */
    public static final class Session {

        private final Player player;
        private final Map<ScreenKey, Handle> screens = new LinkedHashMap<>();
        private final BossBar bar =
            BossBar.bossBar(Component.empty(), 0, BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS);
        private boolean pending;
        private VexTask task;
        private boolean shown;

        private Session(Player player) {
            this.player = player;
        }
    }

    /** Updates one screen's elements and schedules rendering through its player session. */
    public final class Handle implements ScreenUi {

        private final Session session;
        private final ScreenKey key;
        private final Map<String, Element> elements = new LinkedHashMap<>();
        private volatile boolean closed;

        private Handle(Session session, ScreenKey key) {
            this.session = session;
            this.key = key;
        }

        private void put(String id, Element value) {
            validId(id);

            synchronized (VexScreenUiCoordinatorService.this) {
                if (closed) {
                    throw new IllegalStateException("UI handle is closed");
                }

                int count = 0;
                int size = cost(value.rendered());

                for (Handle handle : session.screens.values()) {
                    for (var entry : handle.elements.entrySet()) {
                        if (handle == this && entry.getKey().equals(id)) {
                            continue;
                        }

                        count++;
                        size += cost(entry.getValue().rendered());
                    }
                }

                if (count >= 128 || size > 16384) {
                    throw new IllegalArgumentException("Player UI capacity exceeded");
                }

                if (!value.equals(elements.put(id, value))) {
                    request(session);
                }
            }
        }

        @Override
        public void textBlock(String id, List<Component> lines, TextBlockLayout layout) {
            put(id, new Element(layout.layer(), ScreenUiRenderer.text(List.copyOf(lines), layout, version), layout));
        }

        @Override
        public void textureBlock(String id, UiTexture texture, TextureLayout layout) {
            put(id, new Element(layout.layer(), ScreenUiRenderer.texture(texture, layout, version), null));
        }

        @Override
        public void setLines(String id, List<Component> lines) {
            synchronized (VexScreenUiCoordinatorService.this) {
                if (closed) {
                    throw new IllegalStateException("UI handle is closed");
                }

                Element element = elements.get(id);

                if (element == null || element.textLayout() == null) {
                    throw new IllegalArgumentException("No text block " + id);
                }

                textBlock(id, lines, element.textLayout());
            }
        }

        @Override
        public void remove(String id) {
            synchronized (VexScreenUiCoordinatorService.this) {
                if (!closed && elements.remove(id) != null) {
                    request(session);
                }
            }
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public void close() {
            synchronized (VexScreenUiCoordinatorService.this) {
                if (closed) {
                    return;
                }

                closed = true;
                elements.clear();
                session.screens.remove(key, this);

                if (session.screens.isEmpty()) {
                    retire(session);
                } else {
                    request(session);
                }
            }
        }
    }
}
