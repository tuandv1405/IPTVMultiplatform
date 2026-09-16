# Store screenshots

Captured from a running Android emulator on 2026-09-16 (API 37), against the
`iptv-org` Vietnam playlist — free-to-air channels served by the broadcasters'
own servers, so nothing here risks an IP complaint on an IPTV listing.

| Folder | Size | Ratio | Count |
| --- | --- | --- | --- |
| `phone/` | 1080×1920 (one 1920×1080) | 9:16 / 16:9 | 6 |
| `tablet-7in/` | 1200×1920 | 10:16 | 3 |
| `tablet-10in/` | 1600×2560 | 10:16 | 3 |

All pass Play's constraints: every side within 320–3840 px, and no side more
than twice the other. **The emulator's native 1080×2424 is 2.24:1 and would be
rejected**, which is why the captures override the display size.

## Regenerating

With an emulator running and the app installed:

```bash
ADB="$ANDROID_HOME/platform-tools/adb"

# 1. Play-compliant display size (the native one is too tall)
$ADB shell wm size 1080x1920 && $ADB shell wm density 420

# 2. Clean status bar: fixed clock, full battery, no notification icons
$ADB shell settings put global sysui_demo_allowed 1
$ADB shell am broadcast -a com.android.systemui.demo -e command enter
$ADB shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0930
$ADB shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
$ADB shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false

# 3. Capture
$ADB exec-out screencap -p > phone/01-home.png

# 4. Restore
$ADB shell am broadcast -a com.android.systemui.demo -e command exit
$ADB shell wm size reset && $ADB shell wm density reset
```

Tablet sizes: `wm size 1200x1920 && wm density 240` (7"),
`wm size 1600x2560 && wm density 320` (10"). Force-stop and relaunch the app
after changing size so Compose re-measures from scratch.

## Before uploading

- Upload in the listed order; the first screenshot is what most people see.
- `05-fullscreen-landscape.png` is the only landscape shot. Play accepts mixed
  orientations, but a listing reads better with one orientation — either drop it
  or build a landscape-only set.
- The affiliate offer is visible in `03-player.png`. That is the real app and
  accurate screenshots are a policy requirement, so do not retouch it out. If
  you would rather it not lead the listing, change where the offer sits in the
  app instead.
