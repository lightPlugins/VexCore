# VexCore Screen UI

Owner-scoped screen UI for Minecraft Java 26.2, using the shared m5x7 metrics and shader
protocol 9. Plugins provide localized content and gameplay data. VexCore owns measurement,
word wrapping, groups, rounded backgrounds, avatar rendering and screen lifetime.

## Panels

Resolve `ScreenUiService` from the plugin-scoped registry and reuse a viewer-owned `ScreenUi`.
Submit immutable `UiNode.Text`, `Group`, `Space` and `Avatar` nodes:

```java
var content = new UiNode.Group("quest", false, List.of(
    new UiNode.Text("title", title),
    new UiNode.Group("objective", true, List.of(
        new UiNode.Text("description", description),
        new UiNode.Text("counter", counter)
    ))
));
UiPanelBounds bounds = screen.panel("quest", content, UiPanelLayout.builder()
    .anchor(ScreenAnchor.TOP_RIGHT)
    .x(-8)
    .y(8)
    .maxWidth(224)
    .scale(0.7)
    .style(style)
    .build());
```

`panel` measures the complete tree before committing one element. Short text shrinks the box.
Long text wraps at word boundaries; long words split at glyph boundaries. Styles are retained.
There is no ellipsis. A column advances by each child's measured height plus the configured gap.
A row reserves its other children's widths and wraps its widest child in the remaining space.
Empty children occupy no space. The background is drawn once around the complete group,
so individual lines do not darken one another through overlapping translucent boxes.

`UiPanelBounds.slots()` exposes anchor-relative coordinates for reserved `Space` nodes. Use these
for specialized bars and cursors; never calculate their text offsets in the consuming plugin.
`panelBackground` exposes the same renderer for fixed presentation areas such as dialogue pages.
`remove(id)` removes the whole panel. `close()` retires the screen.

## Configuration

`UiPanelStyle.parse(section, defaults)` accepts `background`, `opacity`, `radius`, `padding`
and `gap`. Defaults: enabled black background, opacity 0.15, radius 3, padding 4, gap 3.
Opacity is rounded to whole percentages; 0 means invisible, 1 means opaque. Radius supports
0..8 logical pixels, padding and gap 0..16.

`UiPanelDefinition.parse(section, sharedStyle, localizationPrefix)` accepts:

- `enabled`, `show-if`: optional visibility predicate name supplied by the plugin.
- `anchor`, `offset-x`, `offset-y`, `scale`, `max-width`, `layer`: panel placement.
- `style`: optional overrides of the shared style.
- `content`: a tree of `text`, `binding`, `row` or `column` fields.
- Text keys are inferred as `<localizationPrefix>.<field-name>`. A root text field uses `.text`.
  Field names are unique per panel; grouping/reordering does not change the inferred key.
  Text belongs exclusively in language files as a scalar or string list; no `localization` entry is accepted.
- A binding has a `source` key supplied by the plugin.
- Groups have ordered `children` maps. Any field may have `show-if`.

`definition.resolve(localize, bindings, conditions)` resolves data without retaining a player.
The localization callback returns every fully prepared Adventure component as a list, including replacements
and contributed icons. Single localized values become singleton lists; no list element is discarded. New fields using existing replacements need no Java changes. New data
sources require a plugin-side binding. Missing bindings and invalid field types fail explicitly.

`UiPanelSet.parse(section, style, localizationPrefix)` appends each panel name to its prefix.
`UiPanelSet` renders a configured collection, caches unchanged content and stacks panels sharing
an anchor using measured heights. Same-anchor panels must use the same scale. Configuration
maps retain their declaration order. Plugins own refresh timing and replace their view on reload.
Separate screen owners must reserve distinct regions; there is no global cross-plugin layout registry.

## Coordinates and limits

Coordinates are logical GUI pixels. Text, icons, avatars, backgrounds and offsets use the same
scale: 0.5, 0.6, 0.7, 0.8, 0.9 or 1.0. Top anchors share a client-side width cap for three lanes.
With widths up to 256 and inward offsets up to 16, the top-left, top-center and top-right regions
remain separated as the window narrows. Other anchors follow their configured scale.
The client does not send viewport dimensions; resizing scales these lanes, it does not rewrap text.
Arbitrary offsets or overlapping separately owned regions remain the caller's responsibility.

Maximum content-sized panel width is 256 including padding. Height is measured, up to 256,
with final logical bounds within Y -256..256. Larger content is rejected, never silently clipped.
Content trees have a 256-node measurement budget and 12 nested levels; configurations allow
eight levels and 32 children per group. Use dialogue pagination for very large narratives.
One text block supports 32 visual lines and 4096 component/character units. The coordinator
allows eight screens, 128 elements and 16384 aggregate component units per player.

m5x7 supports ASCII, Latin-1, umlauts and progress squares. Anchored text supports color and
bold. Resolve translations, keybinds and scores before submission. Unsupported decorations
are rejected. Contributed `icon/*` fonts use the existing 13-pixel advance contract.

## Avatars, notifications and dialogue

`screens.avatar(player)` asynchronously obtains a 24-pixel face with composited hat layer.
Minecraft texture downloads and decoding run away from player threads, with a bounded cache
and neutral fallback. Apply completed data from the caller's existing player refresh loop.

`UiToastStack` retains merging, bounded waiting, expiry and oldest-at-bottom ordering, and now
uses the same content-sized panel style. Its notifications are static while visible; fixed-card
entrance/fade animation is not used by the new panel presentation.

`prepareDialogue` still pre-wraps immutable pages; `show(page, visibleCodePoints)` retains
typewriter reveal. Pass `UiPanelStyle` to `openDialogue` to share a plugin's configured style.
Input, timing and completion remain owned by the consuming feature.

## Build and verification

Run the screen-UI tests, public API/style checks and ArcaneMonolith composite build.
Routine verification uses Java tests and generated-file contracts. Do not run direct OpenGL helpers.
Deploy matching VexCore and ArcaneMonolith JARs with the combined resource pack, not two stacked packs.
The generator provides 1035 core anchored font files; procedural rounded panels use a 144x14
marked atlas and avatars use a 3x5 marked atlas. Background geometry and opacity are encoded
in component RGB; radius is glyph metadata. No per-width or per-height texture generation is needed.

In-game acceptance checks remain necessary: resource-pack acceptance, bright/dark worlds,
window resizing, GUI scales, long quests, fishing phases, reload, disconnect and reconnect.

## Per-panel text alignment

Set `text-align: LEFT`, `CENTER` or `RIGHT` on a panel (`layout` for Fishing).
Omitting it uses `LEFT`; values are case-insensitive. The panel anchor still controls its screen position.
Each wrapped line aligns within its available content area, excluding padding. Columns share that area;
rows retain their measured cells so text cannot overlap avatars, counters or neighboring fields.
The background remains content-sized. Non-text reservations such as the Fishing bar keep their placement.
Programmatic callers use `UiPanelLayout.builder().textAlign(HorizontalAlignment.CENTER)`.
