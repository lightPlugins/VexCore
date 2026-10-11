<div align="center">
  <h1>VexCore</h1>
  <p><strong>The shared infrastructure behind the VexSoft plugin ecosystem</strong></p>
  <p>
    <img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=coffeescript&logoColor=white">
    <img alt="Paper 26.2" src="https://img.shields.io/badge/Paper-26.2-222222?logo=paperlessngx&logoColor=white">
    <img alt="Folia supported" src="https://img.shields.io/badge/Folia-supported-5C6BC0?logo=leaflet&logoColor=white">
    <img alt="Velocity 4.1" src="https://img.shields.io/badge/Velocity-4.1-1198D4?logo=velocity&logoColor=white">
    <img alt="Status" src="https://img.shields.io/badge/Status-Active_development-2E7D32?logo=githubactions&logoColor=white">
    <a href="https://github.com/lightPlugins/VexCore/actions/workflows/build.yml"><img alt="Build" src="https://github.com/lightPlugins/VexCore/actions/workflows/build.yml/badge.svg"></a>
    <a href="https://www.codefactor.io/repository/github/lightplugins/vexcore"><img alt="CodeFactor" src="https://www.codefactor.io/repository/github/lightplugins/vexcore/badge"></a>
  </p>
</div>

VexCore brings together the technical foundations that would otherwise have to be built and maintained separately in every project. It gives VexSoft plugins a consistent environment and allows them to work together without tightly coupling their implementations.

> [!IMPORTANT]
> VexCore is infrastructure, not a gameplay plugin. Items, skills, quests, and general server features remain in their own projects.

## At a Glance

| Foundation | Player-facing systems | Runtime | Compatibility |
| --- | --- | --- | --- |
| Scoped service registries | Localization and placeholders | Paper, Folia, and Velocity | Minecraft version adapters |
| Server and proxy plugin lifecycles | Commands and inventories | Player data, stats, and caching | Packet abstraction |
| Configuration and messaging | Rewards, costs, and requirements | SQLite and PostgreSQL persistence | Data Component abstraction |

## Purpose

Every VexSoft plugin receives its own isolated service scope. Paper plugins and Velocity plugins use their own platform registry, while shared contracts keep their structure consistent without exposing concrete implementations.

VexCore also manages the shared plugin lifecycle. Services, commands, listeners, inventories, message handlers, and data containers follow the same registration rules and are released in a controlled way when a plugin shuts down. A typed messaging layer connects backend servers through the Velocity proxy when communication has to cross process boundaries.

The goal is simple: provide a modern and predictable foundation for VexSoft plugins without rebuilding the same infrastructure in every project.

```mermaid
flowchart TD
    Core["Shared VexCore contracts"]
    Paper["VexCore Paper"]
    Velocity["VexCore Velocity"]
    PaperRegistry["Paper service registry"]
    ProxyRegistry["Velocity service registry"]
    Messaging["Typed messaging bridge"]
    Items["VexItems"]
    Skills["VexSkills"]
    Essentials["VexEssentials"]
    ProxyPlugin["Vex proxy plugins"]

    Core --> Paper
    Core --> Velocity
    Paper --> PaperRegistry
    Velocity --> ProxyRegistry
    PaperRegistry --> Items
    PaperRegistry --> Skills
    PaperRegistry --> Essentials
    ProxyRegistry --> ProxyPlugin
    PaperRegistry <--> Messaging
    Messaging <--> ProxyRegistry
```

> [!NOTE]
> Each plugin owns an isolated service scope. Shared contracts remain accessible through VexCore while plugin-specific implementations stay separate.

## Features

### Service Registry

- Owner- and plugin-scoped services
- Hierarchical resolution between plugins and internal modules
- Lazy references for services that become available later
- Class-based creation of service implementations
- Dependency validation and ordered initialization
- Safe rollback when a service cannot be created
- Controlled cleanup of every service owned by a plugin

### Shared Plugin Foundation

