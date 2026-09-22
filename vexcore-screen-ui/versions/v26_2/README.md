# Screen UI backend: Minecraft 26.2

Owns resource-pack format 88.0 and shader protocol 9. Deploy its matching client pack.
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

Generated-font tests verify kind/anchor recovery after integer-to-float conversion, marker
pixels, measured dimensions and opacity payloads. Client acceptance testing is separate.
