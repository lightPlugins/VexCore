package dev.vexsoft.core.paper.service.interactiveui;

import dev.vexsoft.core.api.localization.LanguageContainer;
import dev.vexsoft.core.api.localization.LanguageKey;
import dev.vexsoft.core.api.service.localization.LocalizationService;
import dev.vexsoft.core.api.service.localization.ThemeColorService;
import dev.vexsoft.core.api.service.player.PlayerService;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.interactiveui.InteractiveUi;
import dev.vexsoft.core.paper.interactiveui.InteractiveUiElement;
import dev.vexsoft.core.paper.interactiveui.InteractiveUiInput;
import dev.vexsoft.core.paper.interactiveui.UiBounds;
import dev.vexsoft.core.paper.service.messages.SendMessageService;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

/** Demonstrates click, pointer capture, dragging and slider input through the public UI API. */
@Dependencies({
    InteractiveUiService.class,
    LocalizationService.class,
    ThemeColorService.class,
    PlayerService.class,
    SendMessageService.class
})
public final class VexInteractiveUiDemoService implements InteractiveUiDemoService {

    private static final String SCREEN_ID = "debug-interactive-ui";

    private final InteractiveUiService screens;
    private final LocalizationService localization;
    private final ThemeColorService colors;
    private final PlayerService players;
    private final SendMessageService messages;

    public VexInteractiveUiDemoService(final VexServiceRegistry services) {
        VexServiceRegistry checkedServices = Objects.requireNonNull(services, "services");

        screens = checkedServices.require(InteractiveUiService.class);
        localization = checkedServices.require(LocalizationService.class);
        colors = checkedServices.require(ThemeColorService.class);
        players = checkedServices.require(PlayerService.class);
        messages = checkedServices.require(SendMessageService.class);
    }

    @Override
    public void start(final Player player) {
        Objects.requireNonNull(player, "player");

        if (screens.find(player, SCREEN_ID).isPresent()) {
            messages.send(player, "interactive-ui.already-active", true);
            return;
        }

        if (player.getGameMode() != GameMode.SURVIVAL) {
            messages.send(player, "interactive-ui.requires-survival", true);
            return;
        }

        if (player.isInsideVehicle()) {
            messages.send(player, "interactive-ui.requires-on-foot", true);
            return;
        }

        if (!player.isOnGround()) {
            messages.send(player, "interactive-ui.requires-ground", true);
            return;
        }

        if (!player.getInventory().getItemInMainHand().getType().isAir()) {
            messages.send(player, "interactive-ui.requires-empty-hand", true);
            return;
        }

        LanguageKey language = players.require(player.getUniqueId())
            .getContainer(LanguageContainer.class)
            .getLanguage()
            .getKey();
        InteractiveUi screen;

        try {
            screen = screens.open(player, SCREEN_ID);
        } catch (IllegalStateException failure) {
            messages.send(player, "interactive-ui.unavailable", true);
            return;
        }

        try {
            Demo demo = new Demo(screen, language);

            demo.render();
            screen.onInput(demo::accept);
            messages.send(player, "interactive-ui.started", true);
        } catch (RuntimeException failure) {
            screen.close();
            throw failure;
        }
    }

    @Override
    public void stop(final Player player) {
        var screen = screens.find(Objects.requireNonNull(player, "player"), SCREEN_ID);

        if (screen.isEmpty()) {
            messages.send(player, "interactive-ui.inactive", true);
            return;
        }

        screen.get().close();
        messages.send(player, "interactive-ui.stopped", true);
    }

    @Override
    public void status(final Player player) {
        var screen = screens.find(Objects.requireNonNull(player, "player"), SCREEN_ID);

        if (screen.isEmpty()) {
            messages.send(player, "interactive-ui.inactive", true);
            return;
        }

        var state = screen.get().state();

        messages.send(
            player,
            state.pressed() ? "interactive-ui.status.pressed" : "interactive-ui.status.released",
            true,
            Map.of(
                "x", String.format(Locale.ROOT, "%.1f", state.x()),
                "y", String.format(Locale.ROOT, "%.1f", state.y()),
                "inputs", Long.toString(state.receivedInputs())
            )
        );
    }

    @RequiredArgsConstructor
    private final class Demo {

