# Owner melee pursuit

The Minecraft 26.2 owner-melee goal binds its configured player as the native target.
It refreshes paths throughout pursuit, including while using its ranged fallback. Rejected native
target changes prevent attacks. Melee damage requires configured reach and line of sight.
Stopping the goal clears only its own target, stops navigation, and removes its live projectiles.

## Distance leap

The existing optional velocity leap remains available for distant targets no more than one block
above the carrier. It requires grounded movement, sight and initial body clearance. It uses one
bounded impulse, pauses navigation during flight, and preserves the melee attack cooldown.
The experimental high-leap solver and side-takeoff search have been removed.

```yaml
behavior:
  owner-melee:
    damage: 10.0
    leap:
      enabled: true
      min-distance: 6.0
      cooldown-ticks: 60
      horizontal-speed: 0.8
      max-horizontal-speed: 1.3
      vertical-speed: 0.45
      water:
        enabled: true
        vertical-speed: 1.2
        horizontal-multiplier: 1.4
        swim-speed-multiplier: 1.5
    unreachable-attack:
      enabled: true
      stuck-ticks: 60
      interval-ticks: 40
      projectile:
        speed: 0.6
        lifetime-ticks: 100
```

## Unreachable targets

With water overrides enabled, pursuit uses the swim-speed multiplier while submerged. Water
launches do not require ground contact, use a stronger upward impulse and scaled horizontal
impulse (capped at 4), and can target shore up to 4 blocks above the carrier. A nearby dry target
can trigger an escape even below the normal minimum horizontal leap distance. The launch retains
collision/ceiling checks and its cooldown. Navigation waits through water exit and resumes on
landing or re-entry; a 20-tick submerged timeout resumes swimming if the initial impulse cannot
escape. No repeated midair impulses or high-platform path solver are added. Deep water and blocked
exits may still require navigation or the ranged fallback. The water block defaults to disabled
for existing configurations; bundled combat creatures enable it.

With the fallback enabled, failed or partial paths persisting for stuck-ticks activate interval
shots. A complete path with no carrier progress for that duration also activates shots. Native
pathfinding continues; a complete route with movement or reaching melee range stops new shots.
An active distance leap resets the pursuit wait. Expected failed paths do not produce repeated
warnings when the fallback is enabled. Rejected target changes remain diagnostic errors.

Projectiles use the native Shulker entity appearance with custom homing and swept target collision.
They steer toward the owner's current position at the configured speed in blocks per tick and
pass through blocks. Only their assigned player can be hit. The ProjectileHitEvent can cancel a
hit, and damage goes through the same Bukkit damage call and configured damage value as melee.
No vanilla Shulker damage or levitation is applied; launch itself deals no damage.
No item model is used for this native projectile type. No extra server sounds or particles are added.

Speed must be finite, greater than zero and at most 4. Lifetime must be 1–1200 ticks; wait and shot
interval must be positive. At most 16 projectiles per goal may remain active. Omitted fallback
configuration defaults to disabled; omitted values within it use the defaults shown above.

## Projectile lifecycle

Goal stop removes all tracked projectiles. Managed mob death, despawn, chunk removal and shutdown
already deactivate the goal through the runtime coordinator. Every projectile additionally checks
its carrier and owner each tick, removing itself on death, invalidity, target replacement, logout,
world change, creative/spectator mode or leaving the pursuit radius. Projectiles expire after their
configured lifetime and cannot load chunks. They are not saved to disk. Completed hits are one-shot.
Already launched projectiles can finish when the owner merely becomes reachable again.
Projectile visibility follows whether each viewer can see the carrier.

## Campfire navigation

Owner-melee Drowned retain the campfire correction: dry extinguished campfires use a walkable
node above their 7/16-high collision shape. Native obstacle checks and swimming remain in place.
Lit and waterlogged campfires retain native treatment.

## Deployment and verification

Replace both VexCore and ArcaneMonolith and restart. The bundled abyssal-drowned and sea-zombie
files enable the fallback. Existing deployed mob YAML needs the unreachable-attack section added
explicitly. No new resource pack is required.

Automated checks cover pursuit, ordinary leaps, blocked sight, interval shots, accepted-but-stalled
paths, recovery, owner loss, carrier death/despawn, projectile caps, homing speed, swept hits and
expiry. In-game verify a normal chase, a two-block ledge, a closed upper floor, moving targets and
mob removal with multiple projectiles in flight. Confirm each hit uses melee damage without
levitation and that the prior death-message fix still behaves correctly.
