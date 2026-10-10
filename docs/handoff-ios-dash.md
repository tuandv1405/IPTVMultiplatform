# Hand-off: MPEG-DASH playback on iOS (VLCKit engine)

Status: **code written, not compiled for iOS, not committed** · Branch: `main` (base `c10b1cd`) ·
Date: 2026-10-10

This machine is Windows, so Kotlin/Native iOS targets and the Xcode project can't be built here.
- **Checked here:** `desktopTest` (492 tests, 0 failures) and `assembleDebug` are green.
  `:composeApp:compileCommonMainKotlinMetadata`, the Kotlin compile that iOS uses for common code,
  now passes; see "Also fixed".
- **Not checked here:** the `iosMain` Kotlin (`compileIosMainKotlinMetadata` is skipped on Windows)
  and the Swift. Both were written against VLCKit's real headers and must be verified on a Mac with
  the checklist below.

## What was built

| Layer | File | What |
|---|---|---|
| Common | `composeApp/src/commonMain/.../player/models/DashRouting.kt` | `DashRouting.isDash(url, mime)` (MIME `application/dash+xml`, or a URL path ending in `.mpd`; an explicit HLS hint wins). `shouldUseDashEngine(url, mime, drm, engineAvailable)` (DASH, no DRM, engine registered). `dashMayNotPlayOnIos(engineAvailable, hasDrm)`. `PlaybackCapabilities.iosDashEngineAvailable`. |
| Common | `core/stremio/StreamClassifier.kt` | The iOS "may not play" badge for DASH appears only when no engine is registered or the stream has DRM. MKV is still badged (AVPlayer can't play it). |
| Common | `core/stremio/StremioModels.kt`, `core/tsiptv/TsiptvDetailProvider.kt` | `StreamBehaviorHints.hasDrm` (app-only, never on the wire), set for TS IPTV Source streams with DRM. |
| iOS (Kotlin) | `iosMain/.../player/IosPlaybackEngine.kt` | Exported to Swift: `IosPlaybackEngine` (`view`, `attachListener`, `load(url, headers, startPositionMs)`, `play`, `pause`, `seekTo`, `stop`, `dispose`, `changeVolume`, `changeRate`, `updateNowPlaying`), `IosPlaybackEngineListener` (`onStateChanged`, `onProgress`, `onError`), `IosEngineState` and `IosEngineError` (Int constants), `IosPlaybackEngineFactory`, and the registry `IosPlaybackEngines.dashFactory`. Setting the registry also sets the capability flag. Names avoid `release`/`description`/setters, which Kotlin/Native would rename in Objective-C. |
| iOS (Kotlin) | `iosMain/.../player/IOSMediaPlayer.kt` | Routing in `prepare` after the existing DRM preflight, so DRM is still refused before any request. DASH goes to the engine and everything else to AVPlayer. Only one engine holds a stream: the engine path clears AVPlayer's item and observers, and the AVPlayer path stops the engine. Covered: state, position, duration (≤ 0 means live, so duration 0), seek, rate, volume, mute, stop (pause + seek 0, as with AVPlayer), errors mapped to `PlaybackError`, and the existing playback notification. The engine is kept for the next DASH item and disposed in `release()`. Also fixed: `setupObservers` now removes the previous periodic observer (one was added per item before). |
| iOS (Kotlin) | `iosMain/.../player/ui/MediaPlayerContent.kt` | While the engine plays, the surface is the engine's `UIView` in a `UIKitView`, keyed on the view. The AVPlayer surface now re-attaches its player each time it is created; before this, switching DASH → HLS left it without a player. |
| iOS (Swift) | `iosApp/iosApp/VLCPlaybackEngine.swift` | `VLCPlaybackEngine` and `VLCPlaybackEngineFactory` on MobileVLCKit. Picked up by the Xcode 16 synchronized group, so no pbxproj edit was needed. |
| iOS (Swift) | `iosApp/iosApp/iOSApp.swift` | `IosPlaybackEngines.shared.dashFactory = VLCPlaybackEngineFactory()` in `init()`. |
| iOS | `iosApp/Podfile`, `.gitignore` | `pod 'MobileVLCKit', '~> 3.7.4'`. `iosApp/Pods/` is ignored. `Podfile.lock` and the generated workspace should be committed. |

### `VLCPlaybackEngine.swift` details

- **Headers:** sent as media options: `User-Agent` becomes `:http-user-agent=` and `Referer`/`Referrer`
  becomes `:http-referrer=`. libVLC 3 has no option for any other request header, so
  `Authorization`, `Cookie`, `Origin`, `X-…` and the rest are **dropped** on DASH. Streams that need
  them play on Android only. Values are never logged.
- **Network cache and resume:** `--network-caching=1500` ms. The `startPositionMs` argument becomes
  `:start-time=`. A seek before the stream is seekable is kept and applied on the first `.playing`
  state or time tick. This covers `MediaProgressTracker`, which seeks once duration > 0.
- **States and errors:**
  - `.opening` becomes BUFFERING.
  - `.buffering` becomes PLAYING if VLC is already playing, else BUFFERING (VLC repeats buffering
    events during playback).
  - `.playing`, `.paused`, `.ended` and `.stopped` map to PLAYING, PAUSED, ENDED and IDLE.
  - `.error` reports `IosEngineError.STREAM_FAILED`, which becomes `PlaybackError.STREAM_FAILED`.
    libVLC doesn't expose the HTTP status, so `FORBIDDEN_HEADERS` is never raised on this path.
- **Progress:** `mediaPlayerTimeChanged` reports position and `media.length` (0 for live).
- **Volume, pause and rate:** the app's 0–1 volume maps to VLC's 0–100. `pause()` on a stream that
  can't pause (some live streams) stops it instead; `play()` restarts it. The rate is passed through.
- **Lock screen:** `MPNowPlayingInfoCenter` gets title, artist, elapsed and duration, or "live".
  Play, pause and toggle handlers are on `MPRemoteCommandCenter`; they answer
  `noActionableNowPlayingItem` when the engine has no media.

### Routing rules (`DashRouting`, unit-tested)

| Stream | iOS engine |
|---|---|
| `.mpd` path, or `application/dash+xml`, no DRM | VLCKit |
| DASH with any `drm` | AVPlayer path, refused by the existing preflight (`DRM_NOT_SUPPORTED_DEVICE`). Channels skip to their next non-DRM stream; the picker shows the badge and the DRM message. |
| HLS (`.m3u8` or HLS hint, even with `.mpd` in the path) | AVPlayer |
| Files (`.mp4`, `.mkv`, …), MPEG-TS | AVPlayer (unchanged) |
| No engine registered | AVPlayer, as before |

MKV was not routed to VLC, although VLC plays it: AVPlayer's behaviour for files is unchanged, and
the badge stays. Routing it would be one condition in `DashRouting` plus the badge; that's a product
call.

## Also fixed (pre-existing; this blocked any iOS build)

Compiling common code the way Kotlin/Native does (`compileCommonMainKotlinMetadata`) failed on
`main`:
- `putIfAbsent` (JVM-only) in `XMLTVEPGParser.kt` and `TsiptvSourceResolver.kt`.
- `.use {}` on okio sources without `import okio.use` in `IPTVParserFactory.kt`,
  `TsiptvSourceParser.kt` and `TsiptvSourceService.kt`.

Both are fixed, and the metadata compile now passes. The JVM behaviour is unchanged.

## Installing VLCKit (Mac)

Official distribution, checked on 2026-10-10:
- VideoLAN publishes `MobileVLCKit` on CocoaPods. The latest stable is **3.7.4**
  (podspec: `LGPL v2.1`, iOS ≥ 9.0, `vendored_frameworks: MobileVLCKit.xcframework` from
  `download.videolan.org/pub/cocoapods/prod/`). 4.0 is alpha only (`4.0.0a2`).
- There is no official Swift Package. Carthage binaries also exist; CocoaPods is the documented path.
- I inspected the 3.7.4 binary: it is a **dynamic** framework (Mach-O `MH_DYLIB`, code-signed,
  `FMWK`). Slices: device `arm64/armv7/armv7s` and simulator `arm64/x86_64`.

Steps:
1. Install CocoaPods **≥ 1.16.2** (needed for the Xcode 16 project format `objectVersion = 77`):
   `sudo gem install cocoapods` or `brew install cocoapods`.
2. `cd iosApp && pod install`. This creates `iosApp.xcworkspace` and `Pods/`, and adds the
   `[CP] Check Pods Manifest.lock` and `[CP] Embed Pods Frameworks` build phases to the `iosApp`
   target. `ENABLE_USER_SCRIPT_SANDBOXING` is already `NO`, which CocoaPods' scripts need.
3. From now on **open `iosApp/iosApp.xcworkspace`**, not the `.xcodeproj`. Update any script, CI
   job or fastlane lane that builds the `.xcodeproj` (no iOS CI exists in `.github/workflows`
   today).
4. Commit `Podfile`, `Podfile.lock`, `iosApp.xcworkspace` and the pbxproj changes `pod install`
   made. Don't commit `Pods/`.
5. Build: the Gradle phase (`embedAndSignAppleFrameworkForXcode`) builds `ComposeApp` first. The
   Swift file uses the exported Kotlin API, so any mismatch shows up there.

## Mac verification checklist

**Build**
- [ ] 1. `./gradlew :composeApp:compileKotlinIosSimulatorArm64` passes. If it doesn't, the fix is in
  `IosPlaybackEngine.kt`, `IOSMediaPlayer.kt` or `MediaPlayerContent.kt`.
- [ ] 2. Xcode build of `iosApp` (simulator and device) passes. Check the generated header in
  `ComposeApp.framework/Headers/ComposeApp.h`. `IosPlaybackEngine` should have
  `loadUrl:headers:startPositionMs:`, `play`, `pause`, `seekToPositionMs:`, `stop`, `dispose`,
  `changeVolumeVolume:`, `changeRateRate:`, `updateNowPlayingTitle:artist:artworkUrl:`,
  `attachListenerListener:` and `view`, and `IosEngineState.shared.PLAYING` should be an `int32_t`
  property. If a Swift signature in `VLCPlaybackEngine.swift` doesn't match, follow the compiler's
  fix-it.
- [ ] 3. The app launches. In the Xcode console there are no "nearly matches optional requirement"
  warnings for `mediaPlayerStateChanged(_:)` / `mediaPlayerTimeChanged(_:)`. Such a warning means
  the delegate methods are never called.

**Playback**
- [ ] 4. **Clear DASH VOD**, e.g. `https://dash.akamaized.net/akamai/bbb_30fps/bbb_30fps.mpd` added
  as a one-channel playlist, or in a TS IPTV Source movie:
  - video and audio play;
  - the duration shows and the progress bar moves;
  - pause and play work;
  - seeking ±10 s and dragging the bar work;
  - the end of the stream gives ENDED.
- [ ] 5. **DASH live**, e.g. `https://livesim.dashif.org/livesim/testpic_2s/Manifest.mpd`: it plays,
  the UI shows LIVE (duration 0), and pausing either pauses or stops-and-restarts without a crash.
- [ ] 6. **HLS still on AVPlayer**: a `.m3u8` channel plays in the AVPlayerViewController with
  native controls, and the lock-screen "Now Playing" works as before.
- [ ] 7. **Switching**: DASH → HLS → DASH → HLS, through channel zapping and the player list.
  - No double audio.
  - No black surface (the AVPlayer surface re-attaches its player).
  - The DASH view appears again.
  - Memory stays stable after 10 switches (Instruments › Allocations: one `VLCMediaPlayer`).
- [ ] 8. **Headers**: a DASH stream behind a server that checks `User-Agent` and `Referer` plays.
  Use a local server that logs request headers, e.g. `qa/tsiptv-fixture` serving an `.mpd`.
  - Check the log shows both headers on the manifest **and** segment requests.
  - A header like `Authorization` is not sent (documented limitation).
- [ ] 9. **Resume**: play a DASH movie for 1 min, leave, and open it again from Continue watching.
  It resumes near the saved position (the pending seek is applied once seekable).
- [ ] 10. **Background**: with DASH playing, lock the device or go home.
  - Audio continues (audio session category is playback, as before).
  - The lock screen shows the title and play/pause, and both work.
  - Back in the app, video renders again (the engine view is re-attached).
  - No crash in the log about OpenGL ES or GPU work in the background.
- [ ] 11. **Volume and mute** in the app change DASH loudness, as with HLS.
- [ ] 12. **DRM refusal**: a DASH channel with `#KODIPROP:inputstream.adaptive.license_type=widevine`
  shows "DRM is not supported on this device" at once and makes no network request. A source
  channel with a DRM stream followed by a clear one plays the clear one. In the movie picker, the
  DRM DASH row has the "may not play" badge and the clear DASH row does not.
- [ ] 13. **Errors**: a 404 `.mpd` shows the "stream offline" overlay (`STREAM_FAILED`), and
  zapping away still works.
- [ ] 14. **Picker badge without the engine**: comment out the registration line in `iOSApp.init()`.
  DASH rows are badged and DASH falls back to AVPlayer (fails as before). Put the line back.
- [ ] 15. **App Store build**: Archive and validate (Organizer › Validate App). It should pass with
  the dynamic framework embedded. If validation rejects the `armv7` slices, run `pod update` to a
  newer 3.x or strip them.

## Risks

- **Not compiled here.** The Kotlin/Native → Objective-C → Swift signatures (labels, `Int32`/`Int64`
  mapping, protocol conformance) were written from the Kotlin/Native export rules and the
  MobileVLCKit 3.7.4 headers, but have never been through a compiler. Expect small fixes at
  checklist steps 1–2.
- **Headers.** VLC sends only User-Agent and Referer. Whether libVLC 3's `adaptive` module applies
  them to segment requests as well as the manifest has to be confirmed at step 8.
- **Background video.** VLC's iOS video output is expected to stop rendering in the background while
  audio continues, but this is unverified; check step 10. The Compose surface detaches the engine
  view in the background, as it already did with AVPlayer.
- **Remote commands.** The engine installs `MPRemoteCommandCenter` handlers while it exists. They
  decline when the engine has no media, but they coexist with AVPlayerViewController's own handling.
  Check that lock-screen play/pause still works for HLS after a DASH item was played.
- **No HTTP status from libVLC.** A 401/403 shows the generic "stream failed" message, not the
  "headers may have expired" one.
- **Binary size.** MobileVLCKit adds about 30–40 MB per arm64 slice before App Thinning.
- **No subtitle or track menu for DASH.** The player options dialog isn't wired to VLC tracks, and
  iOS had no track menu before either.
- **No DRM.** DASH DRM (Widevine, PlayReady, ClearKey) is impossible on iOS with AVPlayer or VLCKit.
  FairPlay requires HLS.

## LGPL (VLCKit is LGPL-2.1-or-later)

These notes are not legal advice; have them reviewed before release.
- **Dynamic linking:** MobileVLCKit 3.7.4 is a **dynamic** framework, embedded separately in the
  app bundle (`Frameworks/MobileVLCKit.framework`). This is the usual way LGPL libraries are shipped
  in iOS apps; VLC for iOS and several commercial apps ship VLCKit this way. Do not convert it to a
  static library: LGPL §6 would then require shipping the app's object files so users can relink.
- **Obligations:**
  - Keep VLCKit unmodified, or publish the modifications.
  - Provide its licence text (`COPYING.txt` in the pod: LGPL-2.1) and a notice that the app uses
    VLCKit/libVLC, with a link to its source (`https://code.videolan.org/videolan/VLCKit`, and
    the exact 3.7.4 source tarball).
  - libVLC includes third-party codecs under various licences (e.g. FFmpeg's LGPL parts); VideoLAN's
    binary notice covers them.
- **App Store:** the App Store's usage rules (DRM, limited devices) are the known tension with
  copyleft licences. LGPL-2.1 is generally accepted in practice (unlike GPL), and VideoLAN
  distributes VLCKit for exactly this use. Still, confirm with whoever owns licensing.
- **Licences screen:** the app has no open-source licences screen today, so none was added. Add the
  VLCKit/libVLC notice to one when it exists, or to the App Store listing or a website licences
  page.
