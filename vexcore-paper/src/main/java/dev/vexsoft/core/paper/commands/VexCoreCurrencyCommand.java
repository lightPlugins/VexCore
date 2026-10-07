package dev.vexsoft.core.paper.commands;

import dev.vexsoft.core.api.localization.LanguageContainer;
import dev.vexsoft.core.api.localization.LanguageKey;
import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.currency.CurrencyLocalizationService;
import dev.vexsoft.core.api.service.currency.CurrencyRegistry;
import dev.vexsoft.core.api.service.localization.LocalizationService;
import dev.vexsoft.core.api.service.placeholder.PlaceholderService;
import dev.vexsoft.core.api.service.player.PlayerService;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.currency.Currency;
import dev.vexsoft.core.currency.CurrencyContainer;
import dev.vexsoft.core.currency.CurrencyTransaction;
import dev.vexsoft.core.number.WholeAmount;
import dev.vexsoft.core.number.WholeAmountFormatter;
import dev.vexsoft.core.paper.command.Argument;
import dev.vexsoft.core.paper.command.Command;
import dev.vexsoft.core.paper.command.CommandRoot;
import dev.vexsoft.core.paper.command.Suggest;
import dev.vexsoft.core.paper.command.VexCommandSource;
import dev.vexsoft.core.paper.service.messages.SendMessageService;
import dev.vexsoft.core.paper.service.scheduler.ScheduleService;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Generic administration and balance inspection for every registered virtual currency. */
@CommandRoot(name = "vexcore", description = "Manages VexCore")
@Dependencies({CurrencyRegistry.class, CurrencyLocalizationService.class, PlayerService.class,
    SendMessageService.class, LocalizationService.class, PlaceholderService.class, ScheduleService.class})
public final class VexCoreCurrencyCommand {

    private static final String TEXT = "commands.vexcore.currency.";
    private final CurrencyRegistry currencies;
    private final CurrencyLocalizationService currencyTexts;
    private final PlayerService players;
    private final SendMessageService messages;
    private final LocalizationService texts;
    private final PlaceholderService placeholders;
    private final ScheduleService schedules;

    public VexCoreCurrencyCommand(final VexServiceRegistry services) {
        currencies = services.require(CurrencyRegistry.class);
        currencyTexts = services.require(CurrencyLocalizationService.class);
        players = services.require(PlayerService.class);
        messages = services.require(SendMessageService.class);
        texts = services.require(LocalizationService.class);
        placeholders = services.require(PlaceholderService.class);
        schedules = services.require(ScheduleService.class);
    }

    /** Credits the executing player's virtual balance with an exact or compact amount. */
    @Command(value = "currency give <currency> <amount>", permission = "vexcore.command.currency.give")
    public int give(
        final VexCommandSource source,
        @Argument("currency") @Suggest(CurrencySuggestionProvider.class) final String currency,
        @Argument("amount") final String amount
    ) {
        return giveTo(source, currency, amount, self(source));
    }

    /** Credits an online player's virtual balance and notifies both parties. */
    @Command(value = "currency give <currency> <amount> <player>", permission = "vexcore.command.currency.give.others")
    public int givePlayer(
        final VexCommandSource source,
        @Argument("currency") @Suggest(CurrencySuggestionProvider.class) final String currency,
        @Argument("amount") final String amount,
        @Argument("player") @Suggest(OnlinePlayerSuggestionProvider.class) final String player
    ) {
        return giveTo(source, currency, amount, target(source, player));
    }

    /** Displays the executing player's current balance. */
    @Command(value = "currency balance <currency>", permission = "vexcore.command.currency.balance")
    public int balance(
        final VexCommandSource source,
        @Argument("currency") @Suggest(CurrencySuggestionProvider.class) final String currency
    ) {
        return showBalance(source, currency, self(source));
    }

    /** Displays an online player's current balance. */
    @Command(value = "currency balance <currency> <player>", permission = "vexcore.command.currency.balance.others")
    public int balancePlayer(
        final VexCommandSource source,
        @Argument("currency") @Suggest(CurrencySuggestionProvider.class) final String currency,
        @Argument("player") @Suggest(OnlinePlayerSuggestionProvider.class) final String player
    ) {
        return showBalance(source, currency, target(source, player));
    }

