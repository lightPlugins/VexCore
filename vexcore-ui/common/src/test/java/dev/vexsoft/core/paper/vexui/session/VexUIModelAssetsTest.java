package dev.vexsoft.core.paper.vexui.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.paper.vexui.VexUIModelAssets;
import dev.vexsoft.core.paper.vexui.VexUIService;
import dev.vexsoft.core.paper.vexui.element.VexUITexture;
import dev.vexsoft.core.paper.vexui.service.VexUIRegistration;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

/** Verifies real scoped model registration, atomic collision handling, manifest loading and owner shutdown. */
public final class VexUIModelAssetsTest {

    @Test
    public void registrationsStayOwnerLocalAndReleaseIdempotentlyWithoutRemovingReplacementAssets() {
        var fixture = new VexUISessionTest.Fixture();
        var other = fixture.services.scoped(() -> "other");
        VexUIRegistration.owner(other);
        other.registerQueuedServices();
        var second = other.require(VexUIService.class);
        Key model = Key.key("test:arkie");
        Key another = Key.key("test:another");
        var texture = new VexUITexture(Key.key("test:preview"), 0xE001, 16, 16, 17, Component.empty());
        var assets = new VexUIModelAssets(Map.of(model, Map.of(16, texture)));
        var handle = fixture.ui.registerModelAssets(assets);
        assertTrue(second.findModelIcon(model, 16, Component.empty()).isEmpty());
        assertTrue(fixture.ui.findModelIcon(model, 32, Component.empty()).isEmpty());
        var tooltip = Component.text("Arkie");
        assertEquals(tooltip, fixture.ui.findModelIcon(model, 16, tooltip).orElseThrow().tooltip());
        assertThrows(IllegalArgumentException.class, () -> fixture.ui.registerModelAssets(
            new VexUIModelAssets(Map.of(model, Map.of(16, texture), another, Map.of(16, texture)))));
        assertTrue(fixture.ui.findModelIcon(another, 16, Component.empty()).isEmpty());
        second.registerModelAssets(assets);
        handle.close();
        assertTrue(fixture.ui.findModelIcon(model, 16, Component.empty()).isEmpty());
        fixture.ui.registerModelAssets(assets);
        handle.close();
        assertFalse(fixture.ui.findModelIcon(model, 16, Component.empty()).isEmpty());
        other.unregisterOwnedServices();
        assertTrue(second.findModelIcon(model, 16, Component.empty()).isEmpty());
        assertThrows(IllegalStateException.class, () -> second.registerModelAssets(assets));
        fixture.services.unregisterOwnedServices();
    }

    @Test
    public void manifestPreservesFontMetricsAndRejectsDuplicateSizes() throws Exception {
        String entry = "key=test:arkie\npixels=16\nfont=test:vexui/models/arkie/16\npoint=57345\nadvance=14\n";
        String manifest = "format=1\ncount=1\n" + entry.lines().map(line -> "model.0." + line + "\n")
            .reduce("", String::concat);
        var assets = VexUIModelAssets.read(new ByteArrayInputStream(manifest.getBytes(StandardCharsets.UTF_8)));
        assertEquals(14, assets.models().get(Key.key("test:arkie")).get(16).advance());
        assertThrows(IllegalArgumentException.class, () -> new VexUIModelAssets(Map.of(Key.key("test:arkie"),
            Map.of(32, new VexUITexture(Key.key("test:preview"), 0xE001, 16, 16, 17, Component.empty())))));
    }
}
