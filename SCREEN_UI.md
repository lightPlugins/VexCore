# VexCore Screen UI

Owner-scoped bossbar UI for Minecraft Java 26.2, using m5x7 and the matching resource-pack
shader protocol 8. ArcaneMonolith uses this API for dialogue, quest progress, its permanent
info card and item acquisition notifications. No client mod is required.

## Build and test

Build `:vexcore-services:test :vexcore-paper:test :vexcore-paper:shadowJar :vexcore-paper:screenUiResourcePack`.
Use `--offline --no-daemon --max-workers=2` on this development machine. Routine verification
uses Java unit tests and generated-file contracts only. Do not run the direct OpenGL helpers.

Outputs are `vexcore-paper/build/libs/VexCore-1.0.0-SNAPSHOT.jar` and
`VexCore-ResourcePack-1.0.0-SNAPSHOT.zip`. ArcaneMonolith's `resourcePackZip` includes these
UI assets and its own icons in one combined pack; use that ZIP instead of stacking both packs.
Always distribute matching plugins and pack. An old protocol 4 pack cannot render the new cards.

`/vexcore debug ui toggle` tests all nine anchors. `/vexcore debug ui dialogue` previews the
existing dialogue panel. Neither command installs a pack. Test window resizing, GUI scale,
pack acceptance, reconnect and normal gameplay in Minecraft before deployment.

## API

Resolve `ScreenUiService` from a VexPlugin's scoped registry. Open a screen with
`service.open(player, "screen-id")`; reuse that handle until it is closed. Closing or unloading
an owner removes its elements. All owners share one carrier bossbar per player.

```java
ScreenUi ui = screens.open(player, "quest-hud");
ui.box("quest", List.of(
    Component.text("A new beginning").decorate(TextDecoration.BOLD),
    Component.text("Mine stone: 5 / 10")
), UiBoxLayout.builder()
    .anchor(ScreenAnchor.TOP_RIGHT).x(-8).y(8)
    .height(48).padding(6).lineSpacing(2).scale(0.7)
    .alignment(HorizontalAlignment.LEFT).build());
ui.removeBox("quest");
ui.close();
```

`box` creates `<id>.background` and `<id>.text`. Do not reuse these IDs separately.
Use `textBlock` for standalone component lists and `textureBlock` for standalone backgrounds.
`setLines` changes an existing text block; replace its layout under the same ID to move it.
Layers draw low to high. LEFT/CENTER/RIGHT aligns each line inside the box; box placement is
inward from its screen anchor. Cards have logical width 240 and heights 48, 64, 80, 96 or 128. Single-line drop cards are 160 x 20.
`UiTexture.card(height)` is rounded, translucent and tintable through `TextureLayout.tint`.
Card PNGs are 256 x 256 alpha canvases. `UiTexture.PANEL` retains the 280 x 96 dialogue artwork.

## Coordinates and fonts

Anchored X/Y are local GUI pixels, not physical framebuffer pixels. Positive X moves right,
positive Y moves down. Supported text glyph origins are -256..256 on Y and layouts validate X.
An anchor stays relative to the screen edge or center on resize; the server does not need resize
packets. Fixed-width content does not automatically rewrap when the window changes.

`scale(0.7)` sets all local geometry and offsets to 70 percent of their logical GUI size.
Supported presets are 0.5, 0.6, 0.7, 0.8, 0.9 and 1.0. Scale is independent of window width;
anchors still follow the window edges/center. A 240-unit card at 0.7 is 168 GUI pixels wide.
There is no automatic collision avoidance or additional shrink on narrow windows. Dialogues remain
full size. Existing API compact(true/false) selects 0.5/1.0 when no explicit scale is supplied.
The Arcane configuration uses scale instead of compact. Protocol 7 requires a matching rebuilt pack
once; switching between supported scales afterward does not require regeneration.
m5x7 supports ASCII, Latin-1 (including umlauts), and the filled/empty progress squares.
Anchored text supports colors and bold; italic, underline, strikethrough and obfuscation are
rejected. Resolve translatable/keybind/score components before submission. Component lists,
embedded newlines and blank lines are supported. WRAP, ELLIPSIS and REJECT govern horizontal
overflow. Box lines use ELLIPSIS and must fit the selected height; use the dialogue API for paging.
Anchored icons use one E001 glyph in an `icon/<id>` font. The pack must supply that icon's
versioned normal/compact and animated font variants; ArcaneMonolith generates them automatically.

## Animated notifications

`UiToastStack<T>` is a bounded generic queue. Supply merge, component and tint functions, then
call `push(key, value, worldGameTime)` and `tick(worldGameTime)` on the owning player thread.
It owns no Player, scheduler or persistence. Close it on quit, owner shutdown, world change or
resource-pack loss. Use world game time, not daylight time or wall-clock milliseconds.

At most five cards are visible and 64 distinct keys wait. Identical keys merge without extending
one card indefinitely. `push` returns false on overflow or a closed screen; callers can use chat.
Oldest entries sit at the bottom, newer entries above. Cards enter from the right and fade in over
5 ticks, hold for 60 ticks and fade out over 8 ticks. Horizontal motion and alpha interpolate per
client frame, with no server updates during the hold. Vertical stack reflow uses short, coalesced
server updates; it is not frame-rate interpolation. Animation colors use 3 bits per RGB channel.

