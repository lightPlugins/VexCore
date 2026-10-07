package dev.vexsoft.core.paper.service.inventory;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.stream.Stream;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.inventory.MenuType;

/** Supplies only inert menu identities needed to initialize Paper's inventory enums in serverless tests. */
public final class InventoryTestRegistryAccess implements RegistryAccess {

    @Override
    public <T extends Keyed> Registry<T> getRegistry(final Class<T> type) {
        return registry();
    }

    @Override
    public <T extends Keyed> Registry<T> getRegistry(final RegistryKey<T> key) {
        return registry();
    }

    @SuppressWarnings("unchecked")
    private <T extends Keyed> Registry<T> registry() {
        return (Registry<T>) Proxy.newProxyInstance(Registry.class.getClassLoader(), new Class<?>[]{Registry.class},
            (object, method, arguments) -> switch (method.getName()) {
                case "get", "getOrThrow" -> Proxy.newProxyInstance(MenuType.Typed.class.getClassLoader(),
                    new Class<?>[]{MenuType.Typed.class}, (menu, operation, values) -> {
                        if (operation.getName().equals("getKey")) {
                            return NamespacedKey.fromString(arguments[0].toString());
                        }
                        return null;
                    });
                case "iterator" -> Collections.emptyIterator();
                case "stream", "keyStream" -> Stream.empty();
                case "size" -> 0;
                default -> null;
            });
    }
}
