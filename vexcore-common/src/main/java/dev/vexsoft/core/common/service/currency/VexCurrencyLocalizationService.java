package dev.vexsoft.core.common.service.currency;

import dev.vexsoft.core.api.localization.LanguageContainer;
import dev.vexsoft.core.api.localization.LocalizedMessage;
import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.currency.CurrencyLocalizationService;
import dev.vexsoft.core.api.service.placeholder.PlaceholderService;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.common.service.localization.LocalizationRegistryService;
import dev.vexsoft.core.currency.Currency;
import dev.vexsoft.core.currency.CurrencyKey;
import java.util.Map;
import java.util.Objects;
import dev.vexsoft.core.number.WholeAmount;
import dev.vexsoft.core.number.WholeAmountFormatter;
import net.kyori.adventure.text.Component;

/** Owner-aware currency localization backed by the shared localization registry. */
@Dependencies({
    CurrencyRegistryCoordinatorService.class,
    LocalizationRegistryService.class,
    PlaceholderService.class
})
public final class VexCurrencyLocalizationService implements CurrencyLocalizationService {

  private final CurrencyRegistryCoordinatorService currencies;
  private final LocalizationRegistryService localizations;
  private final PlaceholderService placeholders;

  /** Resolves the shared currency and localization services. */
  public VexCurrencyLocalizationService(final VexServiceRegistry services) {
    VexServiceRegistry checked = Objects.requireNonNull(services, "services");
    currencies = checked.require(CurrencyRegistryCoordinatorService.class);
    localizations = checked.require(LocalizationRegistryService.class);
    placeholders = checked.require(PlaceholderService.class);
  }

  @Override
  public Component getName(final VexPlayer player, final CurrencyKey currency) {
    VexPlayer checkedPlayer = Objects.requireNonNull(player, "player");
    Currency registered = require(currency);
    return resolve(
        checkedPlayer,
        registered,
        registered.getDefinition().getNameKey(),
        Map.of()
    );
  }

  @Override
  public Component format(
      final VexPlayer player,
      final CurrencyKey currency,
      final WholeAmount amount
  ) {
    VexPlayer checkedPlayer = Objects.requireNonNull(player, "player");
    Currency registered = require(currency);
    return resolve(
        checkedPlayer,
        registered,
        registered.getDefinition().getFormatKey(),
        Map.of(
            "amount", amount.toString(),
            "formatted_amount", formatCompact(amount)
        )
    );
  }

  @Override
  public String formatCompact(final WholeAmount amount) {
    return WholeAmountFormatter.format(amount);
  }

  private Currency require(final CurrencyKey key) {
    return currencies.find(Objects.requireNonNull(key, "currency")).orElseThrow(
        () -> new IllegalStateException("Currency is not registered: " + key)
    );
  }

  private Component resolve(
      final VexPlayer player,
      final Currency currency,
      final String key,
      final Map<String, String> replacements
  ) {
    LocalizedMessage message = localizations.resolve(
        currency.getKey().namespace(),
        player.getContainer(LanguageContainer.class).getLanguage().getKey(),
        key,
        replacements
    );
    return placeholders.resolve(player, message.getLines().getFirst());
  }
}
