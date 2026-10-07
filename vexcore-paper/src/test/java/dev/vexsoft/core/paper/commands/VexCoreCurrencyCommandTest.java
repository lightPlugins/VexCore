package dev.vexsoft.core.paper.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.vexsoft.core.api.localization.Language;
import dev.vexsoft.core.api.localization.LanguageContainer;
import dev.vexsoft.core.api.localization.LanguageKey;
import dev.vexsoft.core.api.localization.LocalizedMessage;
import dev.vexsoft.core.api.player.DataContainerKey;
import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.currency.CurrencyLocalizationService;
import dev.vexsoft.core.api.service.currency.CurrencyRegistry;
import dev.vexsoft.core.api.service.localization.LocalizationService;
import dev.vexsoft.core.api.service.placeholder.PlaceholderService;
import dev.vexsoft.core.api.service.player.PlayerService;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.common.service.currency.CurrencyPlayerData;
import dev.vexsoft.core.common.service.currency.CurrencyRegistryCoordinatorService;
import dev.vexsoft.core.common.service.currency.VexCurrencyContainer;
import dev.vexsoft.core.common.service.currency.VexCurrencyRegistry;
import dev.vexsoft.core.common.service.currency.VexCurrencyRegistryCoordinatorService;
import dev.vexsoft.core.currency.Currency;
import dev.vexsoft.core.currency.CurrencyContainer;
import dev.vexsoft.core.currency.CurrencyDefinition;
import dev.vexsoft.core.currency.CurrencyKey;
import dev.vexsoft.core.number.WholeAmount;
import dev.vexsoft.core.number.WholeAmountFormatter;
import dev.vexsoft.core.paper.command.VexCommandSource;
import dev.vexsoft.core.paper.service.messages.SendMessageService;
import dev.vexsoft.core.paper.service.scheduler.ScheduleService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Verifies public currency commands with real registry, exact amounts and persistent balance mutations. */
public final class VexCoreCurrencyCommandTest {

    private final Map<Class<?>, Object> dependencies = new HashMap<>();
    private final Map<UUID, VexPlayer> loaded = new HashMap<>();
    private final Map<String, Player> online = new HashMap<>();
    private final Map<CommandSender, List<Component>> output = new HashMap<>();
    private final List<String> errors = new ArrayList<>();
    private Server previousServer;
    private Field serverField;
    private Currency currency;
    private VexCoreCurrencyCommand commands;
    private final VexServiceRegistry services = proxy(VexServiceRegistry.class, (object, method, args) ->
        switch (method.getName()) {
            case "getOwner" -> (ServiceOwner) () -> "VexIsles";
            case "require" -> dependencies.get(args[0]);
            default -> throw new UnsupportedOperationException(method.getName());
        });

    @BeforeEach
    void setUp() throws Exception {
        serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        previousServer = (Server) serverField.get(null);
        serverField.set(null, proxy(Server.class, (object, method, args) -> {
            if (method.getName().equals("getPlayerExact")) {
                return online.get(args[0]);
            }
            throw new UnsupportedOperationException(method.getName());
        }));
        dependencies.put(CurrencyRegistryCoordinatorService.class, new VexCurrencyRegistryCoordinatorService(services));
        var registry = new VexCurrencyRegistry(services);
        dependencies.put(CurrencyRegistry.class, registry);
        currency = registry.register(CurrencyDefinition.builder(
            CurrencyKey.of("vexisles", "arkie_tumblebud_essence")).build());
        dependencies.put(PlayerService.class, proxy(PlayerService.class, (object, method, args) ->
            Optional.ofNullable(loaded.get(args[0]))));
        dependencies.put(SendMessageService.class, proxy(SendMessageService.class, (object, method, args) -> {
            errors.add((String) args[1]);
            return null;
        }));
        dependencies.put(ScheduleService.class, proxy(ScheduleService.class, (object, method, args) -> {
            if (method.getName().equals("runFor")) {
                ((Runnable) args[1]).run();
                return Optional.empty();
            }
            ((Runnable) args[0]).run();
            return null;
        }));
        dependencies.put(PlaceholderService.class, proxy(PlaceholderService.class, (object, method, args) -> args[1]));
        dependencies.put(LocalizationService.class, proxy(LocalizationService.class, (object, method, args) ->
            LocalizedMessage.single(Component.text(args[1].equals("general.prefix") ? "CORE » "
                : args[0] + " " + args[1] + " %value%"))));
        dependencies.put(CurrencyLocalizationService.class,
            proxy(CurrencyLocalizationService.class, (object, method, args) -> {
                LanguageKey language = args[0] instanceof VexPlayer viewer
                    ? viewer.getContainer(LanguageContainer.class).getLanguage().getKey() : (LanguageKey) args[0];
                return Component.text(WholeAmountFormatter.format((WholeAmount) args[2]) + " "
                    + (language.equals(LanguageKey.DE_DE) ? "Tumblebud-Essenz" : "Tumblebud Essence"),
                    NamedTextColor.LIGHT_PURPLE);
            }));
        commands = new VexCoreCurrencyCommand(services);
    }

