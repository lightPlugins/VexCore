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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Objects;
import net.kyori.adventure.text.Component;

/** Owner-aware currency localization backed by the shared localization registry. */
@Dependencies({
    CurrencyRegistryCoordinatorService.class,
    LocalizationRegistryService.class,
    PlaceholderService.class
})
public final class VexCurrencyLocalizationService implements CurrencyLocalizationService {

  private static final String[] COMPACT_SUFFIXES = {"", "k", "m", "b", "t", "qa", "qi"};

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
      final long amount
  ) {
    VexPlayer checkedPlayer = Objects.requireNonNull(player, "player");
    Currency registered = require(currency);
    return resolve(
        checkedPlayer,
        registered,
        registered.getDefinition().getFormatKey(),
        Map.of(
            "amount", Long.toString(amount),
            "formatted_amount", formatCompact(amount)
        )
    );
  }

  @Override
  public String formatCompact(final long amount) {
    return compact(amount);
  }

  static String compact(final long amount) {
    if (amount > -1_000L && amount < 1_000L) {
      return Long.toString(amount);
    }
    BigDecimal absolute = BigDecimal.valueOf(amount).abs();
    BigDecimal divisor = BigDecimal.ONE;
    int suffix = 0;
    while (absolute.compareTo(divisor.multiply(BigDecimal.valueOf(1_000L))) >= 0
        && suffix < COMPACT_SUFFIXES.length - 1) {
      divisor = divisor.multiply(BigDecimal.valueOf(1_000L));
      suffix++;
    }
    BigDecimal compact = BigDecimal.valueOf(amount).divide(divisor, 1, RoundingMode.HALF_UP);
    if (compact.abs().compareTo(BigDecimal.valueOf(1_000L)) >= 0
        && suffix < COMPACT_SUFFIXES.length - 1) {
      compact = compact.divide(BigDecimal.valueOf(1_000L), 1, RoundingMode.HALF_UP);
      suffix++;
    }
    return compact.stripTrailingZeros().toPlainString() + COMPACT_SUFFIXES[suffix];
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
