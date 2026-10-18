# Upgrade screen animations

`UpgradeAnimationService` is registered in each Paper plugin's standard service scope. It provides a
viewer-local pixel ritual without owning item reservations, costs, probability rolls, or deliveries.
The caller supplies localized `Component` fields through `UpgradeAnimationTexts`, including an
optional `skipHint`; `%progress%` is the only placeholder replaced by Core. Resolve translations
before submitting screen components. The original eight-argument text constructor remains supported.

`play` returns a future with one `UpgradeAnimationOutcome`. Concurrent rituals for the same player
are rejected. The authoritative resolver is invoked exactly once at the start, after the caller has
reserved its subject. Pending persistence holds progress at zero. A durable `false` result is revealed
at a preselected random checkpoint, including 99%, and never enables the success finale.
When that checkpoint is 99%, failure shares the same full hold and amber activation attempt of the last rune as success.
Only after that hold does the rune activate permanently or the ritual collapse; 99% alone never guarantees success.

For a durable `true` result, each reached progress checkpoint has one configurable visual
breakthrough check, including the final checkpoint. The default is 15%. A breakthrough skips the
remaining charge and immediately starts the normal 100% success finale. It does not roll the actual
upgrade chance again, convert failures into successes, or issue extra rewards. Without a breakthrough,
success appears after the final 99% hold. Checks never repeat during idle ticks, flashes, or holds.

The resolver returns a nonblocking future and commits its authoritative result before completing it.
Core polls completion on the
player's entity scheduler and never accesses Bukkit from a future completion thread. Presentation
updates run every tick, and the shared Screen UI coordinator coalesces updates within one tick.

`skip(player)` hides remaining presentation, reuses that same start-time resolver, and completes with
`SUCCESS` or `FAILURE` as soon as its durable result is available. It never cancels a reservation,
refunds a completed attempt, or rerolls the outcome. Repeated requests, a request before the start
callback, and a request during finishing are idempotent. The requesting plugin maps its localized
Shift hint and input listener to this method; players remain protected until resolution and delivery.

Cancellation, disconnect, world change, shutdown, and resolver timeout clean up the screen and owned
task. They do not cancel a resolver that may already have committed. The gameplay service must
serialize abort and commit operations and consult its durable attempt record before deciding
whether to return the original or upgraded item. A `CANCELLED` or `ERROR` presentation outcome alone
does not establish whether a gameplay transaction committed.

`UpgradeAnimationRequest.successChance` is the frozen gameplay quote used to select visual pacing;
the returned durable boolean remains the sole source of success or failure. Pacing profiles choose
random progress milestones and unequal gaps within their validated ranges:

| Minimum quoted success chance | Steps | Charge ticks | Dedicated long pause |
| --- | --- | --- | --- |
| 90% | 2–3 | 60–80 | None |
| 60% | 3–5 | 100–140 | None |
| 30% | 5–7 | 140–180 | None |
| 0% | 7–9 | 200–240 | 60–80 ticks |

`UpgradeAnimationSettings` owns these profiles, `instantSuccessChance`, and bounded fallback charge,
hold, finish, and resolver timeout durations. `pacingFor` picks the highest matching threshold.
The five-argument settings constructor retains fixed-charge pacing with no visual breakthroughs;
the three-argument request constructor supplies a neutral 50% quote for legacy callers.
Sound sequences are keyed by `start`, `step`, `rune`, `hold`, `success`, and `failure`; each cue has
a relative tick delay and a validated sound type, volume, and pitch. Charging pitches rise with
the ritual's progress. Delayed cues share the finite animation task and are discarded on cleanup.

