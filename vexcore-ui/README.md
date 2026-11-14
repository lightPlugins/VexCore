# VexUI

VexCore's dialog-based UI framework for Paper 26.2. Public classes use the `VexUI` prefix.
It runs inside VexCore and does not depend on VexIsles.

## Modules and versions

- `:vexcore-ui:api`: immutable layouts, widgets, themes, actions and the `VexUIService` contract.
- `:vexcore-ui:common`: owner-scoped sessions, shared coordinator, layout, renderer, Paper transport and debug shop.
- `:vexcore-ui:pack`: deterministic fonts, frame geometry, vanilla icon definitions and matching measurements.

The Java artifacts follow VexCore's release version. API generation 2 and pack schema 3 are exposed through
`VexUIFrame`; they are separate from the Minecraft resource-pack format (88 for this 26.2 profile).
There is currently one Paper backend and no NMS dependency. Minecraft 26.3 has not been validated.
API generation 2 removes the unused profession goal method and snapshot field. Plugins implementing the
profession provider or constructing its snapshots must rebuild against this API. Schema 3 adds Unicode fallback
symbols; install the matching Core JAR and regenerated pack together.

## Build and visual test

From the repository root:

```powershell
.\gradlew.bat :vexcore-ui:api:build :vexcore-ui:common:build :vexcore-ui:pack:build :vexcore-paper:build
```

The first build downloads and SHA-1-verifies the pinned official 26.2 client to measure vanilla fonts and icons.
For an offline build, provide `-PvexuiClientJar=C:/path/to/official-26.2-client.jar` once. The JAR is cached
under `vexcore-ui/pack/build/minecraft`. Vanilla PNGs are read for measurements, not copied into our pack.
The build also verifies and bundles the official English and German vanilla dictionaries as server resources.
For a first offline build, supply the pinned German JSON through `-PvexuiGermanTranslations=C:/path/to/de_de.json`;
subsequent builds reuse `.gradle/vexui/vanilla-de_de-26.2.json`. No dictionaries are downloaded during gameplay.

Outputs:

- `vexcore-paper/build/libs/VexCore-1.0.0-SNAPSHOT.jar`
- `vexcore-ui/pack/build/libs/VexCore-VexUI-26.2.zip` (standalone, no shader changes)
- `vexcore-paper/build/libs/VexCore-ResourcePack-1.0.0-SNAPSHOT.zip` (existing Core pack plus VexUI assets)

Load the VexUI pack, or the updated combined Core pack, on the client before opening the shop:

```text
/vexcore debug vexui shop
/vexcore debug vexui components
/vexcore debug vexui inspect
```

Permission: `vexcore.command.debug.vexui` (operator by default). The command does not host or automatically
download a resource pack. An old loaded pack cannot display new glyphs; resource-pack reload is required
after replacing the pack. Configuration and localization files live under `vexui/` and `languages/*/vexui/`.
The shop configuration is loaded on opening. It is local debug configuration and is not published gameplay data.

The shop provides 36 vanilla products, categories, search with explicit submission, a stackable filter,
three sort orders, Previous / current page / Next, product selection and quantity controls. Purchases grant
actual vanilla items with unlimited test credit. No economy service or wallet is read or changed. Grants
check storage capacity and restore the previous storage snapshot if insertion fails. No items are dropped.

The component playground demonstrates reusable product cards, tabs, explicit-submit search, quantity controls,
single and multiple choices, reactive demo stock, a simulated 500 ms request, error feedback and reset confirmation.
It grants no items. Settings apply to that session only. The inspect command enables developer box boundaries and
action tooltips; the same overlay can be toggled inside the playground. Its menu file is
`vexui/menus/components.yml`; presentation is under `languages/*/vexui/components.yml`.

## Using the API

Depend on `dev.vexsoft.vexui:vexcore-ui-api:1.0.0-SNAPSHOT` (compile-only in a plugin that depends on VexCore).
VexCore's plugin bootstrap registers an owner-local `VexUIService`; resolve it through the existing registry.
Managed classes still require `@Dependencies` and a public `VexServiceRegistry` constructor.

