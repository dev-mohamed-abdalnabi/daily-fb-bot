# Dynamic Island for Android: architecture

An iPhone-style Dynamic Island for Android 10+ (tuned for Android 12 / MIUI & HyperOS). It's drawn as a system overlay that sits on the camera cutout and morphs to show real events: media, notifications, calls, charging, network, timers, clipboard and live activities.

## 1. Constraints that shaped the design

| Constraint | Consequence |
|---|---|
| Third-party apps can't draw in the status bar | `TYPE_APPLICATION_OVERLAY` window (needs *Display over other apps*), laid out with `LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS/SHORT_EDGES` so y=0 is the physical top edge |
| The overlay must survive in the background | Foreground service (`specialUse` type on Android 14) + boot receiver + Xiaomi autostart/battery guidance |
| An overlay window swallows every touch inside its bounds | The window is resized to hug the pill (it grows immediately when animating out and shrinks only after the spring settles) |
| Android 10+ blocks background clipboard reads | The clipboard is read only while our window has focus (double-tap toggles focusability for at most 1.5 s), or via the *Pin to Island* share target. Writing (Copy) is allowed from the background |
| Notifications & media sessions are protected | `NotificationListenerService`. The same grant unlocks `MediaSessionManager.getActiveSessions()` |
| Unit tests must run on the JVM with no device | All decision logic lives in `core/` with **no Android imports** and an injected clock |

## 2. Layers

```
ui (Compose, Material 3 dynamic colour)
  MainActivity · SettingsScreen (24 options, presets, export/import, timers, permissions)
  XiaomiGuideScreen · ShareToIslandActivity · Theme
        │ StateFlow<IslandSettings>                │ TimerManager calls
settings                                         service
  IslandSettings (24 fields, sanitized())          IslandOverlayService (FGS, owns the window)
  SettingsCodec (versioned JSON export/import)       ├─ IslandView (Canvas pill + springs + gestures)
  Presets · SettingsRepository (SharedPrefs)         ├─ GlyphPainter (vector glyphs, no assets)
                                                     ├─ Monitors: Battery, Connectivity, NetSpeed,
core (pure Kotlin, unit-tested)                      │   Media (+ IslandNotificationListener)
  EventEngine   – priority, TTL, dismissal           ├─ TimerManager (persisted), ClipboardHelper
  IslandEvent   – kind/priority/visual/actions       └─ Stores: artwork, PendingIntents, app icons
  Spring        – damped oscillator, fixed sub-steps
  CutoutGeometry– anchor from cutout rects         receiver: BootReceiver, LiveActivityReceiver (API)
  PrivacyFilter – notification/clipboard redaction util:     Permissions, XiaomiSupport
  NetSpeedCalculator / ByteRateFormatter
  CountdownTimer / Stopwatch / TimeFormat
  BatteryTracker / ChargingClassifier / ConnectivityTracker
```

Data flow: **sources → `EventEngine.post()` → `StateFlow<IslandState>` → service picks mode (IDLE / COMPACT / EXPANDED) → `IslandView.render()`**. Gestures go back the other way: view → service → media controller / timers / PendingIntent / `engine.dismiss`.

## 3. Event engine

* One entry per `key` (e.g. `media`, `battery:charging`, `notif:<sbn.key>`). Posting the same key again replaces the entry.
* **Priority** comes from `EventKind`: TIMER_DONE 100 › CALL 95 › BATTERY_LOW 88 › CHARGING 80 › NOTIFICATION 70 › CLIPBOARD 60 › CONNECTIVITY 55 › LIVE_ACTIVITY 50 › TIMER 45 › STOPWATCH 44 › MEDIA 40 › NET_SPEED 10. Ties go to active events first, then the most recent.
* **Transient** events (TTL) auto-expand the island, then expire. The service schedules `prune()` at `nextExpiryAt()`.
* **Persistent** events (media, timers, calls, live activities, net speed) show compact until tapped.
* `postedAt` only changes when `contentId` changes, so re-posting a ticking timer every 500 ms doesn't re-expand it, while a new message does.
* `dismiss()` removes transient events and hides persistent ones until their `contentId` changes (a new song brings media back).
* The settings feature toggles are applied as a `kindFilter`: hidden kinds are filtered out, not deleted, so turning one back on restores it immediately.
* `primary` is drawn in the pill. `secondary` is drawn as a detached circular bubble (the iOS "split" look) in compact mode.

