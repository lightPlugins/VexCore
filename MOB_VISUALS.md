# Viewer-only mob visuals

The existing owner-scoped MobService supports spawnVisual(viewer, definitionKey, location, scale),
moveVisual(handle, location) and removeVisual(handle). It resolves the same owned MobDefinition
catalog used by combat mobs and preserves baby state and entity-specific initializers. It does not
register a second catalog or spawn a Bukkit world entity. The native adapter only constructs spawn,
metadata, attributes, movement and removal packets for the unspawned carrier.

After initialization the carrier is neutralized using the existing NmsMobAdapterService, with AI,
gravity, sound and collisions disabled. No goals or hologram are installed. Unknown server entity IDs
cannot receive damage or produce drops. The handle is intentionally absent from combat snapshots.

Call from the viewer thread; a visual may only move within its original viewer/world. Player quit,
death, world change, world unload and owner close remove visuals and release their world references.
Callers control animation timing and must remove finished visuals. Packet movement follows ordinary
client mob interpolation, rather than display-specific shader interpolation. Allow a short settling
period before removing a visual that has just received its final position.