    @AfterEach
    void restoreServer() throws Exception {
        serverField.set(null, previousServer);
    }

    @Test
    void selfGiveParsesMillionAndAlphabeticSuffixesAndPreservesStyledValue() {
        Player alex = player("Alex", LanguageKey.DE_DE);
        assertEquals(1, commands.give(source(alex), currency.getKey().value(), "5m"));
        assertEquals(WholeAmount.of(5_000_000), balance(alex));
        assertEquals(1, commands.give(source(alex), currency.getKey().value(), "15aa"));
        assertEquals(WholeAmountFormatter.parse("15aa").add(WholeAmount.of(5_000_000)), balance(alex));
        assertEquals(2, output.get(alex).size());
        Component message = output.get(alex).getLast();
        assertTrue(plain(message).startsWith("CORE » de_DE"));
        assertTrue(plain(message).contains("15aa Tumblebud-Essenz"));
        assertTrue(hasColor(message));
        assertTrue(errors.isEmpty());
    }

    @Test
    void givingOthersCreditsOnlyTargetAndNotifiesEachInTheirLanguage() {
        Player alex = player("Alex", LanguageKey.DE_DE);
        Player sam = player("Sam", LanguageKey.EN_EN);
        assertEquals(1, commands.givePlayer(source(alex), currency.getKey().value(), "15aa", "Sam"));
        assertEquals(WholeAmount.ZERO, balance(alex));
        assertEquals(WholeAmountFormatter.parse("15aa"), balance(sam));
        assertTrue(plain(output.get(alex).getFirst()).contains("15aa Tumblebud-Essenz"));
        assertTrue(plain(output.get(sam).getFirst()).contains("received 15aa Tumblebud Essence"));
        assertEquals(1, commands.balancePlayer(source(alex), currency.getKey().value(), "Sam"));
        assertTrue(plain(output.get(alex).getLast()).contains("balance 15aa Tumblebud-Essenz"));
    }

    @Test
    void invalidAmountsUnknownCurrenciesAndOfflineTargetsDoNotCredit() {
        Player alex = player("Alex", LanguageKey.DE_DE);
        for (String input : List.of("0", "-5m", "1.1", "0.0001k", "5q", "nope")) {
            assertEquals(0, commands.give(source(alex), currency.getKey().value(), input));
            assertEquals("commands.vexcore.currency.invalid-amount", errors.getLast());
        }
        assertEquals(0, commands.give(source(alex), "missing", "5m"));
        assertEquals("commands.vexcore.currency.unknown", errors.getLast());
        assertEquals(0, commands.givePlayer(source(alex), currency.getKey().value(), "5m", "Offline"));
        assertEquals("commands.vexcore.currency.player-offline", errors.getLast());
        assertEquals(WholeAmount.ZERO, balance(alex));
        assertTrue(output.get(alex).isEmpty());
    }

    @Test
    void consoleCanCreditAnOnlinePlayerButNeedsAnExplicitTarget() {
        Player sam = player("Sam", LanguageKey.EN_EN);
        CommandSender console = proxy(CommandSender.class, (object, method, args) -> {
            if (method.getName().equals("sendMessage")) {
                output.computeIfAbsent((CommandSender) object, ignored -> new ArrayList<>()).add((Component) args[0]);
                return null;
            }
            return identity(object, method.getName(), args);
        });
        assertEquals(0, commands.give(source(console), currency.getKey().value(), "5m"));
        assertEquals("commands.vexcore.currency.player-only", errors.getLast());
        assertEquals(1, commands.givePlayer(source(console), currency.getKey().value(), "5m", "Sam"));
        assertEquals(WholeAmount.of(5_000_000), balance(sam));
        assertTrue(plain(output.get(console).getFirst()).contains("given 5m Tumblebud Essence"));
    }

    @Test
    void failedDepositDoesNotSendSuccessOrChangeTheBalance() {
        Player alex = player("Alex", LanguageKey.DE_DE);
        ((CurrencyRegistry) dependencies.get(CurrencyRegistry.class)).register(
            CurrencyDefinition.builder(currency.getKey()).maximumBalance(WholeAmount.of(10)).build());
        commands.give(source(alex), currency.getKey().value(), "5m");
        assertEquals(WholeAmount.ZERO, balance(alex));
        assertEquals(List.of("commands.vexcore.currency.maximum-exceeded"), errors);
        assertTrue(output.get(alex).isEmpty());
    }

    @Test
    void disconnectBeforeExecutionDoesNotCreditOrSendSuccess() {
        Player alex = player("Alex", LanguageKey.DE_DE);
        Player sam = player("Sam", LanguageKey.EN_EN);
        loaded.remove(sam.getUniqueId());
        commands.givePlayer(source(alex), currency.getKey().value(), "5m", "Sam");
        assertEquals(List.of("commands.vexcore.currency.player-offline"), errors);
        assertTrue(output.get(alex).isEmpty());
        assertTrue(output.get(sam).isEmpty());
    }