        private static final double CARD_WIDTH = 112;
        private static final double CARD_HEIGHT = 40;
        private static final double SLIDER_X = 24;
        private static final double SLIDER_WIDTH = 272;

        private final InteractiveUi screen;
        private final LanguageKey language;
        private int count;
        private int sliderValue = 50;
        private double cardX = 104;
        private double cardY = 72;
        private double dragOffsetX;
        private double dragOffsetY;

        private void render() {
            put("title", new UiBounds(16, 10, 288, 20), "title", "zinc", 10, false);
            renderCounter();
            put("reset", new UiBounds(120, 38, 80, 22), "reset", "zinc", 8, true);
            put("close", new UiBounds(216, 38, 80, 22), "close", "red", 9, true);
            renderCard();
            renderSlider();
            put("hint", new UiBounds(16, 162, 288, 12), "hint", "zinc", 10, false);
        }

        private void accept(final InteractiveUiInput input) {
            if (screen.isClosed()) {
                return;
            }

            String target = input.targetId();

            if (target == null) {
                return;
            }

            switch (input.kind()) {
                case PRESS -> {
                    if (target.equals("card")) {
                        dragOffsetX = input.x() - cardX;
                        dragOffsetY = input.y() - cardY;
                    } else if (target.equals("slider")) {
                        updateSlider(input.x());
                    }
                }
                case DRAG -> {
                    if (target.equals("card")) {
                        cardX = Math.clamp(input.x() - dragOffsetX, 16, 304 - CARD_WIDTH);
                        cardY = Math.clamp(input.y() - dragOffsetY, 16, 160 - CARD_HEIGHT);
                        renderCard();
                    } else if (target.equals("slider")) {
                        updateSlider(input.x());
                    }
                }
                case CLICK -> click(target);
                default -> {
                }
            }
        }

        private void click(final String target) {
            switch (target) {
                case "counter" -> {
                    count++;
                    renderCounter();
                }
                case "reset" -> {
                    count = 0;
                    sliderValue = 50;
                    cardX = 104;
                    cardY = 72;
                    renderCounter();
                    renderCard();
                    renderSlider();
                }
                case "close" -> screen.close();
                default -> {
                }
            }
        }

        private void updateSlider(final double x) {
            int value = (int) Math.round(Math.clamp((x - SLIDER_X) / SLIDER_WIDTH, 0, 1) * 100);

            if (value == sliderValue) {
                return;
            }

            sliderValue = value;
            renderSlider();
        }

        private void renderCounter() {
            screen.put(new InteractiveUiElement(
                "counter",
                new UiBounds(24, 38, 80, 22),
                text("counter", Map.of("count", Integer.toString(count))),
                color("lime", 9),
                true
            ));
        }

        private void renderCard() {
            put("card", new UiBounds(cardX, cardY, CARD_WIDTH, CARD_HEIGHT), "card", "sky", 9, true);
        }

        private void renderSlider() {
            screen.put(new InteractiveUiElement(
                "slider-label",
                new UiBounds(24, 120, 272, 12),
                text("slider", Map.of("value", Integer.toString(sliderValue))),
                color("zinc", 10),
                false
            ));
            screen.put(new InteractiveUiElement(
                "slider",
                new UiBounds(SLIDER_X, 138, SLIDER_WIDTH, 18),
                Component.empty(),
                color("zinc", 8),
                true
            ));
            screen.put(new InteractiveUiElement(
                "slider-thumb",
                new UiBounds(SLIDER_X + sliderValue / 100.0 * (SLIDER_WIDTH - 8), 136, 8, 22),
                Component.empty(),
                color("sky", 6),
                false
            ));
        }

        private void put(
            final String id,
            final UiBounds bounds,
            final String key,
            final String color,
            final int shade,
            final boolean interactive
        ) {
            screen.put(new InteractiveUiElement(id, bounds, text(key, Map.of()), color(color, shade), interactive));
        }

        private Component text(final String key, final Map<String, String> replacements) {
            return localization.resolve(language, "interactive-ui.demo." + key, replacements).getComponent();
        }

        private int color(final String name, final int shade) {
            return colors.requireColor("tailwind", name, shade).value();
        }
    }
}