```java
VexUIService ui = services.require(VexUIService.class);
ui.open(player, session -> new VexUIFrame(
    localizedTitle,
    localizedClose,
    400,
    new VexUIColumn(4,
        VexUIButton.text(localizedAction, "example"),
        VexUIItemIcon.vanilla(Material.POTATO).size(32)
    ),
    Map.of("example", viewer -> performDomainAction(viewer.player()))
));
```

Create a fresh view for every viewer. Resolve all visible text through the plugin's existing localization
service. Keep domain transactions in their responsible service. `session.update(...)` applies asynchronous
results on the viewer's entity scheduler only while that session remains active.

Resolve native item-name components with `ui.resolveText(component, playerLocale)` before passing them to a
template or caching presentations. This preserves styles and nested arguments while giving the layout engine
the actual text to measure. German and English use their bundled vanilla dictionaries; other locales fall back
to English. Configured literal names remain unchanged. The text engine also resolves remaining vanilla keys
in English as a fallback; it cannot infer a player's configured language from an isolated component.

Crafting material rows remain 16 logical pixels high with at most a one-pixel gap, including older published
layouts that still specify tall cards. The material list fills its panel internally so sparse pages stay
anchored immediately below the heading rather than moving toward the vertical center.

Rows distribute remaining width among weighted children; columns stack and stretch children; grids use
equal-width columns. `VexUIGridCell` spans rows or columns. Panels and buttons contain arbitrary nested layouts.
Button dimensions are supplied in code through `VexUISize`. Pagination defaults to
`VexUIPagination.DEFAULT_BUTTON_WIDTH` (58 pixels), with an explicit width overload for custom controls.
No framework navigation configuration file is needed; `languages/*/vexui/navigation.yml` contains only labels.
Use `VexUIPagedGrid.slice(entries, page, pageSize).grid(columns, gap, cellHeight, renderer)` for paginated
content: it retains the page capacity, uses equal-height cells and automatically reserves invisible,
noninteractive slots on partial and empty pages. Pagination and content below the grid keep their positions.
The cell renderer must produce content that fits `cellHeight`; ordinary `VexUIGrid` remains content-sized.
Pixels are logical GUI pixels, not physical screen pixels. The server cannot detect a client's window dimensions;
use narrower configured layouts for smaller windows. Oversized content scrolls through the native dialog body.
Text is single-line, measured and clipped while preserving styles. Use a column of text nodes for multiple lines.

Default panel skins use desaturated purple tints. `VexUITheme` accepts a custom four-color palette and reuses the
same white geometry. `VexUITexture` accepts an external namespaced font prefix, code point and exact measurements.
Supply its nine font variants `prefix/y_0` through `prefix/y_8` with bitmap ascent `7 - offset` and matching height.
Large custom glyphs need dimensions that fit the client's glyph atlas. All VexUI menus use the original Minecraft
default-font bitmaps at their original size, including accented and non-Latin bitmap characters. The pack references
the client textures and shifts their baselines for positioning. Widths come from those same bitmaps, including
vanilla four-pixel spaces. Missing Latin/general symbols (U+00A0–U+2FFF), mathematical alphabets and emoji also
use the pinned vanilla Unifont fallback, baked into measured bitmap pages. This includes the rarity star `✦`.
The generated pack includes its license in `VEXUI-UNICODE-LICENSE.txt`. Other unsupported code points use `?`.
Install the matching schema-3 pack and plugin build together when upgrading from the former custom pixel font.

## Reusable components

### Compact tabbed menus

`VexUITabbedMenuTemplate` composes a shared `VexUIMenuHeader`, attached `VexUITabs.Tab` choices,
caller-owned content, and a footer. `Layout.compact(560, 176)` reserves the existing 18-pixel header,
18-pixel tab row, 176-pixel content area, and 18-pixel footer within a 560 by 259 logical-pixel frame,
including renderer padding. This is a compact layout profile, not a general VexUI canvas limit.

