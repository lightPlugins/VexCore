package dev.vexsoft.core.paper.service.economy;

import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import java.util.Objects;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

/** Vault-backed economy bridge that remains unavailable when no provider is installed. */
@Dependencies
public final class VexVaultEconomyService implements EconomyService {

  /** Validates service construction. */
  public VexVaultEconomyService(final VexServiceRegistry services) {
    Objects.requireNonNull(services, "services");
  }

  @Override
  public boolean isAvailable() {
    return provider() != null;
  }

  @Override
  public double getBalance(final VexPlayer player) {
    return requireProvider().getBalance(offline(player));
  }

  @Override
  public EconomyTransaction deposit(final VexPlayer player, final double amount) {
    validateAmount(amount);
    return transaction(requireProvider().depositPlayer(offline(player), amount));
  }

  @Override
  public EconomyTransaction withdraw(final VexPlayer player, final double amount) {
    validateAmount(amount);
    return transaction(requireProvider().withdrawPlayer(offline(player), amount));
  }

  @Override
  public String format(final double amount) {
    return requireProvider().format(amount);
  }

  private static Economy provider() {
    RegisteredServiceProvider<Economy> registration = Bukkit.getServicesManager()
        .getRegistration(Economy.class);
    return registration == null ? null : registration.getProvider();
  }

  private static Economy requireProvider() {
    Economy economy = provider();
    if (economy == null) {
      throw new IllegalStateException("Vault economy provider is unavailable");
    }
    return economy;
  }

  private static OfflinePlayer offline(final VexPlayer player) {
    return Bukkit.getOfflinePlayer(Objects.requireNonNull(player, "player").getUniqueId());
  }

  private static EconomyTransaction transaction(final EconomyResponse response) {
    return new EconomyTransaction(
        response.transactionSuccess(),
        response.amount,
        response.errorMessage == null ? "" : response.errorMessage
    );
  }

  private static void validateAmount(final double amount) {
    if (!Double.isFinite(amount) || amount <= 0D) {
      throw new IllegalArgumentException("Economy amount must be finite and greater than zero");
    }
  }
}