## 4. Rendering & motion

* `IslandView` is one `View` that draws everything on a `Canvas`: rounded pill, compact layout (leading icon/art + progress ring + trailing text or "now playing" bars), expanded card (icon, title, subtitle, progress bar, circular action buttons). Compact and expanded content cross-fade by *expandedness* = (h − h_collapsed)/(h_expanded − h_collapsed).
* Width and height are each driven by a `Spring` (user-set stiffness and damping ratio), and content alpha by a critically damped spring. `Choreographer` drives the frames and stops as soon as all springs rest (no idle CPU use).
* Integration is semi-implicit Euler with 4 ms sub-steps. Frame gaps over 64 ms are clamped, so it stays stable at any refresh rate (60–144 Hz).
* Glyphs (battery, bolt, wifi, cellular, VPN, Bluetooth, timer, music, bell, clipboard, call, arrows, transport controls) are vector paths, so no icon assets are needed. App icons come from `PackageManager`, and album art from `MediaMetadata`.

## 5. Cutout detection

`CutoutGeometry.anchor()` takes the cutout rects (`WindowMetrics.windowInsets.displayCutout` on API 30+, `Display.getCutout()` on API 29) and keeps the ones on the top edge. It picks the one nearest the horizontal centre:
* A centred punch-hole or notch (within 12 % of the screen width) anchors the pill to it, and the idle pill is forced to be at least `cutout + 2×8 dp` wide and `cutout height + 6 dp` tall so the camera is covered.
* With a corner punch-hole, the pill stays centred horizontally but aligns with the hole vertically.
* With no cutout, the pill is centred in the status bar.
It's recomputed on every configuration change (rotation). In landscape the island can be hidden (option 24).

## 6. Sources

| Source | API | Event |
|---|---|---|
| Media | `MediaSessionManager` + `MediaController.Callback`, 1 s progress tick while playing | MEDIA (art, title, artist, remaining time, prev/play-pause/next) |
| Notifications | `NotificationListenerService` with Ranking importance ≥ DEFAULT that respects DND. Skips group summaries, media-style, our own, and plain ongoing notifications | NOTIFICATION (transient) |
| Calls | notification `CATEGORY_CALL` | CALL (persistent until the notification is removed) |
| Live activities | (a) notifications with progress / navigation / stopwatch category; (b) public broadcast API `com.abdalnaby.dynamicisland.action.LIVE_ACTIVITY` guarded by permission `com.abdalnaby.dynamicisland.permission.POST_LIVE_ACTIVITY` | LIVE_ACTIVITY |
| Battery | `ACTION_BATTERY_CHANGED` + power connected/disconnected. Wattage from `BATTERY_PROPERTY_CURRENT_NOW` × voltage, with µA/mA and V/mV normalisation | CHARGING, BATTERY_LOW (20/10/5 % with hysteresis), "Fully charged" |
| Connectivity | `registerDefaultNetworkCallback`, plus Bluetooth ACL connect/disconnect (device name if *Nearby devices* is granted) | CONNECTIVITY (only when the type changes) |
| Net speed | `TrafficStats` total rx/tx every 1.5 s, **only while the screen is on**, shown above a user threshold | NET_SPEED |
| Timer/stopwatch | `TimerManager` (wall-clock based, persisted in prefs so it survives restarts). The alarm is a high-importance notification + vibration | TIMER, STOPWATCH, TIMER_DONE |
| Clipboard | focus-gated read (double-tap), *Pin to Island* share target, Copy action | CLIPBOARD |

Live activity broadcast example (sender declares `<uses-permission android:name="com.abdalnaby.dynamicisland.permission.POST_LIVE_ACTIVITY"/>`):