- Consistent lifecycles through `VexPlugin` and `VexProxyPlugin`
- Central registration of services, commands, listeners, and inventories
- An isolated service registry for each plugin
- Configurable and colored console prefixes
- Automatic connection to the local Paper or Velocity infrastructure
- Clear separation between public APIs and runtime implementations

### Cross-Server Messaging

- Typed messages shared by Paper and Velocity plugins
- Versioned and validated message envelopes
- JSON payloads without Java serialization
- Targets for the proxy, individual servers, players, or the complete network
- Owner and source-server metadata for received messages
- Class-based message handler registration through scoped services
- Short-lived pending delivery for temporarily empty backend servers
- A built-in proxy ping diagnostic with localized results
- Velocity-backed online-player directory lookups
- Cross-server player transfers with destination-server arrival handoff

> [!NOTE]
> Paper and Velocity run in separate processes and therefore keep separate service registries. Messaging connects those registries without pretending that Java service instances can be shared across process boundaries.

### Configuration System

- YAML configurations powered by Configurate
- A shared directory structure under `plugins/VexSoft/PluginName`
- Bundled default files and user-defined configurations
- Structured sections and typed values
- Readable warnings for invalid or broken files

### Localization System

- Adventure Components instead of plain text messages
- Any number of language files per plugin and language
- English defaults with automatic fallback behavior
- Support for both single messages and multi-line lists
- Placeholders, prefixes, and direct message delivery
- One centrally stored player language shared by all VexSoft plugins
- Language files can be reloaded while the server is running

### Placeholder System

- Every placeholder is resolved from a loaded `VexPlayer`
- Class-based registration with automatic plugin namespaces
- Dynamic argument paths such as `%vexskills_skill_mining_level%`
- Request-local placeholders such as `%level%` for one expression or render operation
- Cached templates avoid reparsing frequently rendered text
- PlaceholderAPI support in both directions when it is installed

### Stats and Progression Primitives

- Dynamically registered, namespaced stats with stable persistence keys
- Array-backed values for loaded players and retained database values while a stat is unloaded
- Permanent values and temporary flat or multiplicative modifiers
- Stat names and descriptions resolved from the owning plugin's language files
- Owner-scoped providers for reconstructable runtime stat contributions
- Automatic contribution rebuilds after player data loads and explicit refreshes after plugin reloads
- Complete contribution snapshots replace old values without persisting derived final stats
- Extensible `rewards`, `costs`, and `requirements` sections compiled once during configuration reload
- Built-in Stats support, Vault-backed Coins support, and online Permission requirements
- Shared expression variables such as `%level%` without coupling VexCore to a skill system

### Trading and Reward Transactions

`ItemSaleSession` completes sales on the owning player thread and queues player-data saves asynchronously.
It uses no native crash journal, forced `Player.saveData()` calls or database waits. Closing a menu returns
its input items and cursor to the player; inventory overflow and death returns become ordinary world drops.
The legacy `checkpoint()` and `recover()` hooks remain as deprecated no-ops for existing callers.

`VexPlayer.atomic` snapshots a persistent container only before its first write in the operation. Failed
operations restore those touched containers and discard deferred notifications. Data must be changed
through `update`, `updateIfChanged` or `reset`, rather than mutating values obtained from a read.

`PlayerRewardTransactionService.execute(player, operation, slots)` copies only the declared inventory slots.
Slot `-1` includes the cursor; an empty array executes a data-only transaction without native inventory access.
Core currency and XP rewards declare `changesInventory() == false`, so their level claims skip inventory snapshots.
The original unrestricted overload preserves rollback for callbacks that may mutate any native slot.
Custom rewards default to changing inventory until their implementation declares otherwise.

### Rewards, Costs, and Requirements

Progression remains owned by external plugins. VexCore does not know what a skill level or
collection level is; it compiles and executes the three sections supplied by those plugins.
Installed plugins can register additional direct keys independently in each domain.

