package dev.vexsoft.core.paper.service.reward;

import dev.vexsoft.core.api.localization.LanguageContainer;
import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.player.profile.PlayerProfileContext;
import dev.vexsoft.core.api.player.profile.ProfileDomain;
import dev.vexsoft.core.api.service.localization.LocalizationService;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.number.WholeAmountFormatter;
import dev.vexsoft.core.paper.scheduler.VexTask;
import dev.vexsoft.core.paper.screenui.RewardCard;
import dev.vexsoft.core.paper.service.scheduler.ScheduleService;
import dev.vexsoft.core.paper.service.screenui.ScreenUiService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/** One shared feed per viewer, with tasks only while notifications are pending or visible. */
@Dependencies({ScreenUiService.class, ScheduleService.class, LocalizationService.class})
public final class VexRewardToastService implements RewardToastService, AutoCloseable {

    private final ScreenUiService screens;
    private final ScheduleService schedules;
    private final LocalizationService localizations;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private volatile boolean closed;

    public VexRewardToastService(VexServiceRegistry services) {
        screens = services.require(ScreenUiService.class);
        schedules = services.require(ScheduleService.class);
        localizations = services.require(LocalizationService.class);
    }

    @Override
    public synchronized void show(VexPlayer account, List<RewardCard> cards) {
        Player player = account.requirePlatformPlayer(Player.class);
        if (closed || cards.isEmpty() || !player.isOnline()) {
            return;
        }
        Session session = sessions.get(player.getUniqueId());
        if (session != null && !session.matches(account, player)) {
            clear(account);
            session = null;
        }
        if (session == null) {
            var language = account.getContainer(LanguageContainer.class).getLanguage().getKey();
            Component subtitle = localizations.resolve(language, "reward-notifications.received", Map.of())
                .getComponents().getFirst();
            session = new Session(account, player, new RewardToastQueue(screens.open(player, "reward-feed"), subtitle));
            sessions.put(player.getUniqueId(), session);
        }
        long now = player.getWorld().getGameTime();
        for (RewardCard card : cards) {
            if (!session.queue.push(card, now)) {
                var reward = card.reward();
                var amount = reward.amount() == null ? "" : "+" + WholeAmountFormatter.formatDecimal(reward.amount());
                var language = account.getContainer(LanguageContainer.class).getLanguage().getKey();
                localizations.resolve(language, "reward-notifications.overflow", Map.of("amount", amount))
                    .getComponents().forEach(line -> player.sendMessage(line.replaceText(builder -> builder
                        .matchLiteral("%reward%").replacement(reward.name()))));
            }
        }
        session.queue.tick(now);
        if (session.task == null) {
            Session captured = session;
            var task = schedules.runForTimer(player, 1, 1, () -> tick(captured), () -> retire(captured));
            if (task.isPresent()) {
                session.task = task.get();
            } else {
                retire(session);
            }
        }
    }

    @Override
    public synchronized void clear(VexPlayer player) {
        Session session = sessions.get(player.getUniqueId());
        if (session != null) {
            retire(session);
        }
    }

    @Override
    public synchronized void close() {
        closed = true;
        List.copyOf(sessions.values()).forEach(this::retire);
    }

    private synchronized void tick(Session session) {
        if (closed || !session.matches(session.account, session.player) || !session.player.isOnline()) {
            retire(session);
            return;
        }
        session.queue.tick(session.player.getWorld().getGameTime());
        if (session.queue.isEmpty()) {
            retire(session);
        }
    }

    private synchronized void retire(Session session) {
        if (sessions.remove(session.player.getUniqueId(), session)) {
            if (session.task != null) {
                session.task.cancel();
            }
            session.queue.close();
        }
    }

    private static final class Session {

        private final VexPlayer account;
        private final Player player;
        private final UUID world;
        private final Map<ProfileDomain, PlayerProfileContext> contexts;
        private final RewardToastQueue queue;
        private VexTask task;

        private Session(VexPlayer account, Player player, RewardToastQueue queue) {
            this.account = account;
            this.player = player;
            this.queue = queue;
            world = player.getWorld().getUID();
            contexts = account.profileContexts();
        }

        private boolean matches(VexPlayer other, Player viewer) {
            return account == other && player == viewer && world.equals(viewer.getWorld().getUID())
                && contexts.equals(account.profileContexts());
        }
    }
}
