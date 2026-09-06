# Archived shader research helpers

These source-launcher programs are research tools, not Gradle tests or plugin runtime code.
Do not run VerifyAnchorGpu.java for routine verification: its synchronous GPU readbacks caused
severe desktop responsiveness problems during development. Do not load the user's Minecraft LWJGL
libraries for automated checks. VerifyShader.java also requires separate native research dependencies.

The supported local verification workflow is the Java layout, lifecycle, toast queue and generated
resource-pack contract tests via Gradle, with --offline --no-daemon --max-workers=2.
Final visuals and client compatibility are checked through normal Minecraft gameplay.