```yaml
rewards:
  coins: "200 * %level%"
  stats:
    mining_chunk_damage: "1"
    defense: "(2 * %level%) / 5"

costs:
  coins: "50 * %level%"

requirements:
  coins: "1000"
  stats:
    mining_power: "2 * %level%"
  permission:
    - "vexskills.mining"
    - "vexskills.mining.advanced"
```

- Reward keys are registered through `RewardRegistry`
- Cost keys are registered through `CostRegistry`
- Requirement keys are registered through `RequirementRegistry`
- Unknown keys fail configuration compilation instead of being silently skipped
- Costs are checked before consumption and successful earlier entries are compensated if a later
  entry fails
- Requirement entries use AND semantics and never mutate player state
- Reward and requirement implementations provide Adventure Components for consistent chat, lore,
  and menu presentation

`coins` is registered only when Vault and an economy provider are available. `stats` rewards are
reconstructable contributions rather than permanent database mutations. Items are intentionally not
part of this first implementation and will be designed separately.

External systems with derived stats register a contribution provider:

```java
services.require(StatContributionRegistry.class)
    .register("skills", SkillStatContributionProvider.class);
```

The provider returns its complete current snapshot for a player:

```java
@Override
public Map<StatKey, StatModifier> calculate(final VexPlayer player) {
  return Map.of(
      StatKey.of("vexskills", "mining_chunk_damage"), StatModifier.flat(9D),
      StatKey.of("vexskills", "mining_block_break_speed"), StatModifier.flat(40D)
  );
}
```

VexCore applies every provider after `PlayerDataLoadedSignal`, replaces the provider's previous
snapshot in one stat update batch, and removes its runtime modifiers when the player leaves or the
provider is unregistered. A plugin calls `refresh(player, "skills")` after relevant data changes and
`refreshAll("skills")` after reloading its progression configuration. Only permanent stat values are
stored in VexCore; skill, collection, equipment, and event contributions remain reconstructable from
their owning systems.

### Level System

The generic level runtime derives an available level from total experience while the owning plugin
persists only its experience and highest claimed level. Manual claiming is the default; automatic
claiming is an explicit configuration choice. Claims always run sequentially and evaluate
requirements, consume reversible costs, grant action rewards, persist the claimed level, and then
allow the owning plugin to refresh its reconstructed stat contribution.

```yaml
leveling:
  min-level: 0
  max-level: 100
  claim-mode: MANUAL
  claimed-level-overflow: KEEP
  experience:
    required: "100 + (25 * %level%)"

levels:
  - min-level: 1
    step: 1
    rewards:
      coins: "200 * %level%"
      stats:
        mining_chunk_damage: "1"
  - min-level: 5
    step: 5
    requirements:
      permission: vexskills.mining.advanced
    costs:
      coins: "50 * %level%"
```

`LevelService` compiles curves and repeating rules on reload. `LevelClaimService` previews and
executes claims, processes automatic claims when enabled, and reconstructs contribution rewards for
every currently claimed level. Reloading a curve never executes historical action rewards. With
`KEEP`, already claimed progression survives a curve nerf; `CLAMP` limits the effective claimed
level to what the recalculated experience currently reaches.

Progress is connected once through a typed `LevelProgressAccess`. Its reader returns an immutable
snapshot; its claimed-level writer is invoked by VexCore only inside `VexPlayer.update(...)`.
Progression plugins must also grant experience through the player container API:

```java
player.update(
    VexSkillsData.KEY,
    data -> data.addExperience(Skill.MINING, 100D)
);
```

Neither experience nor claimed levels may be mutated through a retained container reference.

Level menus, layouts, item models, and localized lore belong to the consuming plugin.
VexCore supplies the progression engine and general inventory APIs without prescribing a level menu.
`dev.vexsoft.core.paper.ui.ProgressBar` renders configurable text progress bars for any UI;
it has no dependency on level progression.

### Command Framework

