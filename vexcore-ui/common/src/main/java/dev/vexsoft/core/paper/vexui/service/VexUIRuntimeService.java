package dev.vexsoft.core.paper.vexui.service;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.service.scheduler.ScheduleService;
import dev.vexsoft.core.paper.vexui.VexUIModelAssets;
import dev.vexsoft.core.paper.vexui.VexUIService;
import dev.vexsoft.core.paper.vexui.VexUISession;
import dev.vexsoft.core.paper.vexui.VexUIView;
import dev.vexsoft.core.paper.vexui.action.VexUIClickSound;
import dev.vexsoft.core.paper.vexui.dialog.VexUIDialogService;
import dev.vexsoft.core.paper.vexui.element.VexUIItemIcon;
import dev.vexsoft.core.paper.vexui.element.VexUITexture;
import dev.vexsoft.core.paper.vexui.render.VexUIMetrics;
import dev.vexsoft.core.paper.vexui.render.VexUIRenderer;
import dev.vexsoft.core.paper.vexui.render.VexUITranslations;
import dev.vexsoft.core.paper.vexui.session.VexUICoordinatorService;
import dev.vexsoft.core.paper.vexui.session.VexUISessionState;
import dev.vexsoft.core.paper.vexui.state.VexUISubscription;
import dev.vexsoft.core.paper.vexui.style.VexUIStyleSheet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.Getter;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/** Opens sessions for the registry owner and retires them when that service scope closes. */
@Dependencies({VexUICoordinatorService.class, ScheduleService.class, VexUIDialogService.class})
public final class VexUIRuntimeService implements VexUIService, AutoCloseable {

    private final ServiceOwner owner;
    private final VexUICoordinatorService coordinator;
    private final ScheduleService scheduler;
    private final VexUIDialogService dialogs;
    private final VexUIMetrics metrics = new VexUIMetrics();
    private final VexUIRenderer renderer = new VexUIRenderer(metrics);
    private final Map<Key, Map<Integer, VexUITexture>> models = new HashMap<>();
    @Getter
    private volatile VexUIClickSound clickSound = VexUIClickSound.defaults();
    @Getter
    private volatile VexUIStyleSheet styleSheet = VexUIStyleSheet.defaults();
    private volatile boolean closed;

    public VexUIRuntimeService(final VexServiceRegistry services) {
        owner = services.getOwner();
        coordinator = services.require(VexUICoordinatorService.class);
        scheduler = services.require(ScheduleService.class);
        dialogs = services.require(VexUIDialogService.class);
        // Warm bundled dictionaries during service creation rather than the first player's menu click.
        VexUITranslations.render(Component.empty(), Locale.ENGLISH);
    }

    @Override
    public void setClickSound(final VexUIClickSound sound) {
        clickSound = Objects.requireNonNull(sound, "sound");
    }

    @Override
    public void setStyleSheet(final VexUIStyleSheet value) {
        styleSheet = Objects.requireNonNull(value, "styleSheet");
        coordinator.refreshOwned(owner);
    }

    @Override
    public Component resolveText(final Component text, final Locale locale) {
        return VexUITranslations.render(Objects.requireNonNull(text, "text"), Objects.requireNonNull(locale, "locale"));
    }

    @Override
    public Optional<VexUIItemIcon> findIcon(final Key sprite, final int pixels, final Component tooltip) {
        var icon = new VexUIItemIcon(sprite, pixels, tooltip);
        return metrics.hasIcon(sprite, pixels) ? Optional.of(icon) : Optional.empty();
    }

    @Override
    public synchronized VexUISubscription registerModelAssets(final VexUIModelAssets assets) {
        if (closed) {
            throw new IllegalStateException("VexUI owner closed");
        }
        if (assets.models().keySet().stream().anyMatch(models::containsKey)) {
            throw new IllegalArgumentException("A model preview is already registered for this owner");
        }
        models.putAll(assets.models());
        coordinator.refreshOwned(owner);
        var released = new AtomicBoolean();
        return () -> {
            if (released.compareAndSet(false, true)) {
                synchronized (this) {
                    assets.models().forEach((key, sizes) -> models.remove(key, sizes));
                }
                coordinator.refreshOwned(owner);
            }
        };
    }

    @Override
    public synchronized Optional<VexUITexture> findModelIcon(final Key model, final int pixels, final Component tooltip) {
        var sizes = models.get(model);
        var texture = sizes == null ? null : sizes.get(pixels);
        return texture == null ? Optional.empty() : Optional.of(new VexUITexture(texture.fontPrefix(),
            texture.codePoint(), texture.width(), texture.height(), texture.advance(), tooltip));
    }

    @Override
    public VexUISession open(final Player player, final VexUIView view) {
        if (closed) {
            throw new IllegalStateException("VexUI service is closed");
        }
        var session = new VexUISessionState(owner, Objects.requireNonNull(player, "player"),
            Objects.requireNonNull(view, "view"), coordinator, scheduler, renderer, dialogs,
            this::getClickSound, this::getStyleSheet);
        try {
            coordinator.replace(session);
        } catch (RuntimeException failure) {
            session.retire(false);
            throw failure;
        }
        if (closed) {
            session.close();
            throw new IllegalStateException("VexUI owner closed while opening a session");
        }
        session.refresh();
        return session;
    }

    @Override
    public Optional<VexUISession> find(final Player player) {
        return coordinator.find(owner, player.getUniqueId());
    }

    @Override
    public void close() {
        closed = true;
        coordinator.closeOwned(owner);
        synchronized (this) {
            models.clear();
        }
    }
}
