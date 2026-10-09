package dev.vexsoft.core.paper.service.reward;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.api.localization.Language;
import dev.vexsoft.core.api.localization.LanguageContainer;
import dev.vexsoft.core.api.localization.LanguageKey;
import dev.vexsoft.core.api.localization.LocalizedMessage;
import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.localization.LocalizationService;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.execution.PlayerExecutionContext;
import dev.vexsoft.core.paper.reward.RewardAnnouncement;
import dev.vexsoft.core.paper.reward.item.ItemReward;
import dev.vexsoft.core.paper.reward.item.RewardItem;
import dev.vexsoft.core.reward.PreparedRewards;
import dev.vexsoft.core.reward.RewardOptions;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

/** Checks grouped bundles, inline chances, styled names and commit-only sound emission. */
final class RewardAnnouncementTest {

    @Test
    void oneLegendaryBundleProducesOneMessageAndSoundAfterCommit() {
        List<Component> sent = new ArrayList<>();
        AtomicInteger sounds = new AtomicInteger();
        Player platform = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
            (instance, method, arguments) -> {
                if (method.getName().equals("sendMessage")) {
                    sent.add((Component) arguments[0]);
                } else if (method.getName().equals("playSound")) {
                    sounds.incrementAndGet();
                }
                return null;
            });
        var player = new VexPlayer(UUID.randomUUID(), "Alex", type -> 0);
        player.bindPlatformPlayer(platform);
        var language = (LanguageContainer) Proxy.newProxyInstance(LanguageContainer.class.getClassLoader(),
            new Class<?>[]{LanguageContainer.class}, (instance, method, arguments) ->
                new Language(LanguageKey.DE_DE, Component.text("Deutsch"), true));
        player.installContainer(0, LanguageContainer.class, language);
        var localizations = (LocalizationService) Proxy.newProxyInstance(LocalizationService.class.getClassLoader(),
            new Class<?>[]{LocalizationService.class}, (instance, method, arguments) -> {
                if (arguments[1].toString().endsWith(".formats.rewards")) {
                    return LocalizedMessage.list(List.of(Component.text("  %reward% ("
                        + ((Map<?, ?>) arguments[2]).get("chance") + ")")));
                }
                return LocalizedMessage.list(List.of(Component.empty(), Component.text("  LEGENDARY DROP"),
                    Component.empty(), Component.text("%rewards%"), Component.text(" ")));
            });
        var registry = (VexServiceRegistry) Proxy.newProxyInstance(VexServiceRegistry.class.getClassLoader(),
            new Class<?>[]{VexServiceRegistry.class}, (instance, method, arguments) -> localizations);
        var service = new VexRewardAnnouncementService(registry);
        var context = new PlayerExecutionContext(player, Map.of());
        var items = (RewardItemService) Proxy.newProxyInstance(RewardItemService.class.getClassLoader(),
            new Class<?>[]{RewardItemService.class}, (instance, method, arguments) -> switch (method.getName()) {
                case "validate" -> null;
                case "create" -> new Stack();
                case "name" -> ((RewardItem) arguments[1]).key().equals("vexisles:chestplate")
                    ? Component.text("Chestplate +5", NamedTextColor.GOLD)
                    : Component.text("Diamond", NamedTextColor.AQUA);
                default -> throw new AssertionError(method.getName());
            });
        var itemRegistry = (VexServiceRegistry) Proxy.newProxyInstance(VexServiceRegistry.class.getClassLoader(),
            new Class<?>[]{VexServiceRegistry.class}, (instance, method, arguments) -> items);
        var itemReward = new ItemReward(itemRegistry);
        var bundle = itemReward.compile(List.of(
            Map.of("key", "vexisles:chestplate", "upgrade-level", 5, "amount", 1),
            Map.of("key", "minecraft:diamond", "amount", 2))).prepare(context);
        var range = itemReward.compile(Map.of("key", "minecraft:diamond", "amount", Map.of("min", 1, "max", 3)));
        assertEquals("1\u20133\u00D7 Diamond", PlainTextComponentSerializer.plainText().serialize(range.describe(context)));
        var selected = new PreparedRewards(List.of(new PreparedRewards.Entry("bundle",
            new RewardOptions(0.005, "legendary_drop"), bundle)));
        var definitions = Map.of("legendary_drop", new RewardAnnouncement("drops.legendary",
            "minecraft:ui.toast.challenge_complete", 0.3F, 1.2F));
        assertTrue(player.atomic(value -> value, () -> {
            service.announce(selected, context, definitions);
            assertTrue(sent.isEmpty());
            assertEquals(0, sounds.get());
            return true;
        }));
        assertEquals(6, sent.size());
        assertEquals(1, sounds.get());
        assertEquals("  1\u00D7 Chestplate +5 (0.5 %)", PlainTextComponentSerializer.plainText().serialize(sent.get(3)));
        assertEquals("  2\u00D7 Diamond (0.5 %)", PlainTextComponentSerializer.plainText().serialize(sent.get(4)));
        assertEquals(NamedTextColor.GOLD, sent.get(3).children().stream()
            .flatMap(RewardAnnouncementTest::components)
            .filter(child -> child instanceof TextComponent text && text.content().equals("Chestplate +5"))
            .findFirst().orElseThrow().color());
    }

    private static Stream<Component> components(final Component component) {
        return Stream.concat(Stream.of(component), component.children().stream()
            .flatMap(RewardAnnouncementTest::components));
    }

    private static final class Stack extends ItemStack {
        @Getter(onMethod_ = @Override)
        @Setter(onMethod_ = @Override)
        private int amount = 1;

        @Override
        public int getMaxStackSize() {
            return 64;
        }

        @Override
        public ItemStack clone() {
            var copy = new Stack();
            copy.amount = amount;
            return copy;
        }
    }
}
