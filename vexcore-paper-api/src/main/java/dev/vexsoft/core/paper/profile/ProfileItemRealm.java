package dev.vexsoft.core.paper.profile;

import dev.vexsoft.core.api.player.profile.PlayerProfileContext;
import java.util.Objects;
import org.bukkit.inventory.ItemStack;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;

/** Namespaced item provenance shared by members of one profile, independently of the game implementation. */
public final class ProfileItemRealm {

    private static final NamespacedKey DOMAIN = new NamespacedKey("vexcore", "profile_item_domain");
    private static final NamespacedKey PROFILE = new NamespacedKey("vexcore", "profile_item_id");

    private ProfileItemRealm() {
    }

    /** Binds a physical stack before spawning it into a shared world. */
    public static void bind(final ItemStack stack, final PlayerProfileContext context) {
        Objects.requireNonNull(context, "context");
        stack.editMeta(meta -> {
            var data = meta.getPersistentDataContainer();
            data.set(DOMAIN, PersistentDataType.STRING, context.domain().value());
            data.set(PROFILE, PersistentDataType.STRING, context.profileId().toString());
        });
    }

    /** Returns a stable realm or null for unbound public loot. Malformed half-tags remain bound and inaccessible. */
    public static String realm(final ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        var data = stack.getPersistentDataContainer();
        String domain = data.get(DOMAIN, PersistentDataType.STRING);
        String profile = data.get(PROFILE, PersistentDataType.STRING);
        return domain == null && profile == null ? null : domain + "/" + profile;
    }

    /** Checks provenance without network or database work. */
    public static boolean permits(final ItemStack stack, final PlayerProfileContext context) {
        String realm = realm(stack);
        return realm == null || context != null && realm.equals(context.domain().value() + "/" + context.profileId());
    }
}