The header separates `sections` (for example Equipment / Arkies / Combat) from the page's `tabs`.
Equal flexible header sides center section navigation independently of title and Back/Close widths.
Back uses the existing session navigation and a 66-pixel control with centered text; Close and Back
can be disabled independently. Views supply localized labels, availability, action IDs, and click sounds.
Register section and tab actions in the same frame action map before rendering. Keep active IDs and
all gameplay operations in the viewer's existing view rather than in a shared template instance.

```java
var template = new VexUITabbedMenuTemplate(
    VexUITabbedMenuTemplate.Layout.compact(560, 176),
    new VexUITabbedMenuTemplate.Style(
        session.styleSheet().selected(), session.styleSheet().normal().lightBorder(), 0xC5A164
    )
);
var tabs = List.of(
    new VexUITabs.Tab("tab.overview", localizedOverview, -1, true, null),
    new VexUITabs.Tab("tab.materials", localizedMaterials, -1, true, null)
);
return template.render(session, localizedTitle, header, tabs, activeTab, content, footer, actions);
```

`VexUIMenuHeader` accepts the heading element, section options, active section ID, and localized
`Controls`. Empty sections retain a simple title/Back/Close header. A template overload accepts a custom
second row for column headings on detail pages. Both overloads retain the supplied content and footer
elements and their exact reserved positions; no gameplay layout is rebuilt by the template.

Attached tabs use rectangular outlines. Their active fill comes from the content panel's background,
the active outline comes from the template style, and the bottom edge stays open. A width of `-1`
shares remaining space; explicit widths retain compact tabs with longer labels. Existing
`VexUITabs.controls` behavior is unchanged for other consumers. The template uses existing pack tiles
and fonts, so these components require no new resource-pack schema.

Components compose the existing row, column, grid and button primitives. They do not introduce a second layout
engine or embed economy rules. Pass localized `Component` labels, the current stylesheet and a frame-local action
map. Each component needs a unique action prefix; `VexUIActions.register` rejects collisions explicitly.

- `VexUITabs.controls`: horizontal choices with selected and disabled roles.
- `VexUIProductCard.button`: icon, title, detail line and tooltip, usable in an automatically padded page grid.
- `VexUIQuantitySelector.controls`: bounded `-N`, `-1`, current value, `+1`, `+N`, maximum controls.
- `VexUISearchBar.controls`: search and clear controls using the native explicit-submit search form.
- `VexUISelectionGroup`: viewer-owned single or multiple choice, rendered as tabs or a tile grid.
- `VexUINavigation.back`: shared Back action retaining the previous view instance.
- `VexUIConfirmation`: child confirmation view restoring its parent after Confirm or Back.

For example, create one group per viewer, retain it in that viewer's view, then render it with:

```java
VexUISelectionGroup filters = new VexUISelectionGroup(
    localizedOptions, true, Set.of("available")
);

// Inside render(session):
VexUIElement controls = filters.controls(session, "filters", 3, actions);
Set<String> selected = session.bind(filters.selected());
```

`VexUIOption` contains a stable ID, localized label, tooltip and availability. Disabled choices cannot be toggled.
Option labels are immutable snapshots; rebuild choices when the owning plugin replaces their localization data.
Confirmation callbacks run on the entity thread before Back and should perform domain work, not navigation.
The normal dialog Close/Escape still closes the entire session. The parent retains its page, selection and UI state.

## Reactive data and view lifetimes

`VexUIState<T>` stores immutable UI snapshots. `set` and atomic `update` notify listeners only when equality changes;
`readOnly()` exposes a stable observable facade without mutation methods. For domain data, implement
`VexUIObservable<T>` as an adapter to the responsible service's snapshots and subscription mechanism.

```java
private final VexUIState<Integer> available = new VexUIState<>(8);

// During render(session), subscribing before reading avoids missed updates:
int stock = session.bind(available.readOnly());
```

Bindings are render-scoped: repeated reads share one subscription per observable identity. Unused dependencies
are removed after rendering, and navigation, native search, disconnect, replacement and owner shutdown release
the active view's subscriptions. Returning to a retained parent subscribes again and reads its latest values.
Updates may originate on another thread; only notification occurs there, while rendering stays entity-scheduled.
There is no polling and no automatic observation of arbitrary Bukkit or service fields. Use immutable snapshots,
call `bind` only during rendering and keep transactions in their domain service.

