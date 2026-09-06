package dev.vexsoft.core.paper.service.screenui;

import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.screenui.ScreenUi;
import dev.vexsoft.core.paper.screenui.DialoguePanel;
import dev.vexsoft.core.paper.screenui.DialoguePanelLayout;
import dev.vexsoft.core.paper.screenui.PreparedDialogue;
import java.util.List;
import net.kyori.adventure.text.Component;
import java.util.Optional;
import org.bukkit.entity.Player;
/** Automatically removes a plugin's UI handles when its scope closes. */
@Dependencies(ScreenUiCoordinatorService.class)
public final class VexScreenUiService implements ScreenUiService, AutoCloseable {
  private final ServiceOwner owner;
  private final ScreenUiCoordinatorService coordinator;
  private boolean closed;
  public VexScreenUiService(VexServiceRegistry services) {
    owner = services.getOwner();
    coordinator = services.require(ScreenUiCoordinatorService.class);
  }

  @Override public synchronized ScreenUi open(Player player, String id) {
    if (closed) {
      throw new IllegalStateException("UI owner is closed");
    }

    return coordinator.open(owner, player, id);
  }

  @Override public Optional<ScreenUi> find(Player player, String id) {
    return coordinator.find(owner, player, id);
  }

  @Override public void validateDialogueLayout(DialoguePanelLayout layout) {
    coordinator.validateDialogueLayout(layout);
  }

  @Override public PreparedDialogue prepareDialogue(Component speaker, List<Component> paragraphs, DialoguePanelLayout layout) {
    return coordinator.prepareDialogue(speaker, paragraphs, layout);
  }

  @Override public DialoguePanel openDialogue(Player player, String id, PreparedDialogue dialogue) {
    ScreenUi screen = open(player, id);
    try {
      return new VexDialoguePanel(screen, dialogue);
    } catch (RuntimeException exception) {
      screen.close();
      throw exception;
    }
  }

  @Override public synchronized void close() {
    closed = true;
    coordinator.closeOwner(owner);
  }

}
