# VexCore Interactive UI

An owner-scoped, inventory-independent interactive canvas for Minecraft Java 26.2. The initial
implementation combines a fixed camera, a client-only mount, a synthetic mining target and a
separate resource pack. Looking moves a virtual cursor; starting and aborting mining provide the
left-button press and release edges. No client mod is required.

This is an experimental feature built from established techniques. In particular,
[Sunscreen](https://github.com/Combimagnetron/Sunscreen) already uses mounted-camera input and
mining packets for mouse button state. This implementation does not claim to discover a new
Minecraft input primitive.

## Try the demo

Build the separate pack from the VexCore repository:

```powershell
.\gradlew.bat :vexcore-paper:interactiveUiResourcePack
```

The output is `vexcore-paper/build/libs/VexCore-InteractiveUI-26.2.zip`. The normal Paper `assemble`
task also builds this archive. Install and enable it with the matching VexCore plugin on a Java 26.2
client before opening the demo. The command does not download, push or verify the pack.

The archive remains separate from the existing packs. When using VexCore Screen UI at the same
time, place the Interactive UI pack **above** its pack: the Interactive UI text shader includes a
compatibility dispatcher for the existing Screen UI protocol. Other packs that replace the same
core shaders, including custom ArcaneMonolith shader changes, require an explicit merge and test.

The initial backend requires Survival mode, standing on the ground, no existing vehicle and an
empty main hand. The demo reports these requirements in the player's language. It does not clear or
replace the inventory. Run the following commands with `vexcore.command.debug.ui`:

```text
/vexcore debug ui interactive start
/vexcore debug ui interactive status
/vexcore debug ui interactive stop
```

Move the mouse to position the virtual cursor. The first button counts completed clicks, the second
resets the demo, and the third closes it. Hold the left button on the blue card to drag it across the
canvas. Press or drag on the slider to choose a value from 0 to 100. Shift, right-clicking or changing
the selected hotbar slot closes the interface.
The status command reports the last processed cursor position, left-button state and input count.

## API and ownership

Resolve `InteractiveUiService` from a VexPlugin's scoped service registry. Open and mutate a handle
on the viewer's entity scheduler. Input callbacks also run on that scheduler. `state()` returns an
immutable snapshot that can be read from any thread.

```java
InteractiveUiService interfaces = services.require(InteractiveUiService.class);
ThemeColorService colors = services.require(ThemeColorService.class);
InteractiveUi screen = interfaces.open(player, "skills");

// Resolve the visible label through the owning plugin's LocalizationService first.
screen.put(new InteractiveUiElement(
    "learn",
    new UiBounds(24, 40, 96, 24),
    localizedLearnLabel,
    colors.requireColor("tailwind", "sky", 9).value(),
    true
));

screen.onInput(input -> {
    if (input.kind() == InteractiveUiInput.Kind.CLICK && "learn".equals(input.targetId())) {
        learnSelectedSkill(player);
    }
});
```

Replacing an element with the same ID updates its appearance and bounds. When the flow advances,
remove controls that no longer apply; when it finishes, close the handle:

```java
screen.remove("learn");
screen.close();
```

There is one interactive session per viewer across plugin owners. A plugin cannot look up another
owner's session. Closing the owner's service scope releases its sessions. The caller should close
its handle when its own flow finishes; a closed handle must not be reused.

## Coordinates and events

The canvas measures 320 by 180 logical units and is centered on screen. Its origin is the upper-left
corner: X increases to the right, Y increases downward. Rectangles must be finite, positive and fit
inside the canvas. Right and bottom edges are excluded from hit tests. At most 128 elements are
allowed. Insertion order controls drawing and overlap; noninteractive artwork is not a click target.

`InteractiveUiElement` contains a stable ID, bounds, an Adventure label, an RGB background and an
interactive flag. Use the semantic theme palette for colors and localize visible labels. Labels use
resolved literal text in the pack's Latin-1 font, wrapped and clipped to their element; native text
click events, hover events, custom fonts and decorations are not rendered. The demo uses the public
API rather than a second input or rendering path.

| Event | Meaning |
| --- | --- |
| `MOVE` | Cursor coordinates changed. The target is the current hovered element, or null. |
| `ENTER`, `LEAVE` | The hovered interactive element changed. |
| `PRESS` | The left button became pressed; its current target is captured. |
| `DRAG` | The cursor moved while pressed; the target remains the captured element. |
| `RELEASE` | The left button was released; the target remains the captured element. |
| `CLICK` | Press and release occurred over the same element without crossing the drag threshold. |

Capture allows a slider or card to keep receiving drag and release events after the cursor leaves
its original rectangle. Removing the captured element cancels capture. Handle null targets when
listening to the whole canvas. Server-side gameplay actions must still validate permissions,
ownership, costs and current state; the input target alone is not authorization.

## Compatibility and validation

The cursor is synthesized from player rotation. It is not the operating system pointer, and the
server does not receive arbitrary keyboard keys. This backend currently exposes left-button input;
right-button dragging, text editing and native window widgets are not part of this API.

Cursor feedback is returned directly through the network connection when a rotation sample arrives,
without waiting for the entity scheduler. It displays the latest position immediately, with no
interpolation delay or game-clock dependency. Only the latest pending cursor frame is retained on
the connection event loop; older frames cannot move it backwards. Cursor packets use a separate
carrier without resending scene geometry. Click callbacks, hover and element updates still run on
the player's entity scheduler, using the same projected coordinates as the immediate cursor.

Vanilla sends mounted rotation approximately once per client tick, normally 20 times per second.
Mouse feedback therefore still includes client sampling and a network round trip; this transport
cannot behave exactly like a native local mouse cursor. The revised plugin also sends zero-offset
cursor data understood by the original pack, so its old interpolation cannot add trailing motion.
Use the matching updated pack to remove the obsolete shader clock logic too.

The synthetic mining target is preserved across ordinary outgoing block, section and chunk updates.
An already-full third-party bundle with 4096 packets cannot accept an extra correction after a chunk
or section update; that exceptional case can overwrite the target until the UI is reopened. Holding the left
button can also produce local mining sounds or particles because the input transport uses mining.
These effects still need observation with the real client.

The pack reserves purple bossbar textures for invisible transport: **all vanilla purple bossbar bars
become invisible while this pack is active**, including unrelated purple bars. Their ordinary titles
remain visible. Other bossbar colors retain their normal appearance. The pack targets the 26.2
renderer. Iris, OptiFine, alternative renderers, ViaVersion, Geyser and packs that replace the same
shaders need separate validation. See `vexcore-interactive-ui/README.md` for renderer details.

Minecraft also stops drawing bossbars once their ordinary layout reaches one third of the GUI height:
at GUI heights of 180 and 240 pixels, only the first three and four bars respectively are drawn.
Existing bars can therefore prevent the scene or cursor carrier from rendering at all. Shader
positioning cannot recover a skipped bar. Begin testing without other bossbars; this feature does
not remove or reorder bars owned by other plugins.

Java compilation and tests cannot establish in-game input or visual correctness. Before deployment,
verify the matching plugin and pack together on a real 26.2 client: click versus drag, release outside
the target, Shift/right-click/hotbar close, reconnect, world transitions, resource-pack changes, GUI
scale and window resizing. Check that exiting restores the camera and player presentation. No
completed live client acceptance or multiplayer load test is claimed by this document.
