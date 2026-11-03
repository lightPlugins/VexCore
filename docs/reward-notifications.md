# Animated personal reward cards

Core owns the delivery observer, shared viewer feed, card renderer, model assets and shader animation. Dependent plugins
use `RewardService` and register their item provider/presentation. Core contains no Isles-specific layout or animation.

`grantPrepared` emits a frozen receipt only when the complete selection succeeds. Observers run through
`VexPlayer.afterCommit`, including its durable-effects boundary. A later false return, exception or failed durable write
discards the notification. Receipts never reroll rewards, parse formatted quantities, create extra items or grant rewards.
The owner-scoped `RewardNotificationService` subscribes automatically, resolves the owner's registered model icons and
localized names, and unsubscribes on close. Custom actions can override `CompiledReward.notifications(context)`; the
default preserves their descriptive text with a paper icon.

Each committed delivery also plays `vexcore:ui.pick-up` from the bundled `pick-up.ogg` once on the viewer's entity
thread. Separate deliveries each play, even in the same tick or when their quantities merge into one card. Item counts
and the number of receipt entries do not multiply the cue. Rollbacks and retired sessions stay silent. Menu collection
confirmation (`vexcore:ui.collect`) remains independent.

Alternate destinations call `RewardService.notifyDelivered(prepared, context)` **inside their successful transaction**,
once, instead of `grantPrepared`. VexIsles uses this for direct pet-storage routing. Never call both for the same grant.
Rare-drop announcements remain independent: `announce: false` does not hide the personal feed.

`RewardToastService` lives in Core's scope, so plugins share one feed. Its API accepts frozen `RewardCard` values on the
viewer entity thread. It clears cards when connection, world or profile contexts change and has no repeating task while
idle. The screen coordinator coalesces packets; rendering between updates runs entirely on the client.

## Presentation

Cards reuse `VexUITheme.PANEL`, its beveled pixel frame, vanilla menu bitmap sources and static model previews. Names
retain colors and bold style and wrap to two measured lines. Cards render at scale 0.7. Right-aligned quantities render
at 1.5 times the text size and grow by another 50 percent during an addition pulse. Their column reserves the peak pulse
width and uses the exact compact formatter, including `500` and `52aa`.

Four cards are visible, newest at the bottom right, with at most 64 distinct waiting entries. Matching identity, name and
icon combine exact numeric quantities; different upgrades remain separate. Every generation has a finite lifetime even
under continuous farming. Queue overflow produces a localized chat receipt.

The shader uses client `GameTime` with partial ticks:

- Nine-tick damped spring entry with a strong overshoot, a smaller rebound, whole-card pop and 13-degree rotation.
  The size spring follows the slide: start at 84 percent, grow past normal size, rebound slightly below it, then settle
  at 100 percent. Frame, icon, text and quantity share this scale around the card center.
- One broad diagonal shine over a four-pixel frame rim; quantity updates pulse and repeat the shine.
- Six-tick smooth stack reflow with one shared pivot for frame, icon, text and quantity.
- Exit wind-up starts at tick 61: the card pulls inward, pops and rocks back before springing outward with a
  13-degree exit rotation. Opacity starts fading near tick 65 and reaches zero at tick 73; the initial rebound stays
  fully visible. All primitives share the same scale and pivot, and the card lifetime remains unchanged.

Two seven-bit clocks wrap modulo 120, which divides the 24000-tick client clock. RGB also carries 3-bit tint channels and
a pulse flag. A two-tick transport lead keeps cards invisible until their start. Reflow completes between structural
changes. Birth timestamps never reset during merging or reflow.

## Pack and verification

Install matching Core/consumer JARs and the **combined protocol-10 resource pack**. Core generates marked text, frames and
vanilla icons. `VexUIModelPackGenerator` also emits card icons for custom models. Isles retains the interactive UI's
combined shader and excludes duplicate standalone files. Arbitrary client shader packs are not automatically compatible.

`/vexcore debug ui rewards` shows samples including `500` and `52aa` without granting rewards. Repeating it exercises
quantity merging. Permission: `vexcore.command.debug.ui`.

Automated checks cover rollback/durability, observer retirement, real scoped-registry construction, shared ownership,
task cancellation, bounded bursts, finite merging, zero-net advances, clock wrapping, carrier precision, the actual menu
palette and reproducible assets.

Client acceptance still requires Minecraft 26.2: high-FPS entry/exit/shine, sustained drops and reflow, long/localized
names, large quantities, GUI scales and narrow windows, existing menus/HUDs, pet storage, failed grants, profile changes,
disconnect/reconnect and shutdown. Offline tests do not establish client visual acceptance.
