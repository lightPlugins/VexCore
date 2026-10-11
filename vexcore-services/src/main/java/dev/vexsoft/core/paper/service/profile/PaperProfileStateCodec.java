package dev.vexsoft.core.paper.service.profile;

import dev.vexsoft.core.api.player.profile.PlayerProfileContext;
import dev.vexsoft.core.paper.profile.PaperProfileParticipant;
import dev.vexsoft.core.paper.profile.PaperProfileState;
import dev.vexsoft.core.paper.profile.ProfileItemRealm;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.NamespacedKey;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Captures and applies native inventory only on the player's entity execution context. */
final class PaperProfileStateCodec {

    private PaperProfileStateCodec() {
    }

    static PaperProfileState capture(final Player player, final PaperProfileParticipant participant) {
        var identities = new ArrayList<String>();
        ItemStack[] inventory = Objects.requireNonNull(player.getInventory().getContents(), "inventory");
        ItemStack[] enderChest = Objects.requireNonNull(player.getEnderChest().getContents(), "enderChest");
        var contents = new ArrayList<ItemStack>(Arrays.asList(inventory));
        contents.addAll(Arrays.asList(enderChest));
        contents.add(player.getItemOnCursor());
        for (ItemStack item : contents) {
            if (item != null && !item.isEmpty() && participant.persistItem(item)) {
                participant.possessionIdentity(item).ifPresent(identity -> {
                    if (item.getAmount() != 1) {
                        throw new IllegalStateException("Unique possessions cannot be stacked");
                    }
                    identities.add(identity);
                });
            }
        }
        return new PaperProfileState(
            Arrays.stream(inventory).map(item -> encode(item, participant)).toList(),
            Arrays.stream(enderChest).map(item -> encode(item, participant)).toList(),
            encode(player.getItemOnCursor(), participant), player.getInventory().getHeldItemSlot(),
            player.getLevel(), player.getTotalExperience(), player.getExp(), Math.max(0, player.getFireTicks()),
            Math.max(0, player.getRemainingAir()), player.getFoodLevel(), player.getSaturation(), player.getExhaustion(),
            player.getActivePotionEffects().stream().map(effect -> new PaperProfileState.Effect(
                effect.getType().getKey().toString(), effect.getDuration(), effect.getAmplifier(), effect.isAmbient(),
                effect.hasParticles(), effect.hasIcon())).toList(), identities, System.currentTimeMillis(),
            participant.capturePosition(player));
    }

    static void validateRealm(final Player player, final PlayerProfileContext context) {
        var contents = new ArrayList<ItemStack>(Arrays.asList(
            Objects.requireNonNull(player.getInventory().getContents(), "inventory")));
        contents.addAll(Arrays.asList(Objects.requireNonNull(player.getEnderChest().getContents(), "enderChest")));
        contents.add(player.getItemOnCursor());
        if (contents.stream().anyMatch(item -> !ProfileItemRealm.permits(item, context))) {
            throw new IllegalStateException("Native inventory contains another profile's possessions");
        }
    }

    static void apply(final Player player, final PaperProfileState state) {
        var storedInventory = state.inventory();
        int inventorySize = player.getInventory().getSize();
        for (int slot = inventorySize; slot < storedInventory.size(); slot++) {
            if (storedInventory.get(slot).length != 0) {
                throw new IllegalArgumentException("Cannot restore occupied native inventory slot " + slot
                    + " into a " + inventorySize + "-slot inventory");
            }
        }
        // Decode the entire target before mutating native state; corrupt items cannot cause a partial grant.
        ItemStack[] inventory = storedInventory.stream().map(PaperProfileStateCodec::decode).toArray(ItemStack[]::new);
        // Explicitly clear equipment slots absent from a legacy save so they cannot cross profile boundaries.
        inventory = Arrays.copyOf(inventory, inventorySize);
        ItemStack[] enderChest = state.enderChest().stream().map(PaperProfileStateCodec::decode).toArray(ItemStack[]::new);
        ItemStack cursor = decode(state.cursor());
        long now = System.currentTimeMillis();
        var effects = state.effects().stream().filter(effect -> state.remainingDuration(effect, now) != 0).map(effect -> {
            PotionEffectType type = PotionEffectType.getByKey(NamespacedKey.fromString(effect.type()));
            if (type == null) {
                throw new IllegalStateException("Stored potion effect is unavailable: " + effect.type());
            }
            return new PotionEffect(type, state.remainingDuration(effect, now), effect.amplifier(), effect.ambient(), effect.particles(),
                effect.icon());
        }).toList();
        player.getInventory().setContents(inventory);
        player.getEnderChest().setContents(enderChest);
        player.setItemOnCursor(cursor);
        player.getInventory().setHeldItemSlot(state.heldSlot());
        player.setLevel(state.level());
        player.setTotalExperience(state.totalExperience());
        player.setExp(state.experience());
        player.setFireTicks(state.fireTicks());
        player.setFallDistance(0);
        player.setRemainingAir(Math.min(player.getMaximumAir(), state.remainingAir()));
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        player.addPotionEffects(effects);
        player.setFoodLevel(state.foodLevel());
        player.setSaturation(state.saturation());
        player.setExhaustion(state.exhaustion());
    }

    private static byte[] encode(final ItemStack item, final PaperProfileParticipant participant) {
        return item == null || item.isEmpty() || !participant.persistItem(item) ? new byte[0] : item.serializeAsBytes();
    }

    private static ItemStack decode(final byte[] data) {
        return data.length == 0 ? null : ItemStack.deserializeBytes(data);
    }
}
