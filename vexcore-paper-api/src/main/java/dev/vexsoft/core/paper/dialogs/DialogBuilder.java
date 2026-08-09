package dev.vexsoft.core.paper.dialogs;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;

/**
 * Defines the shared presentation and lifecycle options for a dialog
 */
public interface DialogBuilder<T, B extends DialogBuilder<T, B>> {

  /** Sets the title shown at the top of the dialog */
  B title(Component title);

  /** Adds a component to the dialog body */
  B message(Component message);

  /** Adds an item to the dialog body */
  B item(ItemStack item);

  /** Controls whether the player may close the dialog with escape */
  B canCloseWithEscape(boolean canCloseWithEscape);

  /** Sets how long the dialog may remain unanswered */
  B timeout(Duration timeout);

  /** Opens the dialog and returns its asynchronous result */
  CompletableFuture<DialogResult<T>> open();
}