```kotlin
sendBroadcast(Intent("com.abdalnaby.dynamicisland.action.LIVE_ACTIVITY")
    .setPackage("com.abdalnaby.dynamicisland")
    .putExtra("id", "order-42").putExtra("title", "Order on the way")
    .putExtra("subtitle", "Arrives 14:05").putExtra("progress", 0.6f).putExtra("compact", "6 min"))
// end it:  .putExtra("id", "order-42").putExtra("end", true)
```

## 7. Privacy

`PrivacyFilter.filter()` combines the user level (SHOW_ALL / HIDE_CONTENT / APP_NAME_ONLY), the *hide content when locked* option and the posting app's own `visibility`:
* `VISIBILITY_SECRET` + locked → not shown at all.
* `VISIBILITY_PRIVATE` + locked, or *hide when locked* → at least HIDE_CONTENT.
* The lock screen can only make the level stricter, never looser.
Clipboard previews mask clips flagged by the source app with `EXTRA_IS_SENSITIVE` (passwords/OTP, Android 13+). With HIDE_CONTENT they show only the character count. Nothing leaves the device: the app has **no INTERNET permission**.

## 8. Gestures

| Gesture | Action |
|---|---|
| Tap | expand / collapse |
| Tap a button (expanded) | media prev/play-pause/next · timer pause/resume/+1:00/cancel · stopwatch pause/resume/lap/reset · copy · dismiss |
| Long-press | open the source (notification `contentIntent`, media `sessionActivity`, or the app's launch intent) |
| Swipe up | dismiss the current event |
| Double-tap | show clipboard (focus-gated) |
| Tap outside | collapse |
| Tap the bubble | open the secondary event's source |

## 9. Customisation (24 options)

1 enabled · 2 idle width · 3 idle height · 4 expanded width · 5 expanded height · 6 X offset · 7 Y offset · 8 corner roundness · 9 spring stiffness · 10 spring damping · 11 auto-collapse seconds · 12 background opacity · 13 accent colour (0 = Material You) · 14 media · 15 notifications · 16 notification privacy level · 17 hide content when locked · 18 charging/battery · 19 net speed · 20 net-speed threshold · 21 connectivity · 22 clipboard · 23 timers · 24 hide in landscape.

`IslandSettings.sanitized()` clamps every value. **Presets** (Classic, Minimal, Bouncy, Glass, Private, Battery saver) change only look, motion or privacy, never feature switches. **Export/Import** writes a versioned JSON envelope `{format, version, exportedAt, settings}` through the Storage Access Framework. Import ignores unknown keys, defaults missing ones and coerces unknown enum values. It rejects foreign formats, future versions and files over 256 KB, then sanitises.

## 10. Lifecycle

* The `MainActivity` switch sets `settings.enabled` → `IslandOverlayService.start()`. The service stops itself when `enabled` becomes false.
* `BootReceiver` (BOOT_COMPLETED, QUICKBOOT_POWERON, MY_PACKAGE_REPLACED) restarts the service if it's enabled and the overlay permission is held. `specialUse` FGS starts from boot are allowed, and holding SYSTEM_ALERT_WINDOW exempts the app from background-start limits.
* `START_STICKY` plus persisted timers make it survive process death.
* Xiaomi: `XiaomiSupport` detects MIUI/HyperOS (`ro.miui.ui.version.name`, `ro.mi.os.version.name`, brand). It deep-links to Autostart, *Other permissions* (pop-up windows while running in background) and the battery saver. When a screen doesn't exist on the ROM it falls back to App info.

## 11. Build layout

The repo root holds the flat sources. `setup.sh` generates the Gradle project: `app/src/main/java/<package path>` from each file's `package` line (`*Test.kt` → `app/src/test`), resources, Gradle Kotlin DSL files and the wrapper. Toolchain: AGP 8.5.2, Kotlin 2.0.20 (Compose compiler plugin), Gradle 8.9, JDK 17, compileSdk/targetSdk 34, minSdk 29. CI: `.github/workflows/build.yml`.