    private int giveTo(final VexCommandSource source, final String key, final String input, final Player target) {
        if (target == null) {
            return 0;
        }
        Currency currency = currency(source, key);
        if (currency == null) {
            return 0;
        }
        WholeAmount amount;
        try {
            amount = WholeAmountFormatter.parse(input);
            if (!amount.isPositive()) {
                throw new IllegalArgumentException("Amount must be positive");
            }
        } catch (IllegalArgumentException | ArithmeticException exception) {
            messages.send(source.getSender(), TEXT + "invalid-amount", true);
            return 0;
        }
        schedules.runFor(target, () -> {
            VexPlayer player = players.find(target.getUniqueId()).orElse(null);
            if (player == null || !target.isOnline()) {
                sendUnavailable(source, target.getName());
                return;
            }
            CurrencyTransaction result = player.getContainer(CurrencyContainer.class).deposit(currency, amount);
            if (!result.successful()) {
                String error = result.status() == CurrencyTransaction.Status.MAXIMUM_EXCEEDED
                    ? "maximum-exceeded" : "unavailable";
                send(source.getSender(), () -> messages.send(source.getSender(), TEXT + error, true));
                return;
            }
            if (!target.equals(source.getSender())) {
                sendValue(target, "received", currency, amount, target.getName());
            }
            send(source.getSender(), () -> sendValue(source.getSender(), "given", currency, amount, target.getName()));
        }, () -> sendUnavailable(source, target.getName()));
        return 1;
    }

    private int showBalance(final VexCommandSource source, final String key, final Player target) {
        if (target == null) {
            return 0;
        }
        Currency currency = currency(source, key);
        if (currency == null) {
            return 0;
        }
        schedules.runFor(target, () -> {
            VexPlayer player = players.find(target.getUniqueId()).orElse(null);
            if (player == null || !target.isOnline() || !currency.isRegistered()) {
                send(source.getSender(), () -> messages.send(source.getSender(), TEXT + "unavailable", true));
                return;
            }
            WholeAmount balance = player.getContainer(CurrencyContainer.class).getBalance(currency);
            send(source.getSender(), () -> sendValue(source.getSender(), "balance", currency, balance, target.getName()));
        }, () -> sendUnavailable(source, target.getName()));
        return 1;
    }

    private Currency currency(final VexCommandSource source, final String id) {
        var matches = CurrencyCommandIds.matches(currencies.getRegisteredCurrencies(), id);
        if (matches.size() == 1) {
            return matches.getFirst();
        }
        String error = matches.isEmpty() ? "unknown" : "ambiguous";
        messages.send(source.getSender(), TEXT + error, true, Map.of("currency", id));
        return null;
    }

    private Player self(final VexCommandSource source) {
        if (source.getSender() instanceof Player player) {
            return player;
        }
        messages.send(source.getSender(), TEXT + "player-only", true);
        return null;
    }

    private Player target(final VexCommandSource source, final String name) {
        Player player = Bukkit.getPlayerExact(name);
        if (player == null) {
            messages.send(source.getSender(), TEXT + "player-offline", true, Map.of("player", name));
        }
        return player;
    }

    private void sendUnavailable(final VexCommandSource source, final String name) {
        send(source.getSender(), () -> messages.send(source.getSender(), TEXT + "player-offline", true,
            Map.of("player", name)));
    }

    private void send(final CommandSender sender, final Runnable task) {
        if (sender instanceof Player player) {
            schedules.runFor(player, task);
        } else {
            schedules.runGlobal(task);
        }
    }

    private void sendValue(
        final CommandSender sender,
        final String key,
        final Currency currency,
        final WholeAmount amount,
        final String playerName
    ) {
        VexPlayer viewer = sender instanceof Player player ? players.find(player.getUniqueId()).orElse(null) : null;
        if (sender instanceof Player && viewer == null) {
            return;
        }
        if (!currency.isRegistered()) {
            messages.send(sender, TEXT + "unavailable", true);
            return;
        }
        LanguageKey language = viewer == null ? LanguageKey.EN_EN
            : viewer.getContainer(LanguageContainer.class).getLanguage().getKey();
        var value = viewer == null ? currencyTexts.format(language, currency.getKey(), amount)
            : currencyTexts.format(viewer, currency.getKey(), amount);
        var message = texts.resolve(language, TEXT + key, Map.of("player", playerName)).getComponent()
            .replaceText(builder -> builder.matchLiteral("%value%").replacement(value));
        var prefix = texts.resolve(language, "general.prefix", Map.of()).getComponent();
        var rendered = prefix.append(message);
        sender.sendMessage(viewer == null ? rendered : placeholders.resolve(viewer, rendered));
    }
}