`session.scope()` captures one visible view activation. `scope.update(...)` checks that activation both before
scheduling and when executing. `scope.onClose(...)` owns non-Bukkit cleanup, with an idempotent unsubscribe handle.
Going Back creates a new activation even when it restores the same view instance.

## Asynchronous action feedback

Create a fresh `VexUIAsyncAction<T>` per viewer action. During rendering, its `button(...)` observes status and uses
prelocalized `VexUIActionLabels` for Idle, Running, Success and Error. Running buttons are disabled, and the
controller independently rejects duplicate starts. Custom layouts can bind `action.status()` themselves.

```java
// Inside a frame action on the entity scheduler:
loading.run(session,
    () -> catalogService.loadSnapshotAsync(),
    (active, snapshot) -> catalog.set(snapshot),
    (active, failure) -> showLocalizedFailure(failure)
);
```

The supplier must return a nonblocking `CompletionStage`; use the responsible service or VexCore scheduler for
actual background work. Start and reset controllers on the entity thread. Completion callbacks return to the
captured view's entity context. Leaving it resets pending UI feedback and suppresses late callbacks, including
when the same view is reopened. Captured UI references are released even if an external future never completes.
External operations are not cancelled or rolled back by the UI. A payment and item grant must therefore remain
one authoritative domain operation; the completion callback presents its result rather than finalizing a charge.

## Stylesheets and diagnostics

`VexUIStyleSheet` groups Normal, Primary, Selected, Danger, Disabled and Panel skins with gap, padding, button height
and navigation width. `ui.setStyleSheet(...)` refreshes only that plugin owner's sessions; `session.setStyleSheet(...)`
overrides one session, and `null` restores inheritance. Components read `session.styleSheet()` during rendering.
`VexUIButton.text` inherits the Normal role; `button.withRole(VexUIButtonRole.DANGER)` selects another role.
Existing constructors with an explicit `VexUITheme` retain that skin; `withRole(null)` restores it. Explicit element
dimensions remain authoritative, while content-sized buttons use the stylesheet's minimum button height.

```java
session.setDebugOptions(VexUIDebugOptions.inspect());
session.setDebugOptions(VexUIDebugOptions.disabled());
```

Developer overlays show box boundaries and add action IDs, logical sizes and coordinates to button tooltips.
They preserve native click geometry, including disabled buttons. Layout failures throw `VexUILayoutException`
with the offending occurrence's tree path, such as `root/Column/Row[1]`, and retain the original validation cause.
Normal menus do not expose these details. Styles, diagnostics and sounds require no framework YAML settings.

Built-in flat vanilla icons have 16, 32 and 48 pixel variants referencing the same client PNG. Only registered
sprites can be used through `VexUIItemIcon`; unknown sprites fail explicitly. Icon measurements assume the pinned
vanilla artwork; packs changing its alpha bounds
need corresponding measurements. Native inputs use the client's normal styling.

### Custom model previews inside nested elements

`VexUIModelPackGenerator` in the pack module resolves ordinary `minecraft:model` item definitions, inherited JSON
models, cuboids, face UVs, element rotations, GUI transforms and layered generated textures. It writes a pack
fragment and `vexui-models.properties` with exact advances and 14/16/32/48 pixel font variants. Resource-pack roots
are ordered from base to override; the official client supplies missing vanilla parents and textures.
Additional `--item=minecraft:oak_log` arguments bake explicitly requested vanilla item definitions without copying
the original vanilla assets into the pack. Dynamic definitions are reported so callers can choose flat fallbacks.

Run the generator during the consuming plugin's pack build, include the fragment in its pack and the manifest
in its plugin JAR, then register it in the plugin's owner-scoped service lifecycle:

```java
var registration = ui.registerModelAssets(VexUIModelAssets.read(manifestStream));
var icon = ui.findModelIcon(Key.key("plugin:arkie"), 32, Component.empty()).orElseThrow();
```

