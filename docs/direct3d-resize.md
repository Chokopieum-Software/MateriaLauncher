# Direct3D resize crash workaround

The Windows crash logs `hs_err_pid19480.log` and `hs_err_pid12116.log`
show native access violations in Skia's `finishRecordingAsPicture`, with repeated
`WindowComposeSceneLayer.onDrawBoundsChanged -> SkiaLayer.reshape ->
Direct3DRedrawer.renderImmediately -> SkiaLayer.update` calls. The affected
installation uses NVIDIA; another installation with AMD does not reproduce it.

Skiko 0.150.2 renders synchronously from `SkiaLayer.reshape` on Direct3D.
Compose WINDOW layers can resize during picture recording. Rendering again from
that resize re-enters the recorder before the outer recording completes.

`SkikoResizeTransform` in `build.gradle.kts` changes only that invocation to
`Redrawer.needRender(false)`. The existing frame dispatcher schedules the next
frame after the current call returns. Bounds synchronization and validation stay
in place. The workaround also protects explicit Direct3D configurations.
Ordinary rendering is unchanged; resize rendering is deferred to the dispatcher.

The transform applies to runtime and test runtime dependencies, including the
packaged application and uber JAR. It never modifies the downloaded original
JAR. It fails the build if the expected call is missing or occurs more than once,
so Skiko upgrades require reviewing whether this workaround is still necessary.

`Direct3DResizeTest` exercises the real SkiaLayer resize method with a fake
redrawer: repeated resizes must synchronize bounds and request frames without
calling `renderImmediately`. It does not require creating a graphics device.

Build with `gradlew.bat createDistributable test`. On the affected NVIDIA machine,
remove `%APPDATA%\.MateriaLauncher\use_opengl.flag`, run the rebuilt distribution,
and check startup, tooltips, menus, modal dialogs, resizing, DPI changes, and
dialogs above descriptions. This hardware check remains necessary: the
regression test verifies scheduling, not the native NVIDIA driver behavior.
