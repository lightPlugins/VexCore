package dev.vexsoft.core.paper.screenui.service;

import dev.vexsoft.core.api.service.cache.CacheService;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.screenui.ScreenUi;
import dev.vexsoft.core.paper.screenui.UiNode;
import dev.vexsoft.core.paper.screenui.dialogue.DialoguePanel;
import dev.vexsoft.core.paper.screenui.dialogue.DialoguePanelLayout;
import dev.vexsoft.core.paper.screenui.dialogue.DialoguePanelSkin;
import dev.vexsoft.core.paper.screenui.dialogue.PreparedDialogue;
import dev.vexsoft.core.paper.screenui.dialogue.VexDialoguePanel;
import dev.vexsoft.core.paper.screenui.panel.UiPanelStyle;
import dev.vexsoft.core.paper.screenui.render.ScreenUiAvatarLoader;
import dev.vexsoft.core.paper.service.screenui.ScreenUiService;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/** Automatically removes a plugin's UI handles when its scope closes. */
@Dependencies({ScreenUiCoordinatorService.class, CacheService.class})
public final class VexScreenUiService implements ScreenUiService, AutoCloseable {

    private final ServiceOwner owner;
    private final ScreenUiCoordinatorService coordinator;
    private final ScreenUiAvatarLoader avatars;
    private boolean closed;

    public VexScreenUiService(VexServiceRegistry services) {
        avatars = new ScreenUiAvatarLoader(services.require(CacheService.class));
        owner = services.getOwner();
        coordinator = services.require(ScreenUiCoordinatorService.class);
    }

    @Override
    public synchronized CompletableFuture<UiNode.Avatar> avatar(Player player) {
        if (closed) {
            throw new IllegalStateException("UI owner is closed");
        }
        return avatars.avatar(player);
    }

    @Override
    public synchronized ScreenUi open(Player player, String id) {
        if (closed) {
            throw new IllegalStateException("UI owner is closed");
        }

        return coordinator.open(owner, player, id);
    }

    @Override
    public Optional<ScreenUi> find(Player player, String id) {
        return coordinator.find(owner, player, id);
    }

    @Override
    public void validateDialogueLayout(DialoguePanelLayout layout) {
        coordinator.validateDialogueLayout(layout);
    }

    @Override
    public PreparedDialogue prepareDialogue(Component speaker, List<Component> paragraphs, DialoguePanelLayout layout) {
        return coordinator.prepareDialogue(speaker, paragraphs, layout);
    }

    @Override
    public DialoguePanel openDialogue(Player player, String id, PreparedDialogue dialogue) {
        return openDialogue(player, id, dialogue, UiPanelStyle.DEFAULT);
    }

    @Override
    public DialoguePanel openDialogue(Player player, String id, PreparedDialogue dialogue, DialoguePanelSkin skin) {
        ScreenUi screen = open(player, id);

        try {
            return new VexDialoguePanel(screen, dialogue, skin);
        } catch (RuntimeException exception) {
            screen.close();
            throw exception;
        }
    }

    @Override
    public DialoguePanel openDialogue(
        Player player, String id, PreparedDialogue dialogue,
        UiPanelStyle style
    ) {
        ScreenUi screen = open(player, id);
        try {
            return new VexDialoguePanel(screen, dialogue, null, style);
        } catch (RuntimeException exception) {
            screen.close();
            throw exception;
        }
    }

    @Override
    public synchronized void close() {
        closed = true;
        avatars.close();
        coordinator.closeOwner(owner);
    }
}
