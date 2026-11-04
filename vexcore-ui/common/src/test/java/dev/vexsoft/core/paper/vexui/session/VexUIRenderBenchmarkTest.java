package dev.vexsoft.core.paper.vexui.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.management.ThreadMXBean;
import dev.vexsoft.core.paper.vexui.VexUIFrame;
import dev.vexsoft.core.paper.vexui.render.VexUIMetrics;
import dev.vexsoft.core.paper.vexui.render.VexUIRenderer;
import dev.vexsoft.core.paper.vexui.template.profession.VexUIProfessionTemplate;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** Opt-in, warmed renderer/JSON comparison; excludes live Paper transport, domain services and server tick time. */
@EnabledIfSystemProperty(named = "vexui.benchmark", matches = "true")
public final class VexUIRenderBenchmarkTest {

    private static final int WARMUP = 300;
    private static final int SAMPLES = 1000;

    @Test
    public void measureProfessionPages() throws Exception {
        var fixture = new VexUISessionTest.Fixture();
        var session = fixture.ui.open(fixture.player,
            new VexUIProfessionTemplate().createView(new VexUIProfessionTemplateTest.Provider()));
        fixture.flush();
        List<VexUIFrame> frames = new ArrayList<>();
        frames.add(fixture.dialogs.frames.getLast());
        for (String action : List.of("page.next", "tab.rewards", "items.next", "tab.sources", "tab.unlocks")) {
            fixture.invoke(session, action);
            frames.add(fixture.dialogs.frames.getLast());
        }
        session.close();
        fixture.flush();
        var renderer = new VexUIRenderer(new VexUIMetrics());
        var serializer = GsonComponentSerializer.gson();
        var allocation = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        allocation.setThreadAllocatedMemoryEnabled(true);
        long thread = Thread.currentThread().threadId();
        long[] rendering = new long[SAMPLES];
        long[] serialization = new long[SAMPLES];
        long bytes = 0;
        long components = 0;
        long allocated = 0;
        for (int index = -WARMUP; index < SAMPLES; index++) {
            var frame = frames.get(Math.floorMod(index, frames.size()));
            String token = "vexui/" + UUID.randomUUID() + "/" + UUID.randomUUID() + "/";
            long beforeAllocation = allocation.getThreadAllocatedBytes(thread);
            long start = System.nanoTime();
            var rendered = renderer.render(frame,
                (action, sound) -> ClickEvent.custom(Key.key("vexcore", token + action), null));
            long serializedAt = System.nanoTime();
            String json = serializer.serialize(rendered.body());
            long end = System.nanoTime();
            if (index >= 0) {
                allocated += allocation.getThreadAllocatedBytes(thread) - beforeAllocation;
                rendering[index] = serializedAt - start;
                serialization[index] = end - serializedAt;
                bytes += json.getBytes(StandardCharsets.UTF_8).length;
                components += components(rendered.body());
            }
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("scope", "Warmed profession renderer and Adventure JSON; not live Paper MSPT");
        report.put("samples", SAMPLES);
        report.put("render_ms_median", percentile(rendering, 0.5));
        report.put("render_ms_p95", percentile(rendering, 0.95));
        report.put("serialize_ms_median", percentile(serialization, 0.5));
        report.put("allocated_bytes_mean", allocated / SAMPLES);
        report.put("body_components_mean", components / SAMPLES);
        report.put("json_bytes_mean", bytes / SAMPLES);
        Path output = Path.of(System.getProperty("vexui.benchmarkReport"));
        Files.createDirectories(Objects.requireNonNull(output.toAbsolutePath().getParent()));
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(output.toFile(), report);
    }

    private static int components(final Component component) {
        int count = 1;
        for (var child : component.children()) {
            count += components(child);
        }
        return count;
    }

    private static double percentile(final long[] values, final double fraction) {
        var sorted = values.clone();
        Arrays.sort(sorted);
        return sorted[(int) ((sorted.length - 1) * fraction)] / 1_000_000.0;
    }
}
