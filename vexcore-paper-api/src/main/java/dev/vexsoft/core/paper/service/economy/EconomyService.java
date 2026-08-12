package dev.vexsoft.core.paper.service.economy;

import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.registry.VexService;

/** Platform economy bridge used by built-in coin execution types. */
public interface EconomyService extends VexService {

  /** Returns whether Vault currently exposes an economy provider. */
  boolean isAvailable();

  /** Returns the player's current balance. */
  double getBalance(VexPlayer player);

  /** Deposits a positive amount. */
  EconomyTransaction deposit(VexPlayer player, double amount);

  /** Withdraws a positive amount. */
  EconomyTransaction withdraw(VexPlayer player, double amount);

  /** Uses the provider's configured currency formatting. */
  String format(double amount);

  /** Immutable provider transaction outcome. */
  record EconomyTransaction(boolean successful, double amount, String message) {}
}