Optional `music` settings select a viewer-local streamed background track, volume, duration in ticks, and looping.
The default `vexcore:upgrade.background` is the supplied stereo Ogg Vorbis file in `resource-pack-tools/sounds/`.
It lasts 24.25 seconds (485 ticks), plays at its original pitch on the master sound source, and starts with the screen.
Looping uses the same finite ritual task and stops the previous instance before restarting. Music stops before the
first success or failure cue, and immediately on skip, cancellation, disconnect, world change, retirement or shutdown.
Skipping while persistence is pending silences the track immediately, then waits for the same durable result before
its result cue. Cleanup stops only this viewer's configured music key, preserving other game sounds and result cues.
The original settings constructors and snapshots without `music` remain valid and silent; the YAML loader supplies
the new default unless `music.enabled: false` is configured. Publish/reload the animation settings after updating.

The progress bar uses actual pixel widths 0–180. Every step sweeps forward from the previous percentage
to the new target over up to twelve ticks, starting quickly and easing to a stop, then holds that width.
Short gaps and the final hold bound
the sweep so it completes before the next step or finale. A selected early failure waits for its sweep
to finish before collapsing the bar. Charging never flashes to full width or retracts.
The bright accent appears immediately and fades out over up to eighteen ticks, with no opacity fade-in.
Both text and sprite helpers
apply the same global -30 GUI pixel Y offset. The twelve-pixel fill starts at relative Y 64 on a shallow
fourteen-pixel gray track. The percentage sits on the anvil's front face at relative text Y -1, using
cool steel-gray ink and a subtle gray one-pixel shadow. Its compact `0%`–`100%` label fits the 24-pixel face;
older translations with a space before `%` are compacted as well. The percentage explicitly disables bold on every
component span, including nested styles from older published translations, so `100%` never becomes an ellipsis.
The bar contains no text. Heading, subject, details,
phase and Shift positions remain unchanged.

The standard Core resource-pack generator supplies 236 upgrade sprites: one frame, sixteen core
frames, sixteen success burst frames, sixteen success-effect overlays, three active glyphs, three inactive glyphs,
and 181 bar fills.
The central forge uses the approved 72×72 source artwork in `resource-pack-tools/artwork/upgrade-forge.png`:
a front-facing shaded anvil with a levitating amethyst. Its silhouette is mirrored left/right.
The generator preserves the metal shading and animates the amethyst. Success brightens and briefly lifts the crystal,
then sends two angular shockwaves and three overlapping mirrored star showers across a separate 184×104 layer.
That layer stays behind the forge and runes and within the existing text gutters. The center rune and outer rune pair
briefly lift in sequence, then return to their original positions. All sixteen success phases span the configured
finish duration and fade to a clear final frame; no success effects play before authoritative success is revealed.
The charge loop breathes evenly across its wrap boundary; failure dims its last forge pose in place.
The panel uses an opaque Minecraft-gray surface, a bright top/left bevel, and a dark bottom/right bevel.
All four square corners have the same mirrored one-pixel cut; their lighting follows the bevel direction.
Three distinct, freestanding 22×26 pixel letterforms use six-pixel straight strokes with separate
highlight and shadow edges. Active and inactive variants share identical silhouettes.
An engraved baseline aligns the glyphs underneath the anvil without individual slots or a surrounding plate.
The slimmer continuous bar has square ends, a muted violet fill and restrained bevels, without a dark inset well.
Text receives a separate one-pixel offset shadow, synchronized with phase and percentage changes.
Only this ritual normalizes pale component colors for the light surface, including older published
translations; other Screen UI renderers keep their existing shadow behavior. Supplied wording, rarity hues,
and component metadata are retained. Text and its shadow are updated together when their content changes.
Every new PNG uses a 256×256
canvas with two protocol marker rows. Visible elements occupy at most 224×240 logical GUI pixels;
the panel includes the phase label and Shift hint with clear spacing above its bottom border.
The ritual uses the centered, scale-1 sprite font variants; no images are generated or loaded
during animation ticks. The generated resource pack must be included in the server's distributed
pack before the screen is used.

For visual QA, run `resource-pack-tools/PreviewUpgradeRitual.java` in Java source mode with the generated pack directory,
an output directory, and optionally `resource-pack-tools/fonts`. It renders actual sprite composites, a looping GIF,
and a contact sheet without interpolation. Preview renders are not in-game captures.
