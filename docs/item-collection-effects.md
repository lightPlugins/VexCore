# Personal item collection flights

`ItemCollectionEffectService` is registered once in Core's root scope. Consumers inherit that same instance, so the
global budget is shared across plugins. It reuses Core's viewer-only `ItemDisplayPacketService`; no Bukkit entities or
physical item drops are created. Call `play(viewer, request)` on the viewer's entity scheduler after committing rewards.
Use the advisory `hasCapacity(viewer)` check before constructing display items for large batches; `play` still enforces
the limits atomically, since another viewer may consume global capacity between those calls.

`ItemCollectionRequest` copies its origin and item and accepts an entity-thread validity guard plus a thread-safe
`CollectionBlockView`. The latter supplies personal virtual block snapshots, or null for real terrain. A physical
collision is conservatively tested using its bounding box; non-air virtual replacements occupy a full voxel. The
rotating model's swept radius is derived from its 0.38 display scale. Fences and walls are checked from the supporting
block row as well.

Five possible exterior launch surfaces are considered, preferring faces toward the viewer. The display starts outside
the block and travels about one block outward, with an independent spread for each block in a batch. It holds its own
apex for ten ticks (half a second), continuing to spin and emit particles, before beginning the slower homing phase.
From there it steers toward the viewer's moving chest position with a stable random bend, height, rotation axis and phase. Spin is
two to three revolutions per second. The item shrinks near arrival and emits a quiet amethyst chime with a pale accent.
Dust defaults to violet `#8b5cf6` with `#ddd6fe` accents. Consumers can supply different colors.

## Work and lifecycle bounds

- At most 64 visible/starting flights per viewer and 512 globally. Additional blocks use a bounded waiting list instead
  of being discarded by the old four-start limit: 1024 total requests per viewer and 4096 globally. At most sixteen
  world/surface jobs start for one viewer per two-tick bucket. Pending and visible requests both consume capacity.
- One entity timer per active viewer, running every tick. Empty sessions cancel their timer and are discarded.
- Client teleport/transformation interpolation normally uses one tick, adapting up to six ticks after delayed region
  handoffs. Locally owned space avoids both region and viewer handoffs, keeping the common path synchronous.
- Each moving tick emits up to four trail clusters with two particles each, with wider spacing than before. Dense
  batches share a budget of 96 spatial samples per viewer per tick. Hovering emits one particle per item per tick,
  keeping rotation active without redundant position packets. Motion speeds and the half-second hold use tick-based
  units, so the faster cadence does not accelerate the flight.
- Every collision query considers at most 96 blocks in already loaded, scheduler-owned chunks. A foreign-region or
  unavailable volume is rejected. World reads execute only in their owning region, using a region task when needed.
- Free exterior surfaces are preferred. An enclosed block falls back to its own center and a distinct outward offset;
  cosmetic flights may pass through terrain rather than disappear. Unloaded or foreign-region volumes remain rejected.
- Active flights end after 120 ticks or six wall-clock seconds; waiting requests expire after ten seconds. Forty-block
  distance and sustained separation guards remain. Slower stationary homing adds part of the viewer's movement to
  catch normal sprinting, with a maximum catch-up speed of 16 blocks per second. Large viewer jumps retire the session.
- Death, logout, world/profile/session changes, shutdown and late callbacks cannot resurrect displays. Pending surface
  checks also consume capacity, and retired region callbacks skip their world work.

Reward pickup audio is independent of these cosmetic flights. VexIsles keeps normal immediate delivery feedback for
every successful grant, whether a flight is visible, waiting or skipped. The effect only adds an amethyst chime on
arrival and never plays fallback pickup audio. Callers must not grant rewards at arrival.

VexIsles submits one request per block for direct player harvesting, area harvesting and thrown tools. Pet gathering retains its
existing immediate receipt audio and does not launch collection flights. Native block models are preferred; blocks
without an item representation use their placement item; the normal reward cue still plays when no model exists.

Serverless tests cover scoped registry construction and consumer factory access, shared pending/active budgets,
walking/sprinting homing, interpolation/spin, private terrain, blocked/unloaded/foreign-region paths, timer shutdown,
profile retirement, death, logout, world changes, teleport and late callbacks. Minecraft acceptance still requires
checking the visual trajectory, perceived interpolation, trail density and sound mix under real gathering load.
