package dev.vexsoft.core.paper.commands;

import dev.vexsoft.core.currency.Currency;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Resolves command IDs independently of the stable namespaced persistence keys. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class CurrencyCommandIds {

    static List<Currency> matches(final Collection<Currency> currencies, final String input) {
        String id = input.toLowerCase(Locale.ROOT);
        return currencies.stream().filter(currency -> currency.getKey().value().equals(id)
            || qualified(currency).equals(id)).toList();
    }

    static List<String> suggestions(final Collection<Currency> currencies) {
        var counts = currencies.stream().map(currency -> currency.getKey().value())
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        return currencies.stream().map(currency -> counts.get(currency.getKey().value()) == 1L
            ? currency.getKey().value() : qualified(currency)).sorted().toList();
    }

    private static String qualified(final Currency currency) {
        return currency.getKey().namespace() + '.' + currency.getKey().value();
    }
}
