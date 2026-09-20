package dev.vexsoft.core.paper.commands;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.command.Command;
import dev.vexsoft.core.paper.command.CommandRoot;
import dev.vexsoft.core.paper.command.VexCommandSource;
import dev.vexsoft.core.paper.service.interactiveui.InteractiveUiDemoService;
import java.util.Objects;
import org.bukkit.entity.Player;

/** Exposes the opt-in interactive UI demo and its input diagnostics. */
@CommandRoot(name = "vexcore", description = "Manages VexCore")
@Dependencies(InteractiveUiDemoService.class)
public final class VexCoreInteractiveUiCommand {

    private final InteractiveUiDemoService demo;

    public VexCoreInteractiveUiCommand(final VexServiceRegistry services) {
        demo = Objects.requireNonNull(services, "services").require(InteractiveUiDemoService.class);
    }

    @Command(value = "debug ui interactive start", permission = "vexcore.command.debug.ui", playerOnly = true)
    public int start(final VexCommandSource source) {
        demo.start((Player) source.getSender());
        return 1;
    }

    @Command(value = "debug ui interactive stop", permission = "vexcore.command.debug.ui", playerOnly = true)
    public int stop(final VexCommandSource source) {
        demo.stop((Player) source.getSender());
        return 1;
    }

    @Command(value = "debug ui interactive status", permission = "vexcore.command.debug.ui", playerOnly = true)
    public int status(final VexCommandSource source) {
        demo.status((Player) source.getSender());
        return 1;
    }
}
