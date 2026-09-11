package dev.vexsoft.core.paper.commands;

import dev.vexsoft.core.api.localization.LanguageContainer;
import dev.vexsoft.core.api.service.localization.LocalizationService;
import dev.vexsoft.core.api.service.player.PlayerService;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.command.Command;
import dev.vexsoft.core.paper.command.CommandRoot;
import dev.vexsoft.core.paper.command.VexCommandSource;
import dev.vexsoft.core.paper.screenui.DialoguePanelLayout;
import dev.vexsoft.core.paper.screenui.HorizontalAlignment;
import dev.vexsoft.core.paper.screenui.ScreenAnchor;
import dev.vexsoft.core.paper.screenui.ScreenUi;
import dev.vexsoft.core.paper.screenui.TextBlockLayout;
import dev.vexsoft.core.paper.screenui.VerticalAlignment;
import dev.vexsoft.core.paper.service.messages.SendMessageService;
import dev.vexsoft.core.paper.service.screenui.ScreenUiService;
import java.util.Locale;
import java.util.Map;
import org.bukkit.entity.Player;

/** Opt-in visual acceptance test; does not change gameplay or ArcaneMonolith dialogs. */
@CommandRoot(name = "vexcore", description = "Manages VexCore")
@Dependencies({ScreenUiService.class, LocalizationService.class, PlayerService.class, SendMessageService.class})
public final class VexCoreUiCommand {

    private final ScreenUiService screens;
    private final LocalizationService localization;
    private final PlayerService players;
    private final SendMessageService messages;

    public VexCoreUiCommand(VexServiceRegistry services) {
        screens = services.require(ScreenUiService.class);
        localization = services.require(LocalizationService.class);
        players = services.require(PlayerService.class);
        messages = services.require(SendMessageService.class);
    }

    @Command(value = "debug ui dialogue", permission = "vexcore.command.debug.ui", playerOnly = true)
    public int dialogue(VexCommandSource source) {
        Player player = (Player) source.getSender();
        var existing = screens.find(player, "debug-dialogue");

        if (existing.isPresent()) {
            existing.get().close();
            messages.send(player, "screen-ui.disabled", true);

            return 1;
        }

        var language =
            players.require(player.getUniqueId()).getContainer(LanguageContainer.class).getLanguage().getKey();
        var speaker =
            localization.resolve(language, "screen-ui.dialogue-demo.speaker", Map.of()).getComponents().getFirst();
        var paragraphs = localization.resolve(language, "screen-ui.dialogue-demo.body", Map.of()).getComponents();
        var prepared = screens.prepareDialogue(speaker, paragraphs, DialoguePanelLayout.defaults());
        var panel = screens.openDialogue(player, "debug-dialogue", prepared);

        try {
            panel.setHint(localization.resolve(language, "screen-ui.dialogue-demo.hint", Map.of())
                .getComponents()
                .getFirst());
            panel.show(0, prepared.pages().getFirst().codePoints());
            messages.send(player, "screen-ui.enabled", true);

            return 1;
        } catch (RuntimeException failure) {
            panel.close();
            throw failure;
        }
    }

    @Command(value = "debug ui toggle", permission = "vexcore.command.debug.ui", playerOnly = true)
    public int toggle(VexCommandSource source) {
        Player player = (Player) source.getSender();
        var existing = screens.find(player, "debug-ui");

        if (existing.isPresent()) {
            existing.get().close();
            messages.send(player, "screen-ui.disabled", true);

            return 1;
        }

        var language =
            players.require(player.getUniqueId()).getContainer(LanguageContainer.class).getLanguage().getKey();
        ScreenUi ui = screens.open(player, "debug-ui");

        try {
            for (ScreenAnchor anchor : ScreenAnchor.values()) {
                int column = anchor.ordinal() % 3;
                int row = anchor.ordinal() / 3;
                HorizontalAlignment horizontal = HorizontalAlignment.values()[column];
                VerticalAlignment vertical = VerticalAlignment.values()[row];
                int x = column == 0 ? 8 : column == 2 ? -8 : 0;
                int y = row == 0 ? 8 : row == 2 ? -8 : 0;
                String name = anchor.name().toLowerCase(Locale.ROOT);

                ui.textBlock(
                    name,
                    localization.resolve(language, "screen-ui.anchors." + name, Map.of()).getComponents(),
                    TextBlockLayout.builder()
                        .anchor(anchor)
                        .x(x)
                        .y(y)
                        .maxWidth(100)
                        .horizontalAlignment(horizontal)
                        .verticalAlignment(vertical)
                        .layer(10)
                        .build()
                );
            }

            messages.send(player, "screen-ui.enabled", true);

            return 1;
        } catch (RuntimeException failure) {
            ui.close();
            throw failure;
        }
    }
}

