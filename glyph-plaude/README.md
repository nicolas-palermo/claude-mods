# glyph-plaude

Not a Claude mod: an Android app for the Nothing Phone (4a) Pro. It adds an always-on Glyph Toy that shows the Plaude logo on the phone's round 13×13 Glyph Matrix (137 LEDs).

![Preview](preview.png)

## How updates reach the phone

- **Drawings and screens** live in [`glyph.json`](glyph.json). The toy downloads it from `main` every few minutes, so a push to `glyph.json` changes the Glyph with no reinstall.
- **Code changes** (anything under `app/`) make the workflow build a signed APK and publish it as release `glyph-plaude-v1.N`. Obtainium on the phone picks it up and installs it over the previous one.

### glyph.json

```json
{
  "screens": [
    { "type": "bitmap", "rows": ["13 rows of 13 characters: # full, + half, . off"] },
    { "type": "clock" }
  ]
}
```

With more than one screen, the toy shows a different one each minute. `clock` shows the time as HH over MM.

## Install on the phone (once)

1. Install [Obtainium](https://github.com/ImranR98/Obtainium) from its GitHub releases.
2. In Obtainium, add the app with URL `https://github.com/nicolas-palermo/claude-mods`, turn on **Filter release titles by regular expression** with `Plaude Glyph`, and install it. Allow installs from Obtainium when Android asks.
3. Settings › Glyph Interface › Flip to Glyph › Always-on Glyph Toy › pick **Plaude**.

The APK is signed with the key in `signing/`. It is committed on purpose: this is a personal app with no data to protect, and a fixed key lets every new APK install over the old one.

## Build locally

1. Download the GlyphMatrix SDK `.aar` from https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit into `app/libs/`.
2. `gradle :app:assembleRelease`, then `adb install app/build/outputs/apk/release/app-release.apk`.

## Draw

`editor/glyph-editor.html` is a painter for the grid that exports the 13 rows.