The icon is a `VexUITexture` and works in panels, buttons, rows and grids. Close the registration with its owner;
duplicate model keys are rejected atomically within a scope. This renders a static GUI preview of the 3D model,
not a live rotating model. Animated textures use their first frame; item predicates, special entity renderers,
dynamic tinting and runtime ItemStack components are not evaluated. Unsupported dynamic/special definitions
are listed in a build report. No model parsing or rasterization happens on a server tick.

## Session and rendering behavior

The shared coordinator allows one VexUI session per viewer across plugin scopes. Owner shutdown, replacement,
Back navigation and disconnect retire views and invalidate their actions. `VexUIView.closed()` releases resources
and may run during shutdown; it must not access Bukkit world state.

Every rendered frame gets unpredictable, viewer-bound action identifiers. Only the first action from a frame
is accepted until refresh; stale or foreign identifiers are ignored. Refreshes are coalesced, and unchanged
unsolicited refreshes avoid another packet. Disabled buttons have no executable action. View state and purchases
are processed on the viewer's entity scheduler. Dialogs use `pause(false)` and `afterAction(NONE)` and are replaced
without a preceding close packet.

Incoming clicks are checked against the current action table before scheduling. Duplicate clicks on a pending
action share one queued task; close actions remain independent. Entity tasks validate again because the frame
or session may have changed after admission. Forged or stale identifiers therefore consume no scheduler tasks
when they are already invalid at dispatch time.

Background geometry and its exact hit areas share the first text baseline using generated vertical offsets.
All foreground labels and icons are drawn afterward. Reserved lines give the native widget its full hit-test
height. This avoids later backgrounds erasing tall icons. Label spaces deliberately do not inherit button actions:
Minecraft's empty-space hit regions ignore custom font ascent. The background supplies the complete action area.

Hover tooltips are native; arbitrary hover-only texture changes and live per-keystroke server search are not
part of this backend. Existing non-VexUI dialogs are outside the VexUI coordinator's ownership.

## Button sounds

Enabled custom buttons play `minecraft:ui.button.click` in the UI sound category at volume `0.4` and pitch
`1.25` by default. Each plugin owns its default; changing it also affects that owner's existing sessions.
Native dialog controls retain their client sound without an additional framework sound. Disabled, stale,
duplicate and foreign actions do not play feedback. Sound settings are controlled exclusively through the
Java API; the debug shop inherits the service default without overriding settings made in code.

```java
VexUIService ui = services.require(VexUIService.class);
ui.setClickSound(new VexUIClickSound(true, Key.key("minecraft:ui.button.click"), 0.4F, 1.4F));
ui.setClickSound(VexUIClickSound.disabled()); // Mute this plugin's inherited button feedback.

VexUIFrame silentMenu = frame.withClickSound(VexUIClickSound.disabled());
VexUIButton silentButton = button.withClickSound(VexUIClickSound.disabled());
```

Resolution order is button, frame, owner default. Passing `null` to either `withClickSound` restores inheritance.
Pass `VexUIClickSound.defaults()` to `setClickSound` to restore the standard feedback; service defaults cannot
be null. Sound keys may reference an external resource pack. Volume must be finite and nonnegative; pitch
must be between `0.5` and `2.0`.

`VexUIClickSound.collect()` supplies the shared `vexcore:ui.collect` confirmation (volume `0.65`, pitch `1`).
Play `player.playSound(VexUIClickSound.collect().asSound())` on the entity thread only after a menu collection succeeds.
Do not use it as the initiating button's click sound: rejected, empty and failed collections must not confirm success.
The profession template plays it once for a successful nonempty receipt, including Collect All.

The supplied audio is converted to mono Ogg Vorbis at 44.1 kHz and stored as `sounds/ui/collect.ogg` in the `vexcore`
namespace. Standalone VexUI packs include their own sound manifest. Combined Core/Isles packs retain Core's merged
manifest, which registers both the collection cue and the existing upgrade background sound.

## Verification

