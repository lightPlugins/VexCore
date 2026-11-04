package dev.vexsoft.core.paper.vexui.session;

import dev.vexsoft.core.api.configuration.ConfigurationOwner;
import dev.vexsoft.core.api.configuration.VexConfiguration;
import dev.vexsoft.core.api.localization.LanguageKey;
import dev.vexsoft.core.api.localization.LocalizedMessage;
import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.configuration.ConfigurationService;
import dev.vexsoft.core.api.service.localization.LocalizationService;
import dev.vexsoft.core.api.service.player.PlayerService;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.service.messages.SendMessageService;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.command.CommandSender;

/** External contracts only; any startup access to configuration, players or presentation fails this fixture. */
@Dependencies
public final class VexUIDebugTestServices implements ConfigurationService, LocalizationService,
    PlayerService, SendMessageService {

    public VexUIDebugTestServices(final VexServiceRegistry services) {
    }

    @Override
    public ConfigurationOwner getOwner() {
        throw new UnsupportedOperationException("No configuration owner during construction");
    }

    @Override
    public VexConfiguration load(final String path) {
        throw new UnsupportedOperationException("No configuration reads during construction");
    }

    @Override
    public VexConfiguration load(final Path path) {
        return load(path.toString());
    }

    @Override
    public VexConfiguration load(final Path path, final boolean defaults) {
        return load(path.toString());
    }

    @Override
    public VexConfiguration load(final Path path, final String resource) {
        return load(path.toString());
    }

    @Override
    public Map<Path, VexConfiguration> loadDirectory(final Path path) {
        throw new UnsupportedOperationException("No configuration reads during construction");
    }

    @Override
    public LocalizedMessage resolve(final LanguageKey language, final String key, final Map<String, String> replacements) {
        throw new UnsupportedOperationException("No localization during construction");
    }

    @Override
    public void reload() {
        throw new UnsupportedOperationException("No reload during construction");
    }

    @Override
    public Optional<VexPlayer> find(final UUID player) {
        throw new UnsupportedOperationException("No player access during construction");
    }

    @Override
    public VexPlayer require(final UUID player) {
        throw new UnsupportedOperationException("No player access during construction");
    }

    @Override
    public void send(final CommandSender sender, final String key) {
        throw new UnsupportedOperationException("No messages during construction");
    }

    @Override
    public void send(final CommandSender sender, final String key, final boolean prefix) {
        send(sender, key);
    }

    @Override
    public void send(final CommandSender sender, final String key, final Map<String, String> replacements) {
        send(sender, key);
    }

    @Override
    public void send(final CommandSender sender, final String key, final boolean prefix, final Map<String, String> replacements) {
        send(sender, key);
    }
}