- Class-based command registration without command entries in `plugin.yml`
- Root commands, subcommands, and arguments
- Optional and greedy arguments
- Dynamic suggestions
- Extensible typed argument parsers, including server IDs and namespaced world IDs
- Non-blocking command handlers through `CompletionStage` return values
- Permission checks for both execution and suggestions
- One command structure shared across all plugins

### Player Data

- One global `VexPlayer` for each player
- Extensible data containers owned by individual plugins
- Shared access to cached player data
- Controlled and thread-safe container updates
- SQLite persistence for standalone servers and PostgreSQL persistence for networks
- Automatic schema extension on both persistent storage backends
- Saving on disconnect, shutdown, and scheduled autosaves
- New data containers can be introduced through later plugin updates
- A central UUID and last-known-name index independent of individual plugin containers

### Global Data

- Plugin-owned typed values for shared data such as warps and server settings
- One storage pool shared with player persistence within each VexCore process
- Bounded Caffeine caching with local or PostgreSQL network invalidation notifications
- Revision-based atomic updates that do not lose concurrent changes from another server
- Runtime unregistration without deleting stored values
- Restoring existing values when the same owner and key are registered again

### Worlds and Teleports

- Persistent namespaced world IDs such as `minecraft:overworld`
- No dependence on legacy Bukkit world names or filesystem folder paths
- Local teleports through Paper's `Player#teleportAsync` API
- The teleport result completes only after Paper's asynchronous chunk load and callback
- Velocity transfers retain the exact target world ID, coordinates, yaw, and pitch
- Clear results for unavailable servers, unloaded worlds, offline players, and timeouts

### Cache System

- Synchronous and asynchronous caching powered by Caffeine
- Configurable size and expiration limits
- Cache statistics and centralized management
- Reusable caches for frequently requested data
- Player profiles and other commonly read values remain available in memory

### Scheduler

- One scheduling API for Paper and Folia
- Synchronous and asynchronous tasks
- Immediate, delayed, and repeating execution
- Safe handling of global, regional, and entity-bound work
- Automatic task cleanup when the owning plugin is disabled

### Inventory Framework

- Class-based inventory registration
- Reusable foundations for inventories, buttons, and pagination
- Previous and next page navigation
- Navigation history and direct returns to a specific menu
- Fully replaceable default buttons
- Managed inventory sessions

For editable menus, existing `MutableInventoryView` callbacks expose raw Bukkit click and drag events.
Use `SlotInventoryView` with `CursorInventoryElement` for rendered slots backed by plugin storage:
the transfer callback validates the item and exchanges the stored item with the real cursor, then the
framework refreshes the view. Rendered placeholders are never transferred as items. Normal player
inventory clicks and drags remain available; shift-click moves between backpack and hotbar, and
double-click collects only from player storage. A drag into one cursor slot is deferred until Bukkit
restores the cursor; mixed drags across menu slots are rejected as a whole. Menu controls accept
left-click by default. Existing `PagedInventoryView` provides page bounds and previous/next buttons;
subclasses can override `renderPageItem` to render entries using their own state.

### Dialog System

- A central abstraction for Paper's experimental Dialog API
- Class-based dialog definitions
- Managed active dialog sessions
- Typed results and controlled cancellation
- Automatic cleanup when players leave or an owning plugin shuts down

> [!CAUTION]
> Paper's Dialog API is experimental. VexCore keeps dialog-specific behavior behind a dedicated abstraction so changes remain contained.

### Packet System

- Version-specific packet adapters
- Compatible adapters can be reused across multiple Minecraft versions
- Individual behavior can be replaced when only a small part breaks
- Viewer-specific text, item, and block displays without server-side entities
- Standalone virtual interaction hitboxes with owner-scoped left- and right-click callbacks
- Display passengers and backward-compatible interactive text holograms
- Viewer-specific player hand-swing animations
- Packet effects for hits, glowing entities, and lightning
- Packet-based item names and lore
- Initial support starts with Minecraft 26.2

### Item Foundation

