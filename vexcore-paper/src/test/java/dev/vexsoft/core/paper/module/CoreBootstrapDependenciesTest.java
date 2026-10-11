package dev.vexsoft.core.paper.module;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.api.service.registry.Dependencies;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Checks actual bootstrap registration phases against managed dependencies without starting a server. */
class CoreBootstrapDependenciesTest {

    private static final Path SOURCES = Path.of("src/main/java");
    private static final Pattern EVENTS = Pattern.compile(
        "(?:coreServices|services)\\.register\\(\\s*(\\w+)\\.class\\s*,\\s*([^;]+?)\\);"
            + "|(?:coreServices|services)\\.registerQueuedServices\\(\\);"
            + "|modules\\.enable\\(new (\\w+)\\([^;]*?\\)\\);"
    );
    private static final Pattern IMPORTS = Pattern.compile("import ([\\w.]+);");

    @Test
    void everyLoadPhaseHasItsManagedDependenciesBeforeConstructingConsumers() throws Exception {
        var available = new HashSet<Class<?>>();
        verify("dev.vexsoft.core.paper.VexCorePlugin", "onLoad", available);
    }

    private static void verify(final String type, final String method, final Set<Class<?>> available) throws Exception {
        String source = Files.readString(SOURCES.resolve(type.replace('.', '/') + ".java"));
        var names = new LinkedHashMap<String, String>();
        var imports = IMPORTS.matcher(source);
        while (imports.find()) {
            String imported = imports.group(1);
            names.put(imported.substring(imported.lastIndexOf('.') + 1), imported);
        }
        String packageName = type.substring(0, type.lastIndexOf('.'));
        var queued = new LinkedHashMap<Class<?>, List<Class<?>>>();
        var events = EVENTS.matcher(body(source, method));
        while (events.find()) {
            if (events.group(1) != null) {
                Class<?> service = resolve(names, packageName, events.group(1));
                String implementation = events.group(2).strip();
                List<Class<?>> dependencies = List.of();
                if (implementation.matches("\\w+\\.class")) {
                    Class<?> concrete = resolve(names, packageName,
                        implementation.substring(0, implementation.length() - ".class".length()));
                    Dependencies annotation = concrete.getAnnotation(Dependencies.class);
                    assertTrue(annotation != null, () -> concrete.getName() + " lacks @Dependencies");
                    dependencies = List.of(annotation.value());
                }
                // Version-selected adapters are supplied at this phase; their implementations have separate tests.
                queued.put(service, dependencies);
            } else if (events.group(3) != null) {
                verify(resolve(names, packageName, events.group(3)).getName(), "enable", available);
            } else {
                flush(type, queued, available);
            }
        }
        assertTrue(queued.isEmpty(), () -> type + " leaves services queued at the end of " + method);
    }

    private static void flush(
        final String phase, final Map<Class<?>, List<Class<?>>> queued, final Set<Class<?>> available
    ) {
        while (!queued.isEmpty()) {
            var ready = new ArrayList<Class<?>>();
            queued.forEach((service, dependencies) -> {
                for (Class<?> dependency : dependencies) {
                    assertTrue(available.contains(dependency) || queued.containsKey(dependency),
                        () -> phase + ": " + service.getName() + " requires unregistered " + dependency.getName());
                }
                if (available.containsAll(dependencies)) {
                    ready.add(service);
                }
            });
            assertFalse(ready.isEmpty(), () -> phase + " has circular service dependencies: " + queued.keySet());
            available.addAll(ready);
            ready.forEach(queued::remove);
        }
    }

    private static Class<?> resolve(final Map<String, String> names, final String packageName, final String name)
        throws ClassNotFoundException {
        return Class.forName(names.getOrDefault(name, packageName + '.' + name), false,
            CoreBootstrapDependenciesTest.class.getClassLoader());
    }

    private static String body(final String source, final String method) throws IOException {
        int declaration = source.indexOf("public void " + method + '(');
        if (declaration < 0) {
            throw new IOException("Missing bootstrap method: " + method);
        }
        int opening = source.indexOf('{', declaration);
        int depth = 1;
        for (int position = opening + 1; position < source.length(); position++) {
            if (source.charAt(position) == '{') {
                depth++;
            } else if (source.charAt(position) == '}' && --depth == 0) {
                return source.substring(opening + 1, position);
            }
        }
        throw new IOException("Unclosed bootstrap method: " + method);
    }
}
