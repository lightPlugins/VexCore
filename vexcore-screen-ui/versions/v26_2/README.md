# Screen UI backend: Minecraft 26.2

Owns resource-pack format 88.0 and shader protocol 10. Deploy its matching client pack.
The shared API, layout and lifecycle are described in the root `SCREEN_UI.md`.

Font ascent encodes a kind and one of nine anchors. Zero-net horizontal advances transport
runtime Y as `Y * 4096`; the shader restores local coordinates relative to the chosen anchor.
All Y origins -256..256 use the same generated font per kind/anchor. The enum order is contractual.
Two marked pixels distinguish owned glyphs from ordinary Minecraft text.

Kinds 0..78 retain text, texture, animation, transition and contributed sprite primitives.
Kinds 79..84 are procedural rounded panels at scales 0.5..1.0. Their nine 16x14 glyph cells
encode radii 0..8 in metadata. RGB carries `(width - 1) << 15 | (height - 1) << 7 | opacityPercent`.
Each quad expands to its measured size; fragment evaluation clips the rounded corners.
The glyph advance is always 17 and is cancelled, independent of its visible dimensions.
Kinds 85..90 render opaque 3x3 avatar pixels at the same scales with advance 4.

Top anchors cap scale to `(guiWidth / 3 - 8) / 272`, shared by every primitive. This keeps the
default three-lane layout separated on narrow windows without client viewport messages.
All shader changes are guarded by the owned-glyph marker; ordinary text retains vanilla behavior.

Protocol 10 adds an independent reward-card carrier range, 65536..98304, dispatched before the screen UI.
Four primitives and sixteen current/previous slot pairs encode shared transforms. RGB carries two modulo-120 clocks,
a pulse bit and 3-bit tint channels. `RewardCardAssets` generates marked fixed-width menu text, model previews and
the VexUI frame. Existing anchored font kinds remain unchanged.

Progression notices use carrier positions 98304–101887 and the same marked text atlas. The seven
parts draw labels, the new level, four fragments of the previous level, and sparks. RGB carries
the modulo-120 start tick, a text tone, compact sizing for long numbers, and a twinkle index for
ambient sparks. The GUI text shader draws a glyph-shaped shadow and three shine passes across
the visible text. The exit lifts the glyphs in a wave, dissolves their ink and shadow, and
reuses the impact spark quads for an upward trail. It finishes by tick 102 and the server retires
it at tick 104.

Generated-font tests verify kind/anchor recovery after integer-to-float conversion, marker
pixels, measured dimensions and opacity payloads. Client acceptance testing is separate.
