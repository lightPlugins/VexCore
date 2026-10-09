package dev.vexsoft.core.paper.service.reward;

import dev.vexsoft.core.api.localization.LanguageContainer;
import dev.vexsoft.core.api.service.localization.LocalizationService;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.execution.PlayerExecutionContext;
import dev.vexsoft.core.paper.reward.RewardAnnouncement;
import dev.vexsoft.core.reward.PreparedRewards;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;

/** Expands localized reward rows while preserving styled item names and original selection chances. */
@Dependencies(LocalizationService.class)
public final class VexRewardAnnouncementService implements RewardAnnouncementService {

    private final LocalizationService localizations;

    /** Captures the plugin-owned localization resolver. */
    public VexRewardAnnouncementService(final VexServiceRegistry services) {
        localizations = services.require(LocalizationService.class);
    }

    @Override
    public void announce(final PreparedRewards rewards, final PlayerExecutionContext context,
                         final Map<String, RewardAnnouncement> types) {
        Map<String, List<PreparedRewards.Entry>> groups = new LinkedHashMap<>();
        rewards.entries().forEach(entry -> {
            if (entry.options().announcement() != null) {
                groups.computeIfAbsent(entry.options().announcement(), ignored -> new ArrayList<>()).add(entry);
            }
        });
        if (groups.isEmpty()) {
            return;
        }
        var language = context.player().getContainer(LanguageContainer.class).getLanguage().getKey();
        Player player = context.player().requirePlatformPlayer(Player.class);
        groups.forEach((type, entries) -> {
            RewardAnnouncement definition = types.get(type);
            if (definition == null) {
                throw new IllegalArgumentException("Unknown announcement type: " + type);
            }
            List<Component> rows = new ArrayList<>();
            entries.forEach(entry -> {
                String chance = BigDecimal.valueOf(entry.options().chance()).multiply(BigDecimal.valueOf(100))
                    .stripTrailingZeros().toPlainString() + " %";
                entry.reward().describeEntries(context).forEach(description ->
                    localizations.resolve(language, definition.localizationKey() + ".formats.rewards",
                        Map.of("chance", chance)).getComponents().forEach(line -> rows.add(line.replaceText(
                            TextReplacementConfig.builder().matchLiteral("%reward%")
                                .replacement(description.fallback()).build()))));
            });
            List<Component> lines = new ArrayList<>();
            localizations.resolve(language, definition.localizationKey() + ".message", Map.of()).getComponents()
                .forEach(line -> {
                    if (PlainTextComponentSerializer.plainText().serialize(line).equals("%rewards%")) {
                        lines.addAll(rows);
                    } else {
                        lines.add(line);
                    }
                });
            context.player().afterCommit(() -> {
                lines.forEach(player::sendMessage);
                if (definition.sound() != null) {
                    player.playSound(player.getLocation(), definition.sound(), definition.volume(), definition.pitch());
                }
            });
        });
    }
}
