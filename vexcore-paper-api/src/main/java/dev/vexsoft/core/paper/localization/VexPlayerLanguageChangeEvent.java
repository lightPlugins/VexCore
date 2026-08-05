package dev.vexsoft.core.paper.localization;

import dev.vexsoft.core.api.localization.Language;
import dev.vexsoft.core.api.player.VexPlayer;
import lombok.Getter;
import lombok.NonNull;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

@Getter
public final class VexPlayerLanguageChangeEvent extends Event {

  private static final HandlerList HANDLERS = new HandlerList();

  @NonNull
  private final VexPlayer vexPlayer;
  @NonNull
  private final Language previousLanguage;
  @NonNull
  private final Language newLanguage;

  public VexPlayerLanguageChangeEvent(
      final VexPlayer vexPlayer,
      final Language previousLanguage,
      final Language newLanguage,
      final boolean asynchronous
  ) {
    super(asynchronous);
    this.vexPlayer = vexPlayer;
    this.previousLanguage = previousLanguage;
    this.newLanguage = newLanguage;
  }

  @Override
  public HandlerList getHandlers() {
    return HANDLERS;
  }

  /** Returns the handler list used by Bukkit for this event */
  public static HandlerList getHandlerList() {
    return HANDLERS;
  }
}