Tests cover real registry/factory construction, owner replacement, stale and duplicate actions, navigation,
search, disconnect, pagination boundaries, quantities, capacity disabling, nested/spanning layouts, reactive
subscription lifetimes, view-scoped asynchronous completion, themes, confirmation and component integration. A CPU
preview decodes the actual generated fonts and checks every pixel's action target against the layout. It writes
`vexcore-ui/common/build/reports/vexui/debug-shop.png`. Pack tests verify reproducible bytes and no vanilla PNG copies.
The component integration tests also write `components.png` and `components-inspect.png` beside that shop preview.

The CPU preview is not an in-game acceptance test. Verify the loaded pack, GUI scales, tooltips, cursor-preserving
updates, search return, item grants, full inventory, Back/Close/Escape and disconnect on a Paper 26.2 test server.

## Rendering performance

Text is emitted as styled runs instead of individual character components. Per-render, bounded text measurements
reuse flattened labels; glyph advances, tile IDs and icon metrics are parsed once. Adjacent hit tiles share one
event-bearing run, background strips inherit one style, and immutable cursor shifts are reused. Viewer state,
localized text and action tokens are not stored in shared render caches. Every new interactive frame still rotates
its opaque action identifiers; duplicate/stale action rejection is unchanged. Those optimizations do not change geometry.

An opt-in comparison runs 300 warmup iterations and 1,000 measured renders over six profession pages:

```powershell
.\gradlew.bat -PvexuiBenchmark=true :vexcore-ui:common:test --tests '*VexUIRenderBenchmarkTest'
```

The report is `vexcore-ui/common/build/reports/vexui/render-benchmark.json`. On the local comparison host, median
rendering fell from 1.13 ms to 0.23 ms and Adventure JSON serialization from 3.99 ms to 2.07 ms. Average temporary
allocations fell from 11.10 MB to 4.91 MB, and JSON size from 327.7 kB to 186.1 kB. These are renderer/serializer
measurements with fixture snapshots, not server MSPT, actual network bytes or a live Paper transport profile.
The benchmark has no wall-clock pass/fail threshold and is skipped during ordinary builds.

## Reusable templates (API generation 2)

Templates live in `dev.vexsoft.core.paper.vexui.template` and create a fresh `VexUIView` for each viewer:

| Factory | Adapter | Included presentation |
| --- | --- | --- |
| `VexUIMainMenuTemplate` | `VexUITemplateProvider<VexUIMainMenuTemplate.Snapshot>` | Paginated dashboard tiles, disabled actions, reward/news attention |
| `VexUIShopTemplate` | `VexUIShopProvider` | Categories, explicit-submit search, availability filter, sorting, pages, item preview, quantity controls, live purchase quote |
| `VexUIUpgradeTemplate` | `VexUIUpgradeProvider` | Before/after preview, option selection, paginated requirements/costs, progress and asynchronous execution |
| `VexUIProfessionTemplate` | `VexUIProfessionProvider` | Single profession workspace, compact sidebar, progress, embedded claims, inline item/details/receipt pages |
| `VexUITravelTemplate` | `VexUITravelProvider` | Paged category sidebar, retained destination pages, embedded direct-travel buttons and live journey/cancellation strip |

Adapters are viewer-specific plain objects. Implement their observable snapshot, localized text and layout contracts;
resolve the underlying managed domain services through the plugin's existing registry. `VexUITemplateLayout`
sets logical width, columns, rows, cell height and gaps. Every grid reserves its complete page capacity.
Close the adapter's resources in `close()`; the root owns it while navigation children preserve it.

```java
var view = new VexUIProfessionTemplate().createView(professionProvider);
services.require(VexUIService.class).open(player, view);
```

The shop template keys are `previous`, `next`, `page` (`page`, `pages`), `back`, `close`, `all_categories`,
`search`, `search_title`, `search_label`, `apply`, `all_products`, `available_only`, `sort_0`, `sort_1`, `sort_2`,
`empty`, `select_product`, `pending`, `failed`, `quantity` (`amount`), and `quantity_minus16`, `quantity_minus1`,
`quantity_plus1`, `quantity_plus16`, `quantity_max`. Dashboard keys are the shared paging/back/close subset;
upgrade additionally uses `empty`, `pending`, `failed`. Profession keys are namespaced per section (`main.*`,
`job.*`, `levels.*`, `rewards.*`, `sources.*`, `unlocks.*`, `receipt.*`); the VexIsles adapter demonstrates
independent published layout/localization ownership. Values use the owning plugin's localization system.

