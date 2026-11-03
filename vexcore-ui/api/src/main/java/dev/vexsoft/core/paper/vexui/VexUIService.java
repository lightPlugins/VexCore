package dev.vexsoft.core.paper.vexui;

import dev.vexsoft.core.api.service.registry.VexService;
import java.util.Optional;
import java.util.Locale;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/** Opens owner-scoped VexUI sessions backed by Paper dialogs. */
public interface VexUIService extends VexService {

    /** Returns this plugin owner's default button feedback. */
    VexUIClickSound getClickSound();

    /** Changes this owner's feedback, including in existing sessions; other plugin owners are unaffected. */
    void setClickSound(VexUIClickSound sound);

    /** Returns this plugin owner's default component palette and dimensions. */
    VexUIStyleSheet getStyleSheet();

    /** Replaces the plugin theme and refreshes its active sessions. */
    void setStyleSheet(VexUIStyleSheet styleSheet);

    /** Resolves vanilla translation keys and nested arguments before glyph measurement, retaining component styling. */
    Component resolveText(Component text, Locale locale);

    /** Finds a registered flat item sprite; custom or block models require an explicit UI icon mapping. */
    Optional<VexUIItemIcon> findIcon(Key sprite, int pixels, Component tooltip);

    /** Registers build-generated model previews for this plugin owner until the returned handle closes. */
    VexUISubscription registerModelAssets(VexUIModelAssets assets);

    /** Finds a registered model preview at the requested size, suitable for any nested layout element. */
    Optional<VexUITexture> findModelIcon(Key model, int pixels, Component tooltip);

    /** Replaces this viewer's active VexUI session with a fresh view. */
    VexUISession open(Player player, VexUIView view);

    /** Finds this service owner's active session for the viewer. */
    Optional<VexUISession> find(Player player);
}
