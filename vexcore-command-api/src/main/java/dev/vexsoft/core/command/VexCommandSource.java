package dev.vexsoft.core.command;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;

/** Provides the Paper command context passed to Vex command handlers. */
@Getter
@RequiredArgsConstructor
public final class VexCommandSource {

  @NonNull
  private final CommandSourceStack paper;

  /** Returns the sender that executed the command */
  public CommandSender getSender() {
    return paper.getSender();
  }

  /** Returns the entity used as the command executor when one exists */
  public Entity getExecutor() {
    return paper.getExecutor();
  }

  /** Returns the location from which the command was executed */
  public Location getLocation() {
    return paper.getLocation();
  }

  /** Checks whether the sender has the given permission */
  public boolean hasPermission(final String permission) {
    return getSender().hasPermission(permission);
  }
}
