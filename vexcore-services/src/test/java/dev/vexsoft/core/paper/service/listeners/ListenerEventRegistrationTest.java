package dev.vexsoft.core.paper.service.listeners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.junit.jupiter.api.Test;

/** Validates Core handler signatures against Bukkit's registration contract without needing a server. */
class ListenerEventRegistrationTest {

    @Test
    void everyCoreEventHandlerHasARegistrableEventType() throws Exception {
        Path sources = Path.of("src/main/java");
        int handlers = 0;
        try (var files = Files.walk(sources)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                if (!Files.readString(file).contains("@EventHandler")) {
                    continue;
                }
                String name = sources.relativize(file).toString().replace('\\', '.').replace('/', '.');
                Class<?> listener = Class.forName(name.substring(0, name.length() - ".java".length()),
                    false, getClass().getClassLoader());
                assertTrue(Listener.class.isAssignableFrom(listener), listener.getName());
                for (Method method : listener.getDeclaredMethods()) {
                    if (!method.isAnnotationPresent(EventHandler.class)) {
                        continue;
                    }
                    handlers++;
                    assertEquals(1, method.getParameterCount(), method.toString());
                    Class<?> event = method.getParameterTypes()[0];
                    assertTrue(Event.class.isAssignableFrom(event), method.toString());
                    Method handlerList = handlerList(event);
                    assertNotNull(handlerList, () -> method + " has no static getHandlerList on its event hierarchy");
                    assertTrue(Modifier.isStatic(handlerList.getModifiers()), method.toString());
                    assertEquals(HandlerList.class, handlerList.getReturnType(), method.toString());
                }
            }
        }
        assertTrue(handlers > 0, "No Core event handlers were inspected");
    }

    private static Method handlerList(final Class<?> event) {
        for (Class<?> type = event; type != null && Event.class.isAssignableFrom(type); type = type.getSuperclass()) {
            try {
                return type.getDeclaredMethod("getHandlerList");
            } catch (NoSuchMethodException missing) {
                // Bukkit can use the handler list declared by a concrete event's superclass.
            }
        }
        return null;
    }
}
