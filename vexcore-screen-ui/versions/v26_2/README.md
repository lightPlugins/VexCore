# Screen UI backend: Minecraft 26.2

Owns resource-pack format 88.0 and shader protocol 4. The backend requires the matching client pack.
No BetterHud dependency and no client mod are used.

`pack/protocol.properties` registers one zero-origin font per kind and anchor (18 total).
Each row transports its runtime Y in horizontal advances of `Y * 4096`, then cancels the advance
after its final glyph. The carrier retains zero total width. The shader extracts Y from the
horizontal bucket relative to the carrier center and restores the original local X.
All integer Y origins in -256..256 work without additional bitmap providers or pack rebuilds.
The public enum order matches `anchors` in the manifest and is part of the version's encoding.

Font encoding: `ascent = 8 - ((base + (kind * 9 + anchor) * yCount - minY) * stride + bias)`.
Visible X, including quad width, must remain inside (-2048, 2048); the public layout bounds
keep it in this range. Transport advances remain exactly representable at the supported range.
kind 0 is the 16x14 text cell, including two metadata rows before its visible content.
kind 1 uses a 256x256 alpha canvas with 140x48 artwork at (58,104), scaled 2x by font height 512.
The visible panel remains 280x96. Two signature pixels sit at (0,0) and (0,1) inside the canvas;
no extra rows are added. Its measured font advance is 397, X padding is -116, and the shader
subtracts 208 from panel Y to align the visible content. The GPU reconstructs corner Y.
The bias keeps ordinary carrier positions within the same encoding bucket. Very tall carrier stacks
outside the bucket, or a carrier omitted by vanilla's bossbar cutoff, are outside this prototype.

Original 26.2 text shader reference: the official Mojang client JAR (assets/minecraft/shaders/core/text.vsh).
Changes: guarded VexCore include and position transform before the original projection.
Mojang release notes: https://www.minecraft.net/en-us/article/minecraft-java-edition-26-2

Adding another version requires its adapter, manifest/assets, build wiring and explicit selector
entry, followed by client acceptance tests. Keep shared lifecycle/layout logic in VexCore services.