- An ItemStack builder based on Paper's Data Component API
- Stable VexCore component keys in front of version-specific components
- Support for item models and tooltip styles
- Version adapters for Data Components that may change between Minecraft releases
- Packet-based presentation without unnecessarily changing persistent item data

> [!NOTE]
> Packets and Data Components are version-sensitive by nature. Their implementations live behind version adapters instead of leaking Minecraft internals into other plugins.

## Architecture

VexCore is deliberately split into small modules. API contracts are kept separate from their
implementations so VexSoft projects only depend on the platform contracts they actually use.

| Module | Responsibility |
| --- | --- |
| `vexcore-api` | Platform-neutral contracts for services, player and global data, identities, world positions, localization, placeholders, stats, expressions, rewards, costs, requirements, caching, configuration, and messaging |
| `vexcore-common` | Platform-neutral implementations shared by the server and proxy runtimes |
| `vexcore-paper-api` | Public Paper and Folia contracts together with the `VexPlugin` foundation |
| `vexcore-services` | Paper-only implementations for commands, inventories, dialogs, scheduling, gameplay events, and other stable services |
| `vexcore-items:common` | Public item and Data Component contracts |
| `vexcore-items:versions:v26_2` | Minecraft 26.2 Data Component implementation |
| `vexcore-packets:common` | Public packet contracts |
| `vexcore-packets:versions:v26_2` | Minecraft 26.2 packet implementation |
| `vexcore-paper` | The server plugin that connects and starts every module |
| `vexcore-velocity-api` | Public Velocity contracts together with the `VexProxyPlugin` foundation |
| `vexcore-velocity` | The proxy plugin that owns Velocity services and routes network messages |

## For Server Owners

VexCore is installed as a dependency of VexSoft plugins. It does not add gameplay content by
itself, and server owners normally do not configure its internal services individually.

### Installation

1. Install the VexCore Paper jar on every backend server that runs a VexSoft plugin.
2. Start the server once so VexCore can create its files below `plugins/VexSoft/VexCore`.
3. Configure shared storage in `database.yml` before opening the server to players.
4. Install the VexCore Velocity jar on the proxy when cross-server VexSoft features are required.
5. Set `server-id` in every Paper server's `network.yml` to its exact Velocity server name.
6. Configure the Velocity VexCore instance to use the same PostgreSQL database.
7. Restart the affected server or proxy after replacing a VexCore jar.

Paper and Velocity use separate jars because they run in different processes. The Velocity plugin
is only required for proxy-side services and network messaging; a standalone Paper server does not
need it.

### Persistent Storage

SQLite is the default for a standalone Paper server. Its database file is kept inside the VexCore
configuration directory:

```yaml
storage: sqlite

sqlite:
  file: vexcore.db
```

SQLite is local to one server process. Do not share its file with another Paper server or a proxy,
including through a network filesystem.

PostgreSQL is required as soon as VexCore runs on Velocity or more than one server process needs to
share data:

```yaml
storage: postgresql

postgresql:
  jdbc-url: jdbc:postgresql://localhost:5432/vexcore
  username: postgres
  password: change-me
  maximum-pool-size: 10
  auto-create-database: true
  maintenance-database: postgres
```

With `auto-create-database: true`, VexCore attempts to create the configured database through the
maintenance database when necessary. The configured PostgreSQL account must have the corresponding
permission.

Both persistent backends store player containers, the central player identity index, and
plugin-owned global values. In a network, Paper and Velocity each maintain one local PostgreSQL
connection pool and must point to the same database.

`storage: memory` is available for temporary development environments. All values are lost on
shutdown, instances cannot share changes, and it should not be used for a production network.

### Network Identity and Worlds

Each Paper backend has a `network.yml` file:

```yaml
server-id: lobby-1
```

The value must match the corresponding server name configured in Velocity. Cross-server positions
store worlds as namespaced IDs, for example `minecraft:overworld`, `minecraft:the_nether`, or a
custom ID such as `vexessentials:dungeon`. VexCore does not use legacy world names or paths below
Paper's `world/dimensions` directory to identify a world.

