package dev.vexsoft.core.paper.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.vexsoft.core.api.service.currency.CurrencyRegistry;
import dev.vexsoft.core.api.service.registry.VexClassFactory;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.common.service.currency.CurrencyRegistryCoordinatorService;
import dev.vexsoft.core.common.service.currency.VexCurrencyRegistry;
import dev.vexsoft.core.common.service.currency.VexCurrencyRegistryCoordinatorService;
import dev.vexsoft.core.common.service.registry.DefaultServiceRegistry;
import dev.vexsoft.core.currency.CurrencyDefinition;
import dev.vexsoft.core.currency.CurrencyKey;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Reproduces delayed command binding after multiple plugins register currency facades. */
public final class CurrencySuggestionProviderTest {

    @Test
    void coreOwnedFacadeAllowsDelayedSuggestionConstructionWithMultiplePluginRegistries() {
        var registry = new DefaultServiceRegistry();
        VexServiceRegistry core = registry.scoped(() -> "VexCore");
        VexServiceRegistry gameplay = core.scoped(() -> "vexcore_gameplay");
        gameplay.register(CurrencyRegistryCoordinatorService.class, VexCurrencyRegistryCoordinatorService.class);
        gameplay.register(CurrencyRegistry.class, VexCurrencyRegistry.class);
        gameplay.registerQueuedServices();

        // Early command construction resolves the only available facade, masking the later ambiguity.
        core.require(CurrencyRegistry.class);
        VexServiceRegistry isles = registry.scoped(() -> "VexIsles");
        VexServiceRegistry essentials = registry.scoped(() -> "VexEssentials");
        isles.register(CurrencyRegistry.class, VexCurrencyRegistry.class);
        essentials.register(CurrencyRegistry.class, VexCurrencyRegistry.class);
        isles.registerQueuedServices();
        essentials.registerQueuedServices();
        var essence = isles.require(CurrencyRegistry.class).register(CurrencyDefinition.builder(
            CurrencyKey.of("vexisles", "arkie_tumblebud_essence")).build());
        essentials.require(CurrencyRegistry.class).register(CurrencyDefinition.builder(
            CurrencyKey.of("vexessentials", "quest_tokens")).build());
        assertThrows(IllegalStateException.class,
            () -> VexClassFactory.create(CurrencySuggestionProvider.class, core, "Command suggestion"));

        core.register(CurrencyRegistry.class, VexCurrencyRegistry.class);
        core.registerQueuedServices();
        var suggestions = VexClassFactory.create(CurrencySuggestionProvider.class, core, "Command suggestion");
        assertEquals(List.of("arkie_tumblebud_essence", "quest_tokens"), suggestions
            .suggest(null, new SuggestionsBuilder("", 0)).join().getList().stream()
            .map(suggestion -> suggestion.getText()).toList());
        assertSame(essence, core.require(CurrencyRegistry.class).require(essence.getKey()));
        assertThrows(IllegalStateException.class, () -> registry.require(CurrencyRegistry.class));

        isles.unregisterOwnedServices();
        assertEquals(List.of("quest_tokens"), suggestions.suggest(null, new SuggestionsBuilder("", 0))
            .join().getList().stream().map(suggestion -> suggestion.getText()).toList());
    }
}
