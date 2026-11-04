package dev.vexsoft.core.paper.reward.service;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.api.service.reward.RewardService;
import dev.vexsoft.core.paper.screenui.reward.RewardCard;
import dev.vexsoft.core.paper.service.reward.RewardNotificationService;
import dev.vexsoft.core.paper.service.reward.RewardToastService;
import dev.vexsoft.core.paper.service.scheduler.ScheduleService;
import dev.vexsoft.core.paper.vexui.VexUIService;
import dev.vexsoft.core.paper.vexui.action.VexUIClickSound;
import dev.vexsoft.core.reward.notification.RewardDeliveryFeedback;
import dev.vexsoft.core.reward.notification.RewardNotification;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/** Plays committed pickup feedback and resolves the consuming plugin's reward translations and model icons. */
@Dependencies({RewardService.class, RewardToastService.class, VexUIService.class, ScheduleService.class})
public final class VexRewardNotificationService implements RewardNotificationService, AutoCloseable {

    private final AutoCloseable subscription;
    private volatile boolean enabled = true;

    public VexRewardNotificationService(VexServiceRegistry services) {
        var ui = services.require(VexUIService.class);
        var toasts = services.require(RewardToastService.class);
        var schedules = services.require(ScheduleService.class);
        subscription = services.require(RewardService.class).observeDeliveries((entries, context) -> {
            if (!enabled) {
                return;
            }
            Player viewer = context.player().findPlatformPlayer(Player.class).filter(Player::isOnline).orElse(null);
            if (viewer == null) {
                return;
            }
            var profiles = context.player().profileContexts();
            // Data-only rewards may commit away from an entity thread. All HUD/world access belongs to the viewer.
            schedules.runFor(viewer, () -> {
                if (!enabled || !viewer.isOnline() || !profiles.equals(context.player().profileContexts())
                    || context.player().findPlatformPlayer(Player.class).orElse(null) != viewer) {
                    return;
                }
                // Receipt feedback belongs to each delivery, even when the shared feed merges its quantities.
                if (RewardDeliveryFeedback.from(context) == RewardDeliveryFeedback.IMMEDIATE) {
                    viewer.playSound(VexUIClickSound.pickUp().asSound());
                }
                toasts.show(context.player(), entries.stream().map(entry -> {
                    var resolved = new RewardNotification(entry.identity(), ui.resolveText(entry.name(), viewer.locale()),
                        entry.icon(), entry.amount());
                    Key model = Key.key(entry.icon());
                    Key icon;
                    if (ui.findModelIcon(model, 32, Component.empty()).isPresent()) {
                        icon = Key.key(model.namespace(), "reward/model/" + model.value());
                    } else {
                        Key sprite = Key.key(model.namespace(), "item/" + model.value());
                        String name = ui.findIcon(sprite, 32, Component.empty()).isPresent() ? model.value() : "paper";
                        icon = Key.key("vexcore", "reward/icon/vanilla/" + name);
                    }
                    return new RewardCard(resolved, icon);
                }).toList());
            }, () -> {
            });
        });
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public void close() throws Exception {
        enabled = false;
        subscription.close();
    }
}