### Optional Integrations

PlaceholderAPI is optional. When it is installed, VexCore exposes the player-bound placeholders of
VexSoft plugins to PlaceholderAPI and can resolve placeholders provided by other expansions. Without
PlaceholderAPI, the internal placeholder system continues to work and no expansion registration is
attempted.

### Administration Commands

| Command | Permission | Purpose |
| --- | --- | --- |
| `/vexcore reload` | `vexcore.command.reload` | Reloads VexCore themes and language resources |
| `/vexcore language set <language>` | `vexcore.command.language` | Changes the executing player's language |
| `/vexcore debug performance toggle` | `vexcore.command.debug.performance` | Toggles the live performance display |
| `/vexcore debug proxy ping` | `vexcore.command.debug.proxy.ping` | Tests the connection to the Velocity plugin |

VexCore also provides explicit player-data reset commands. Every destructive command ends with the
literal `confirm` argument to reduce accidental execution:

| Command | Permission |
| --- | --- |
| `/vexcore reset player <player> container <container> confirm` | `vexcore.command.reset.player.container` |
| `/vexcore reset player <player> all confirm` | `vexcore.command.reset.player.all` |
| `/vexcore reset global container <container> confirm` | `vexcore.command.reset.global.container` |
| `/vexcore reset global all confirm` | `vexcore.command.reset.global.all` |

> [!CAUTION]
> Reset commands replace stored values with their defaults. Global resets can affect every stored
> player and should only be executed after confirming the intended scope and available backups.

### What Happens at Runtime

- Online players are represented by one shared `VexPlayer` and loaded only when their session begins.
- Player data is cached while required and saved on disconnect, shutdown, and scheduled autosaves.
- Player UUIDs and last-known names are indexed centrally for plugin-independent lookups.
- Global values are cached locally and invalidated across PostgreSQL-connected VexCore instances.
- Local teleports wait for Paper's asynchronous chunk loading and teleport result.
- Velocity transfers resolve the destination world by namespaced ID only after reaching the target
  backend.
- Dynamically removed stats disappear from runtime without deleting their stored player values.
- Reward, cost, and requirement expressions are compiled during configuration reload instead of
  being reparsed during progression events.
- Plugin-specific reloads refresh only that plugin's stat contribution providers.
- Unknown or duplicate execution keys fail with the affected domain and configuration key.
- Derived stat snapshots are rebuilt after player data loads and never written as permanent values.

The technical APIs and module boundaries are documented in the architecture and feature sections
for developers who want to understand how VexCore operates. They are not intended as a general
third-party plugin development framework.

## Platforms

VexCore is built for modern Paper servers, supports Folia from the start, and provides a dedicated Velocity plugin for proxy-side infrastructure. Platform-specific behavior is hidden behind shared services, while `VexPlugin` and `VexProxyPlugin` provide the appropriate lifecycle for each environment.

Paper and Velocity keep independent service registries because they run in different processes. The messaging system provides the controlled bridge between them and can route typed messages across backend servers through Velocity.

Version-sensitive areas such as packets and Data Components live in dedicated submodules. This allows new Minecraft versions to be added without copying implementations that are still compatible.

> [!TIP]
> Compatible adapters can be reused by later Minecraft versions. A new implementation is only needed for the behavior that actually changed.

## Scope

VexCore only contains systems that are useful to multiple plugins. Concrete content remains in the projects built on top of it, for example:

- VexItems provides configurable items, categories, and item registration
- VexSkills provides skills, experience, and progression
- VexEssentials provides general server features

These plugins can register their own services and data containers, then communicate through VexCore. VexCore remains the technical foundation and does not take ownership of domain-specific gameplay logic.

### Armor item components

