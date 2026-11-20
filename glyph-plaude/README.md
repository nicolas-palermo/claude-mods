# glyph-plaude

Not a Claude mod: an Android app for the Nothing Phone (4a) Pro. It drives the phone's round Glyph Matrix (13×13 grid, 137 LEDs) with drawings, animations, a clock, scrolling text, live data, notification icons and a Snake game.

![Preview](preview.png)

## What it does

- **Always-on Glyph Toy "Plaude".** Shows the screens in [`glyph.json`](glyph.json), rotating them by duration. While the system keeps the toy connected it redraws at its own pace (up to 25 fps), not only on the one-minute AOD tick.
- **Notifications and calls.** With notification access, a new notification shows its app's icon for a few seconds, and a call shows a phone icon until it ends.
- **Live data.** Weather (Open-Meteo, no key) and any GitHub search count (open PRs, review requests), refreshed every 10 minutes.
- **Snake.** An app that draws on the matrix with `setAppMatrixFrame`. Volume down turns left, volume up turns right; the circle's edge is the wall.
- **Launcher app.** Live preview of what the matrix shows, status of content, data and permissions, and shortcuts to the toy picker, notification access and Snake.

## How updates reach the phone

- **Content** lives in `glyph.json`. The toy downloads it from `main` every few minutes, so a push changes the Glyph with no reinstall. CI parses and renders it in a unit test, so a broken `glyph.json` fails the build instead of reaching the phone (the app also keeps the last valid one).
- **Code changes** (anything under `app/`) make the workflow build a signed APK and publish it as release `glyph-plaude-v1.N`. Obtainium on the phone picks it up and installs it over the previous one. Content-only pushes run the tests without releasing.

## glyph.json

```json
{
  "brightness": 100,
  "screens": [
    { "type": "animation", "frames": [["13 rows"], ["13 rows"]], "frameMs": 110, "loop": false, "seconds": 2 },
    { "type": "bitmap", "rows": ["13 rows of 13 characters: # full, + half, . off"], "seconds": 30 },
    { "type": "clock", "blink": true, "seconds": 15 },
    { "type": "text", "text": "hola", "speed": 12 },
    { "type": "icon", "icon": "heart", "blink": false },
    { "type": "data", "source": "weather", "seconds": 8 },
    { "type": "data", "source": "prs", "seconds": 8 }
  ],
  "data": {
    "weather": { "type": "weather", "lat": -34.6037, "lon": -58.3816 },
    "prs": { "type": "github", "query": "is:pr is:open author:nicolas-palermo", "icon": "pr" }
  },
  "notifications": {
    "enabled": true,
    "seconds": 8,
    "calls": true,
    "apps": { "com.whatsapp": "chat", "com.some.app": null },
    "default": null
  }
}
```

| Field | Meaning |
| --- | --- |
| `brightness` | 0 to 100, applied to every screen. Default 100. |
| `screens[].seconds` | How long the screen stays before the next one. Default 60; for `text`, one full pass (at least 10 s). |
| `animation` | `frames` of 13 rows each, `frameMs` or `fps` (capped at 25 fps), `loop` (default true; false holds the last frame). |
| `clock` | HH over MM; `blink` blinks the centre dot every second. |
| `text` | Scrolls right to left with a 3×5 font; `speed` in pixels per second (2 to 40). Accents are dropped, unknown characters become `?`. |
| `icon` | One of the built-in icons, optionally blinking. |
| `data` | Shows `source` from `data` with its icon. Weather shows the rounded temperature and an icon for the conditions; `github` shows the result count of `query`. |
| `notifications.apps` | Package name to icon. Merged over the built-in map (WhatsApp, Telegram, Messages, Slack, Gmail, Outlook, GitHub, Calendar, Instagram, Spotify); `null` removes one. |
| `notifications.default` | Icon for apps not in the map, or `null` to ignore them. |

Built-in icons: `bell`, `chat`, `mail`, `phone`, `git`, `heart`, `star`, `calendar`, `music`, `alert`, `check` (9×9), `sun`, `cloud`, `rain`, `storm`, `snow`, `fog`, `pr`, `dot` (5×5) and `plaude` (13×13).

[`editor/glyph-editor.html`](editor/glyph-editor.html) paints drawings and animations on the round grid and exports them as `screens` entries.

## Install on the phone (once)

1. Install [Obtainium](https://github.com/ImranR98/Obtainium) from its GitHub releases.
2. In Obtainium, add the app with URL `https://github.com/nicolas-palermo/claude-mods`, turn on **Filter release titles by regular expression** with `Plaude Glyph`, and install it. Allow installs from Obtainium when Android asks.
3. Open **Plaude Glyph**: tap **Elegir Plaude como Always-on Glyph Toy** and pick **Plaude**, then **Dar acceso a notificaciones** if you want notification icons.
4. If the matrix stays dark after picking the toy, turn Glyph Interface off and on. It is a known system bug.

The APK is signed with the key in `signing/`. It is committed on purpose: this is a personal app with no data to protect, and a fixed key lets every new APK install over the old one.

## Develop

- `app/src/main/java/com/plaude/glyph/core/` is plain Java with no Android dependencies: grid and mask, font, icons, screens, playlist, the engine that picks overlay or screen and the next redraw time, and Snake. It is covered by JUnit tests in `app/src/test/`.
- The Android layer is thin: `PlaudeToy` (toy service loop), `GlyphNotificationListener`, `LiveData`, `RemoteContent`, `MainActivity`, `SnakeActivity` (its own `:game` process, because the SDK keeps one connection per process).
- The SDK's raw frame is 0..4095 per LED, not 0..255.
- Build locally: download the unified SDK `.aar` from `Nothing-Developer-Programme/Glyph-Developer-Kit/sdk/` into `app/libs/`, then `gradle :app:testReleaseUnitTest :app:assembleRelease`.