## Dialogue panels

`prepareDialogue(speaker, paragraphs, layout)` pre-wraps immutable pages using actual font widths.
`openDialogue(player, id, prepared)` returns a handle; `show(page, visibleCodePoints)` reveals
already laid-out text. `setHint` sets a separate hint. The caller owns input, timing and completion.
The standard panel has a bold speaker, three body lines and a hint line. Sentences start new pages;
oversized sentences split across pages. There are at most 128 pages and a shared content budget.

## Lifetime and performance

The coordinator schedules one player-bound render two ticks after mutation, coalesces changes and
does not resend equal output. There is no repeating task per element. Quit, retirement, screen close
and owner shutdown release handles and components. Limits are eight screens, 128 elements and
16,384 aggregate component units per player; one text block allows 4,096 units and 32 visual lines.
Feature plugins should update only changed content and use existing player-bound tasks.

## Version and compatibility boundaries

`vexcore-screen-ui/versions/v26_2` owns the adapter, manifest and shader assets. Protocol 7 ships
927 core anchored font files, plus separately generated icon fonts. The versioned shader overrides
both Minecraft text vertex and fragment stages; ordinary glyphs retain vanilla behavior. Two reserved
low-alpha marker pixels identify UI glyphs. Generator output is derived; edit source assets/tools.

Purple bossbar sprites are transparent globally. Vanilla's bossbar stack cutoff can hide the carrier
when many other bars precede it; there is no global packet reordering. Core shaders are not a stable
Mojang extension API. Vulkan, shader mods, protocol translation and competing packs need in-game
validation. Pack acceptance is the caller's responsibility and does not verify the exact pack version.
No 50-100-player live-load test or final in-game visual acceptance is claimed by the Java tests.

The bundled m5x7 font by Daniel Linssen is CC0; see `resource-pack-tools/fonts/LICENSE.txt`.
The pack includes `FONT-LICENSE.txt`. The TTF is used only during asset generation.

## Short text transitions (protocol 7)

`TextBlockLayout.transition(UiTransition.move(gameTime, ticks, offsetX))` moves an anchored text
block from a relative horizontal offset to its destination. `UiTransition.fadeOut(gameTime, ticks)`
fades an overlay away. Both run per client frame using the existing glyph atlas; they create no
scheduler. Supported durations are 2, 4, 8 and 16 ticks, offsets -32..31 logical pixels, scales
0.5..1.0 in 0.1 steps. Tint uses three bits per channel. Icons and simultaneous toast animation
are unsupported. Reuse the same element ID and replace/clear finite transitions within 250 ticks;
the compact clock wraps every 500 ticks. Use world game time, with the shared two-tick render lead.
Rebuild and distribute VexCore and the combined resource pack together. Protocol 6 packs do not
contain these fonts. The original TextBlockLayout constructor remains available.


## Missing text glyphs

The renderer measures and renders the same replacement glyphs when localized text contains characters
outside the bitmap atlas. Typographic minus/dashes, quotes, ellipses, bullets and horizontal arrows use
ASCII equivalents; unsupported whitespace becomes a space and other missing glyphs become `?`.
Styles and input/output budgets remain enforced. Contributed icon glyphs stay strict. This behavior
uses existing pack glyphs and requires no resource-pack rebuild.

## Contributed anchored sprites (protocol 8)

`UiTexture.sprite(Key asset, int width, int height)` renders a plugin-owned bitmap with the existing
screen anchor and scale presets (0.5 through 1.0). Static sprites use no animation payload. Declare
content at most 254 x 240 pixels, beginning at (0, 2) on a 256 x 256 RGBA atlas; wider panels must be
split. Metadata pixels are (0,0)=0x01565831 and (0,1)=0x01434F52. An alpha-1 pixel at
(width-1,1) preserves the declared width+1 glyph advance. Leave unused canvas transparent.

The plugin pack supplies bitmap fonts at `<namespace>:ui/v26_2/sprite/<asset-path>/<scale-percent>/<anchor>`.
Each provider has height 256, the single E100 glyph, and ascent `8 - (encoded * 4096 + 512)` where
`encoded = 1024 + ((73 + scale-tenth - 5) * 9 + anchor-ordinal) * 513 + 256`.
The existing horizontal Y transport carries the runtime offset. Shader kinds 73..78 recognize
256-square cells, preserve RGB/alpha and scale around the anchor. Font assets remain plugin-owned;
VexCore supplies the rendering contract, validation and shared shader. Deploy the matching protocol-8
pack and plugin together. Existing card/text/transition kinds retain their encoding.

### Dialogue skins

The four-argument `ScreenUiService.openDialogue` accepts a `DialoguePanelSkin`: up to eight static
texture parts positioned relative to the standard panel and an optional reserved hint width. Parts
must fit within 280 x 96. The built-in dialogue owns installation, replacement and cleanup, so callers
retain the same pagination/reveal lifecycle without accessing internal screen element identifiers.
The existing three-argument overload retains the default panel. Sprite skins require matching plugin
assets in the accepted resource pack.