The existing ItemService builder supports DYED_COLOR (24-bit RGB), TRIM (resolved with
ItemService.armorTrim(patternKey, materialKey)), ATTRIBUTE_MODIFIERS (VexItemAttributes), and
TOOLTIP_DISPLAY (VexTooltipDisplay). VexItemAttributes.empty() explicitly replaces prototype
modifiers; resetData restores defaults and unsetData removes a component. VexTooltipDisplay
hides selected details while retaining custom name and lore. Resolution and component application
live in the version adapter; gameplay rules such as zero armor or unbreakability belong to consumers.

### Personal player previews

PlayerDummyService creates viewer-only mannequin packets through the versioned display adapter.
No entity is added to the server world or ticked. A private client team disables collision and name tags.
The dummy is immovable, has no gravity, and receives no hand equipment. SkinService first copies signed
textures from the online profile. Missing textures use asynchronous profile updates (5-second timeout),
VexCore bounded caches (2048 entries, 1-hour successful skin retention, 30-second request/failure retention),
and shared pending requests. Closed or removed previews ignore late results.

Call spawn(viewer, center, BobRotation), armor(handle, bootsToHelmet), and animate(handle) on the
viewer thread from the feature's existing visual loop. The position is the body's center; the adapter
converts to feet. Equipment packets are sent only on changes. Call remove on range exit; owner shutdown,
quit, respawn, and world changes also retire handles. isActive lets feature runtimes detect retirement.

### Shared rewards and resource providers

Named rewards support independent chances, upgraded item bundles, frozen preparation for retries, transactional inventory
delivery, private overflow and grouped announcements. Namespace adapters expose existing plugin item and block factories.
See [shared rewards and resource providers](docs/rewards.md) for the API contracts and reusable YAML format.

### Redis live state and SQL checkpoints

Paper and Velocity share the Redis backend configured in `plugins/VexSoft/VexCore/database.yml`.
Set `redis.enabled: true` on **every** instance in the network and use the same Redis endpoint, database,
key prefix and PostgreSQL checkpoint database. The templates leave Redis disabled until credentials are supplied.
Each Paper server must have a unique `network.yml` server ID matching its Velocity registration. The current
directory/transfer protocol has one active Velocity proxy authority; adding backend instances does not add proxies.
Redis control messages work on empty backends without a connected player. Duplicate endpoint ownership fences
the conflicting process. After an unclean stop its endpoint lease expires within 15 seconds.

Use Redis 7 or newer with `maxmemory-policy noeviction` and server-managed AOF persistence. Do not expire or
evict authoritative state, journal or epoch keys. The configured ACL user needs the Core namespace's read/write,
stream, scripting, `TIME`, `INFO server`, and `CLIENT SETNAME` permissions. Scripts preflight their write permissions
before changing any state or journal entry. The Java client uses bounded asynchronous queues. Profile,
account, shared/global and published configuration operations use Redis; legacy SQL gameplay tables are no longer
updated in live mode. Exact currency amounts remain strings. Existing plugin APIs continue to address account,
personal-profile and shared-profile data separately. Vault's external provider remains outside these checkpoints.

**Initial cutover:** stop all old Paper and Velocity writers, back up SQL, configure all instances, and start the
updated plugins together. Cold initialization imports all existing Core owner tables, profile documents, global
data and identities into one complete recovery point. It never imports individual SQL values during gameplay.
Once checkpoint data exists, the new plugin refuses direct-SQL mode to prevent accidental loading of old tables.
An old plugin version cannot enforce that guard, so do not run old and new storage modes together.

Paper captures live native state every second without blocking gameplay while the write is pending;
unchanged native snapshots are skipped. Economic and ownership
changes use the guarded Redis transaction boundary. Retries reuse an operation receipt; ambiguous outcomes fence
the affected profile until reconnect rather than compensating an operation that may already have committed.
Live economic transactions capture their rollback baseline locally and commit wallet and inventory together once;
they wait for any outstanding background write, and confirmed failures retain unsaved baseline changes for retry.
Profile/server handovers drain live writes first. Profile switch/logout request a coalesced checkpoint;
requests coalesce across the network for 10 seconds and unchanged checkpoints skip SQL writes.
SQL writes do not delay the handover. The default checkpoint interval is 300 seconds, configurable under
`persistence.checkpoint-interval-seconds`. Only one coordinator publishes at a time, and both checkpoint generation
and recovery epoch fence stale SQL publishers. The last full checkpoint remains usable after a failed SQL write.

