package dev.vexsoft.core.dialog;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;

/**
 * Defines the shared presentation and lifecycle options for a dialog
 */
public interface DialogBuilder<T, B extends DialogBuilder<T, B>> {

  /** Sets the title shown at the top of the dialog */
  public B title(Component title);

  /** Adds a component to the dialog body */
  public B message(Component message);

  /** Adds an item to the dialog body */
  public B item(ItemStack item);

  /** Controls whether the player may close the dialog with escape */
  public B canCloseWithEscape(boolean canCloseWithEscape);

  /** Sets how long the dialog may remain unanswered */
  public B timeout(Duration timeout);

  /** Opens the dialog and returns its asynchronous result */
  public CompletableFuture<DialogResult<T>> open();
}
