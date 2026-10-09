# Shared reward preparation and delivery

`RewardService` accepts both legacy sections keyed by handler type and named reward envelopes:

```yaml
legendary-bundle:
  type: items
  chance: 0.005
  announce: true
  announce-type: legendary_drop
  value:
    - key: vexisles:guardian-chestplate
      upgrade-level: 5
      amount: 1
    - key: minecraft:diamond
      amount: {min: 2, max: 4}
money:
  type: currencies
  chance: 1.0
  announce: false
  value: {example:coins: '5'}
```

Named envelopes require a numeric chance from zero to one. Each action rolls independently. Legacy sections retain their
guaranteed selection behavior. Contributions use a deterministic player/context/key selection so progression refreshes
do not reroll permanent contributions.

Call `compile`, then `prepareActions` once per logical operation. Preserve the returned `PreparedRewards` across retries.
Inside `PlayerRewardTransactionService.execute`, call `grantPrepared`; a failed report must return false. Built-in item
amounts, currency expressions, Vault amounts and level XP are frozen during preparation. Custom handlers override
`CompiledReward.prepare` to capture their own random values. A prepared selection can be executed again deliberately;
the consuming system owns operation identity and must prevent duplicate successful settlements.

The global `items` handler delegates namespaced identities to `RewardItemService`. Vanilla items are built in. Plugins
register a namespace through `RewardItemProvider` for their existing item factory, validation and styled display names.
Close the registration handle on unload. Item providers validate upgrade levels and construct the physical upgraded stack;
Core does not define a second upgrade model. All reward consumers can reuse this handler rather than adding local item grants.

`RewardItemService.deliver` inserts stacks into the native inventory first. Remaining stacks become real owner-only item
entities after successful commit. Visibility is disabled before spawning. Persistent ownership rejects other players,
mobs and hoppers and prevents merging with public or differently owned drops. Visibility is restored on entity load and
player rejoin. Transactional consumers must use the inventory-aware player transaction service.

`VexPlayer.afterCommit` queues external notifications and drops until success. `afterRollback` registers compensations
for external effects, running them in reverse order on failure. Vault coin rewards compensate successful deposits with
withdrawals if a later action fails; provider failures during compensation are logged. Ordinary player data changes
continue using the existing snapshot rollback. Custom action handlers opt into `supportsPlayerRollback` only when they
meet that contract; generic transactions cannot automatically reverse arbitrary third-party commands or remote actions.

`RewardAnnouncementService` is plugin-scoped and receives the consuming plugin's map of `RewardAnnouncement` definitions.
It groups selected entries by their original announcement type, expands every actual item/currency reward description,
and queues one localized message and one sound per type after successful commit. Pass the complete logical operation,
including all segments of a multi-block harvest, to obtain one grouped message. `announce: false` produces no announcement.

Each announcement references two localized templates: `<localization>.message` with the whole-line `%rewards%` marker and
`<localization>.formats.rewards` with `%reward%` and `%chance%` on the same line. A typical German presentation is:

```yaml
message:
  - ''
  - '  <amber-400><bold>LEGENDARY DROP</bold>'
  - ''
  - '%rewards%'
  - ' '
formats:
  rewards:
    - '  %reward% <slate-400>(%chance%)'
```

The caller validates its localization and type definitions before configuration publication. Rewards keep styled item
components and show the original per-draw percentage, including rows from multi-item bundles. Announcement definitions
are plain immutable API models; plugin content and configuration remain in the consuming plugin.

For shared world resources, `ResourceBlockService` offers namespaced capture, property validation and controlled placement.
The Vanilla provider preserves exact BlockData properties. Custom providers can recognize their blocks before Vanilla and
identify attached plants through `isAttachedTo`. Provider adapters own integration with their custom plugin's placement,
physics and native drop hooks; Core contains no Gathering content or region policy.

`AtomicJsonFile` publishes flushed local snapshots through an atomic replacement and does not recover malformed data by
guessing defaults. Callers serialize disk operations on their background executor. `BudgetedWorkQueue` and
`DeadlineWorkQueue` provide owner-thread count/time slicing and replacement scheduling without mutating heap entries.
