# glyph-plaude

Not a Claude mod: an Android app for the Nothing Phone (4a) Pro. It adds an always-on Glyph Toy that shows the Plaude logo on the phone's round 13×13 Glyph Matrix (137 LEDs).

![Preview](preview.png)

## Install from the phone

Each push that touches this folder builds the APK in GitHub Actions and publishes it as a release named `glyph-plaude-N`.

1. On the phone, open the repository's **Releases** page and download `plaude-glyph.apk` from the newest `glyph-plaude-N` release.
2. Open the file. Android asks to allow installs from that app (the browser or Files): allow it and install.
3. Settings › Glyph Interface › Flip to Glyph › Always-on Glyph Toy › pick **Plaude**.

## Build locally

1. Download the GlyphMatrix SDK `.aar` from https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit into `app/libs/`.
2. `gradle :app:assembleDebug`, then `adb install app/build/outputs/apk/debug/app-debug.apk`.

## Change the drawing

The drawing is `LOGO` in `app/src/main/java/com/plaude/glyph/PlaudeToy.java`: 13 rows of 13 characters, `#` full, `+` half brightness, `.` off. `editor/glyph-editor.html` is a painter for the grid that exports that array.
