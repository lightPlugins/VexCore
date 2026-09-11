package dev.vexsoft.core.paper.service.screenui;

import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.paper.screenui.DialoguePanelLayout;
import dev.vexsoft.core.paper.screenui.PreparedDialogue;
import dev.vexsoft.core.paper.screenui.ScreenUi;
import java.util.List;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/** Internal cross-owner coordinator. */
public interface ScreenUiCoordinatorService extends VexService {

    /** Returns the owner's existing screen or creates a screen for the player and ID. */
    ScreenUi open(ServiceOwner owner, Player player, String id);

    /** Finds an existing screen for the supplied owner, player, and ID. */
    Optional<ScreenUi> find(ServiceOwner owner, Player player, String id);

    /** Closes every screen belonging to the supplied owner. */
    void closeOwner(ServiceOwner owner);

    /** Retires the player's UI session and removes its displayed content. */
    void discard(Player player);

    /** Rejects dialogue layouts unsupported by the active resource-pack adapter. */
    void validateDialogueLayout(DialoguePanelLayout layout);

    /** Lays out dialogue text for the active resource-pack adapter before it is displayed. */
    PreparedDialogue prepareDialogue(Component speaker, List<Component> paragraphs, DialoguePanelLayout layout);
}
