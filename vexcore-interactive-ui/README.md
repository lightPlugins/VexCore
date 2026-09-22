# Interactive UI resource pack

Build the separate archive with `gradlew.bat :vexcore-paper:interactiveUiResourcePack`.
The output is `vexcore-paper/build/libs/VexCore-InteractiveUI-26.2.zip`.

The pack targets Minecraft Java 26.2, pack format 88. It provides its own fonts and procedural
rectangle/cursor shaders. It does not modify the source or generated output of the existing
VexCore screen UI resource pack. Its text shader includes the existing screen UI dispatcher so
both features can render when both packs are installed. Load the interactive pack **above** the
existing pack. Other packs that replace `minecraft:shaders/core/text.vsh` or `text.fsh` need an
explicit shader merge; arbitrary shader-pack compatibility is not assumed.

ArcaneMonolith's combined resource pack already includes the current Interactive UI assets and
composed shaders. When using that combined archive, remove separate VexCore UI pack imports from
Nexo or other pack mergers. An older interactive archive includes an older screen dispatcher and
can hide new screen glyphs even when their font files and protocol manifest are up to date.

The 320 by 180 logical canvas is centered and uniformly reduced when the client's GUI resolution
is smaller. Scene and cursor use separate zero-width purple bossbar title carriers. Purple bossbar
textures are transparent in this pack, as in the existing screen UI pack. Ordinary purple bossbars
therefore also have invisible bars while this pack is active; their title remains ordinary text.

Vanilla stops drawing bossbars at one third of the GUI height: only the first three bars at 180 GUI
pixels, or four bars at 240 GUI pixels. Both scene and cursor must fall within that visible prefix;
earlier bars from other features can hide either carrier entirely. The shader cannot reposition a
bar the client never draws. Begin testing without other bossbars; other plugins' bars are not removed.

Scene rectangles use a 4096-color palette and one-pixel borders. Labels use an owned, fixed-advance
Latin-1 bitmap font generated from the bundled, licensed m5x7 font. Text is wrapped and clipped to
each element's rectangle. Components must contain resolved literal text; colors are preserved,
while rich-text click events, hover events, decorations, custom fonts and non-text components are
not part of this renderer. Interactive actions are dispatched by the server's scene hit testing.

Cursor packets carry the latest target position, which the shader displays immediately upon
arrival. Cursor placement does not depend on the game clock and adds no interpolation delay or
settling packets. The renderer encodes zero movement offsets for compatibility with older versions
of this pack, which also display the new target immediately. Input still travels through the server:
network round-trip time and server processing remain visible, unlike a native local mouse cursor.

The renderer keeps scene components separate from cursor updates, so cursor motion does not resend
the scene geometry. New scene snapshots and hover changes rebuild only the scene carrier. The shader
protocol tests cover transport precision, glyph advances, immediate cursor placement, palette decoding and
dispatch compatibility. Those checks do not replace an in-game smoke test at different GUI scales,
with multiple bossbars, high latency and third-party core-shader overrides.