A Paper crash reads the surviving Redis state on reconnect. Loss of the Redis authority restores the entire last
complete SQL checkpoint, including inventories, currencies, world-item receipts, shared stock and pending claims.
Never restore individual players from legacy tables. A database outage increases the checkpoint age; the bounded
journal rejects further writes when full. Redis unavailability rejects authoritative changes. These guarantees
also reject a missing checkpoint anchor or a Redis recovery older than SQL; restart all instances through a coordinated
cold recovery in that situation. They
cover Core-owned state, not external economy providers or mutable world containers. A future island/world adapter
must coordinate world/container snapshots with these checkpoints; no island implementation is included here.

PostgreSQL checkpoints use a schema per plugin and a table per data area. Core uses `vexcore.players`,
`vexcore.player_names`, `vexcore.inventories`, `vexcore.currencies`, `vexcore.delivery_inboxes`,
`vexcore.profile_sessions`, `vexcore.world_items` and `vexcore.pickup`. Isles uses `vexisles.profiles`
(one shared roster per profile), `vexisles.profile_accounts` (a player's profile list), `vexisles.profile_members`,
`vexisles.loadouts`, `vexisles.pet_buffer`, `vexisles.shops` and further registered container tables.
Tables are created lazily as their first records are saved. The SQL account needs permission to create plugin
schemas/tables and read/write the checkpoint tables. Domain-specific native container names and departure
receipt names are grouped into their data area, preserving the original `container_key`. New plugin containers
participate automatically; island worlds still require a dedicated persistence adapter.

Each data row has `namespace`, `record_key`, `scope`, `domain`, `player_uuid`, `profile_uuid`, `container_key`,
`revision`, JSONB `value` and `updated_checkpoint`. UUIDs identify both members and shared profiles; shared rows
have no player UUID. Exact currency amounts inside the payload remain strings. `vexcore.checkpoint_tables`
tracks registered tables and `vexcore.checkpoints` stores only generation, journal position, recovery epoch and
layout version. SQLite uses the same columns with JSON text and `<plugin>__<area>` table names.

Normal checkpoints read only the SQL cursor, coalesce the journal up to a fixed cut and batch-write changed rows
plus explicit deletions. Untouched rows retain their prior `updated_checkpoint`; they still belong to the complete
recovery point. All table changes and the cursor commit in one transaction. Recovery reads the complete table
set under a consistent SQL snapshot, so a concurrent publisher cannot mix generations. Generation and epoch
checks reject stale coordinators before their rows change. New tables and their registration also roll back on failure.

On first access, old `vex_network_checkpoints` snapshots migrate transactionally into the new tables, retaining
position, generation and epoch. Only the successfully copied namespace's old blob row is removed; old pre-Redis
owner tables remain obsolete migration sources. Stop old writers and back up SQL before upgrading the storage
layout. Do not run old binaries against the migrated database. No manual-edit commands are added.

Capacity and Redis latency must be measured with production-shaped inventories before increasing player
counts; sharding requires an explicit transaction design rather than independent Redis instances.

Integration tests use an isolated namespace on a real Redis server: set `VEXCORE_TEST_REDIS` to a test Redis URI
and run `:vexcore-common:test`. Without that variable Redis integration tests are skipped; SQL checkpoint tests
and the existing storage/profile tests still run. No test flushes the Redis database or touches another namespace.
Set `VEXCORE_TEST_POSTGRES` to a test JDBC URL whose user can create databases to run the PostgreSQL table tests.
Each test creates and drops its own database. Without this variable only that PostgreSQL variant is skipped;
the same migration, incremental publication, rollback and concurrent recovery tests still run on SQLite.
