package dev.vexsoft.core.api.service.currency;

import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.currency.CurrencyKey;
import net.kyori.adventure.text.Component;

/** Resolves owner-provided localized currency names and formatted balances. */
public interface CurrencyLocalizationService extends VexService {

  /** Returns the localized display name for the player's selected language. */
  Component getName(VexPlayer player, CurrencyKey currency);

  /** Returns a compact gaming representation such as {@code 10k}, {@code 1.5m}, or {@code 2b}. */
  String formatCompact(long amount);

  /**
   * Formats an amount through the currency owner's localized format.
   *
   * <p>The format receives {@code %amount%} as the exact value and
   * {@code %formatted_amount%} as a compact gaming value such as {@code 10k} or {@code 1.5m}.</p>
   */
  Component format(VexPlayer player, CurrencyKey currency, long amount);
}