Quotes and previews are presentation only. Shop/upgrade/claim adapters must revalidate profile identity, item
identity, funds, requirements, capacity, stock and transaction state in their existing authoritative services.
Templates never grant rewards, roll random amounts, create wallets or persist progression. Async actions are
single-flight and view-scoped; closing a view suppresses stale UI feedback without cancelling its domain transaction.

`VexUIProgressBar` supports continuous or segmented fills. `VexUIParagraph` wraps styled text at measured glyph
widths; capped previews use an ellipsis, while `VexUISession.wrap` allows complete detail pagination. Newlines and
whole-line localized list entries remain separate. `VexUIButton.withAttention(new VexUIAttention(count, fresh))`
adds a static gold frame while preserving enabled/selected semantics. There is no unverified animation loop.

Use `VexUIIcon.item("minecraft:item/iron_ingot")` only for registered sprites. `VexUIService.findIcon` safely
queries availability at 16/32/48 pixels, allowing adapters to provide a fallback for unsupported custom or block
models. New pack entries include the profession reward materials; rebuilding the pack remains necessary.

The template tests write `professions.png`, `profession-rewards.png` and `profession-bundle.png` beside the other
CPU previews. These are font-decoded diagnostic renders, not screenshots from a live client.

The profession template uses one continuous themed workspace. Profession selection, tabs, milestone previews,
per-level claim buttons, and receipt inspection update that same view. There are no intermediate bundle or
item-detail dialogs. Its provider additionally supplies `lore(key, values, sections)` through the owning plugin's
localization system: complete string-list lines, blank separators and whole-line styled component sections are
preserved. VexIsles publishes these compact sections under `menu_profession_ui_v4_*`; older layout publications
are not interpreted as the new workspace. The favorite/pinning API has been removed.

`VexUIPanel.withTooltip(Component)` applies a native tooltip across a panel's complete area. Embed interactive
buttons in panels and rows; a panel is not itself a clickable parent, so locked child buttons remain disabled.
Native hit-area tests verify that the backdrop and panel tooltips do not mask nested claim actions.

Profession workspace defaults omit search and use tighter row leading and smaller per-section capacities.
Viewport tests bound the standard workspace to 300 logical pixels, including empty filters. Filter/sort,
section and page controls use centered labels while preserving the existing panel themes.
Navigation groups share one bottom row, including disabled controls, empty previews and partial pages.
Back sits at the upper right with global Collect All immediately to its left; there is no hint/footer row.
Unlocks use the full workspace as a two-column paged grid (eight entries with the default four rows), with
formatted details on hover and no separate detail pane or detail pager.
Defaults show five levels and four preview items per page. The level path has no goal or reward shortcut controls;
claims remain under the rewards tab and the global claim action. Goal contracts and snapshot fields have been removed.

## Shared crafting stations

`VexUICraftingTemplate.createView(VexUICraftingProvider)` opens one viewer-local station workspace.
The immutable `VexUICraftingSnapshot` contains category metadata and ordered recipe IDs, allowing the template
to request `recipe(id)` only for visible entries. The provider's `quote(id, executions)` returns exact
`WholeAmount` costs, owned balances, output quantity, requirements, button state and an opaque domain token.
The template passes that frozen quote to `craft`; the plugin must revalidate it in its existing transaction
boundary. No inventory or currency mutation happens in Core's template. `maximum(id)` is requested only by Max.

Geometry sections are `categories`, `recipes` and `costs`. The default material page contains eight unframed
16-pixel rows with model, name and `required / owned` amounts. Each pager keeps incomplete pages padded
and shares the same footer baseline. Back returns from recipe details to the previous recipe page before leaving
the station view. Static/custom model icons use the normal `VexUIIcon` abstraction at 16 and 32 pixels.
Provider subscriptions own all domain refresh events and must release resources from `close()`.
