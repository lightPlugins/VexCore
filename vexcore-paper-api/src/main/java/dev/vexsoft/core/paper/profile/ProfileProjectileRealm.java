package dev.vexsoft.core.paper.profile;

import dev.vexsoft.core.api.player.profile.PlayerProfileContext;
import org.bukkit.entity.Projectile;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;

/** Captures optional profile provenance on a projectile without retaining players or querying storage. */
public final class ProfileProjectileRealm {

    private static final NamespacedKey REALM = new NamespacedKey("vexcore", "projectile_profile");
    private static final NamespacedKey CONTEXT = new NamespacedKey("vexcore", "projectile_context");

    private ProfileProjectileRealm() {
    }

    /** Records the launching session on the projectile's execution context. */
    public static void bind(final Projectile projectile, final PlayerProfileContext context) {
        var data = projectile.getPersistentDataContainer();
        data.set(REALM, PersistentDataType.STRING, realm(context));
        data.set(CONTEXT, PersistentDataType.STRING, identity(context));
    }

    /** Rejects delayed damage from a switched, disconnected or readmitted launching session. */
    public static boolean current(final Projectile projectile, final PlayerProfileContext context) {
        var data = projectile.getPersistentDataContainer();
        String captured = data.get(CONTEXT, PersistentDataType.STRING);
        return context == null ? captured == null && !data.has(REALM)
            : realm(context).equals(data.get(REALM, PersistentDataType.STRING)) && identity(context).equals(captured);
    }

    /** Permits public arrows or members of the same shared profile to recover a launched physical arrow. */
    public static boolean permitsPickup(final Projectile projectile, final PlayerProfileContext context) {
        var data = projectile.getPersistentDataContainer();
        String captured = data.get(REALM, PersistentDataType.STRING);
        return captured == null ? !data.has(CONTEXT) : context != null && captured.equals(realm(context));
    }

    private static String realm(final PlayerProfileContext context) {
        return context.domain().value() + '/' + context.profileId();
    }

    private static String identity(final PlayerProfileContext context) {
        return context.playerId() + "/" + context.sessionId() + "/" + context.generation() + "/"
            + context.membershipEpoch() + "/" + context.fence();
    }
}
