# Java package structure

Packages group code by feature inside the existing Gradle modules. A feature may then separate services,
listeners, commands, configuration, presentation, and cohesive internal subsystems. Module dependencies and
the distinction between platform-independent, Paper, Velocity, API, and implementation code remain intact.

## Implementation packages

- Shared implementations live under `dev.vexsoft.core.common.<feature>`.
- Paper implementations live under `dev.vexsoft.core.paper.<feature>`.
- Velocity implementations live under `dev.vexsoft.core.velocity.<feature>`.
- A feature's services live in its `service` package. Larger service families use responsibility packages,
  such as `mob.service.registry`, `mob.service.runtime`, and `mob.service.spawner`.
- Feature-owned commands and listeners live with their feature. Framework command annotations and argument
  contracts remain in `paper.command`; Core's general administrative commands use `paper.command.core`.
- The scoped registry remains in `common.service.registry`: it is shared bootstrap infrastructure used by
  dependent plugins and their registration tests.

For example:

```text
dev.vexsoft.core.paper.mob
  service
    registry
    runtime
    spawner

dev.vexsoft.core.paper.packets
  service
    block
    connection
    display
    effect
    interaction
    item
    player
    version
```

Closely coupled runtime helpers stay with the service that owns them. In particular, inventory sessions,
holders, renderers, and their lifecycle listener share `inventory.service`; crafting shortcuts have their
own `inventory.shortcut` package. Package-private access should not be widened merely to create another
directory.

## API packages

Public contracts and models stay in their existing API modules. Established Core service contracts retain
their `api.service` and `paper.service` import paths. Implementation classes are not moved into API modules.

Larger model and UI packages have responsibility-specific subpackages:

- Level definitions, compiled rules, and progress use `level.definition`, `level.compiled`, and
  `level.progress`.
- Reward contribution and notification types use `reward.contribution` and `reward.notification`.
- VexUI uses `element`, `component`, `action`, `state`, and `style`, plus feature-specific packages under
  `template`. Its sealed element hierarchy stays together in `element`.
- Screen UI separates `dialogue`, `layout`, `panel`, `reward`, and `animation` definitions.
- Packet display requests and updates use `display.text`, `display.item`, and `display.block`.

These type moves change fully qualified class names. Dependent plugins must update imports and rebuild;
the VexIsles sources are migrated alongside Core. Existing configuration keys, persistent field names,
service registration contracts, and gameplay behavior are preserved.

## Tests and resources

Tests follow the implementation or model they inspect. Shared test fixtures may be used across test
packages without exposing production internals solely for testing. Source-path assertions, reflection
strings, service descriptors, and documentation links must follow moved types.

Generated Screen UI font metrics live beside `screenui.render.ScreenUiFont`. Generated vanilla VexUI
translations live beside `vexui.render.VexUITranslations`; resource generators and class-relative reads
must always agree on these locations.