    @Test
    void suggestionsAndCommandArgumentsUseColonFreeIds() throws Exception {
        Player alex = player("Alex", LanguageKey.DE_DE);
        var suggestions = new CurrencySuggestionProvider(services)
            .suggest(source(alex), new SuggestionsBuilder("arkie_", 0)).join().getList();
        assertEquals(List.of("arkie_tumblebud_essence"), suggestions.stream().map(value -> value.getText()).toList());
        for (var suggestion : suggestions) {
            var reader = new StringReader(suggestion.getText());
            assertEquals(suggestion.getText(), StringArgumentType.word().parse(reader));
            assertFalse(reader.canRead());
            assertFalse(suggestion.getText().contains(":"));
        }
        assertEquals(1, commands.give(source(alex), "vexisles.arkie_tumblebud_essence", "5m"));
        assertEquals(WholeAmount.of(5_000_000), balance(alex));
        assertEquals(0, commands.give(source(alex), currency.getKey().toString(), "5m"));
        assertEquals(WholeAmount.of(5_000_000), balance(alex));
        assertEquals("commands.vexcore.currency.unknown", errors.getLast());
    }

    @Test
    void collidingShortIdsRequireQualifiedIdsWithDotsAndDoNotCreditTheWrongCurrency() throws Exception {
        Player alex = player("Alex", LanguageKey.DE_DE);
        var coordinator = (CurrencyRegistryCoordinatorService) dependencies.get(CurrencyRegistryCoordinatorService.class);
        var other = coordinator.register(() -> "Other", CurrencyDefinition.builder(
            CurrencyKey.of("other", "arkie_tumblebud_essence")).build());
        assertEquals(0, commands.give(source(alex), "arkie_tumblebud_essence", "5m"));
        assertEquals("commands.vexcore.currency.ambiguous", errors.getLast());
        assertEquals(WholeAmount.ZERO, balance(alex));
        var suggestions = new CurrencySuggestionProvider(services)
            .suggest(source(alex), new SuggestionsBuilder("", 0)).join().getList();
        assertEquals(List.of("other.arkie_tumblebud_essence", "vexisles.arkie_tumblebud_essence"),
            suggestions.stream().map(value -> value.getText()).toList());
        for (var suggestion : suggestions) {
            var reader = new StringReader(suggestion.getText());
            assertEquals(suggestion.getText(), StringArgumentType.word().parse(reader));
            assertFalse(reader.canRead());
        }
        assertEquals(1, commands.give(source(alex), "vexisles.arkie_tumblebud_essence", "15aa"));
        assertEquals(WholeAmountFormatter.parse("15aa"), balance(alex));
        assertEquals(WholeAmount.ZERO, loaded.get(alex.getUniqueId()).getContainer(CurrencyContainer.class)
            .getBalance(other));
    }

    private Player player(final String name, final LanguageKey language) {
        UUID id = UUID.randomUUID();
        VexPlayer vexPlayer = new VexPlayer(id, name, type -> type == CurrencyContainer.class ? 0 : 1);
        new CurrencyPlayerData(services).register(key -> install(vexPlayer, key));
        vexPlayer.installContainer(0, CurrencyContainer.class, new VexCurrencyContainer(vexPlayer));
        vexPlayer.installContainer(1, LanguageContainer.class,
            proxy(LanguageContainer.class, (object, method, args) -> new Language(language, Component.empty(), false)));
        loaded.put(id, vexPlayer);
        Player player = proxy(Player.class, (object, method, args) -> switch (method.getName()) {
            case "getUniqueId" -> id;
            case "getName" -> name;
            case "isOnline" -> true;
            case "sendMessage" -> {
                output.get(object).add((Component) args[0]);
                yield null;
            }
            default -> identity(object, method.getName(), args);
        });
        online.put(name, player);
        output.put(player, new ArrayList<>());
        return player;
    }

    private WholeAmount balance(final Player player) {
        return loaded.get(player.getUniqueId()).getContainer(CurrencyContainer.class).getBalance(currency);
    }

    private static <T> void install(final VexPlayer player, final DataContainerKey<T> key) {
        player.install(key, key.createDefaultValue());
    }

    private static VexCommandSource source(final CommandSender sender) {
        return new VexCommandSource(proxy(CommandSourceStack.class, (object, method, args) -> sender));
    }

    private static String plain(final Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static boolean hasColor(final Component component) {
        return NamedTextColor.LIGHT_PURPLE.equals(component.color()) || component.children().stream().anyMatch(
            VexCoreCurrencyCommandTest::hasColor);
    }

    private static Object identity(final Object object, final String method, final Object[] args) {
        return switch (method) {
            case "hashCode" -> System.identityHashCode(object);
            case "equals" -> object == args[0];
            case "toString" -> "TestSender";
            default -> throw new UnsupportedOperationException(method);
        };
    }

    private static <T> T proxy(final Class<T> type, final InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }
}
