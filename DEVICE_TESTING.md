# Device testing checklist

> Status: **not yet built or run anywhere.** This checklist is for the first install on a real phone.
> Main target: Android 12 (API 31/32), ideally a Xiaomi/Redmi/POCO on MIUI 13/14 or HyperOS. Please also check one Pixel/AOSP-like device.

## 0. Install & logs
```bash
./setup.sh && ./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -s IslandService MediaMonitor AndroidRuntime
```
Record the device model, ROM version (Settings → About), screen resolution and cutout type (centre hole / corner hole / notch / none).

## 1. Permissions & start
| # | Step | Expected |
|---|---|---|
| 1.1 | Open the app on first launch | The Setup card lists the missing permissions. The status reads "Needs Display over other apps…" |
| 1.2 | Tap Grant for each permission | Each opens the right system screen. Back in the app, it shows ✓ (refreshes on resume) |
| 1.3 | Turn on **Show the island** | Status reads "Running". A silent "Dynamic Island is active" notification appears. A black pill covers the front camera |
| 1.4 | Turn it off | The pill disappears and the service notification is gone |

## 2. Cutout & layout
| # | Step | Expected |
|---|---|---|
| 2.1 | Centre punch-hole device | The idle pill is centred on the camera and fully covers it |
| 2.2 | Corner hole / no cutout | The pill is centred horizontally and sits in the status bar |
| 2.3 | Rotate to landscape | The pill hides (option 24 on). With the option off, it re-anchors |
| 2.4 | Move the X/Y offset and size sliders | The pill moves and resizes live with a spring animation |
| 2.5 | Tap the status bar area beside the pill | Notification shade still opens (the window hugs the pill) |

## 3. Motion
| # | Step | Expected |
|---|---|---|
| 3.1 | Apply the **Bouncy** preset, then trigger an event | Visible overshoot when expanding |
| 3.2 | Apply **Minimal** | No overshoot, smaller pill |
| 3.3 | 90/120 Hz display | Animation is smooth, with no jitter or jumps after the screen wakes |
| 3.4 | Idle for 1 min with Developer options → GPU rendering profile | No continuous frames when nothing is animating |

## 4. Media
| # | Step | Expected |
|---|---|---|
| 4.1 | Play in Spotify / YouTube Music / YouTube | Compact view: album art + remaining time (`-2:31`). The time counts down each second |
| 4.2 | Tap to expand | Title, artist, progress bar, prev / play-pause / next buttons |
| 4.3 | Tap each button | The player responds. The play/pause icon flips |
| 4.4 | Long-press | Opens the player app |
| 4.5 | Swipe up | Media hides, then comes back when the next track starts |
| 4.6 | Close the player | The media event disappears |

## 5. Notifications & privacy
| # | Step | Expected |
|---|---|---|
| 5.1 | Receive a WhatsApp/Telegram message (unlocked, Show everything) | The island auto-expands with sender + text, then collapses after N s and expires |
| 5.2 | Set **Hide content** | Sender + "New notification" |
| 5.3 | Set **App name only** | Only the app name |
| 5.4 | Lock the screen, receive a message, then unlock quickly | Content hidden (option 17), and nothing appears for apps marked secret |
| 5.5 | Enable DND | No island alerts for blocked notifications |
| 5.6 | Low-importance / silent notification | Not shown |
| 5.7 | Long-press an alert | Opens the conversation |
| 5.8 | Incoming call (or a VoIP call notification) | CALL stays until the call notification ends |

## 6. Live activities
| # | Step | Expected |
|---|---|---|
| 6.1 | Download a big file in Chrome | Persistent live activity with % progress, removed when done |
| 6.2 | Google Maps navigation | Navigation shows as a live activity |
| 6.3 | Broadcast API (from a test app holding the permission): see ARCHITECTURE.md §6 | It appears, updates and ends (`end=true`) |
| 6.4 | `adb shell am broadcast -a com.abdalnaby.dynamicisland.action.LIVE_ACTIVITY -p com.abdalnaby.dynamicisland --es id t --es title Test` | **Rejected**: the shell doesn't hold the permission. That confirms the protection works |

## 7. Battery
| # | Step | Expected |
|---|---|---|
| 7.1 | Plug in the charger | "Charging / Fast charging", level %, wattage (if the device reports current), green bolt |
| 7.2 | Wireless / USB | Source prefix shown |
| 7.3 | Unplug | The charging card disappears |
| 7.4 | Drain to 20 % (or `adb shell dumpsys battery set level 20; adb shell dumpsys battery unplug`) | One "Low battery" alert per threshold. `adb shell dumpsys battery reset` afterwards |
| 7.5 | Check the wattage against a USB meter | Within reason. Note devices that report wrong units |

## 8. Connectivity & net speed
| # | Step | Expected |
|---|---|---|
| 8.1 | Wi-Fi → mobile data → airplane mode | "Mobile data", "Offline" alerts (only when the type changes) |
| 8.2 | Connect a VPN | "VPN active" |
| 8.3 | Connect Bluetooth earbuds | "Bluetooth connected · <name>" (name needs Nearby devices) |
| 8.4 | Enable Net speed, start a download | Live ↓/↑ speed. It disappears ~3 s after traffic stops |
| 8.5 | Screen off for 10 min (Battery stats) | No wakeups from the net-speed poller |

## 9. Timer & stopwatch
| # | Step | Expected |
|---|---|---|
| 9.1 | Start 1 min from the app | The island counts down. Expanded view shows "Ends at …" |
| 9.2 | Pause / +1:00 / cancel from the island | Works. The app card reflects it |
| 9.3 | Let it finish (screen off too) | "Time's up" + alarm-sound notification + vibration |
| 9.4 | Force-stop the app mid-timer, reopen | Timer continues with the correct remaining time |
| 9.5 | Stopwatch start / lap / pause / reset | Correct times and lap list |

## 10. Clipboard (Android 12 limits)
| # | Step | Expected |
|---|---|---|
| 10.1 | Copy text in any app, double-tap the island | Preview appears. Android 12 shows its "pasted from clipboard" toast (expected) |
| 10.2 | Keyboard open while double-tapping | The keyboard may close for a moment, then focus returns (documented limitation) |
| 10.3 | Copy a password from a password manager (Android 13+) | Masked •••• |
| 10.4 | Share text → "Pin to Island" | Pinned card with **Copy** button. Copy puts it back on the clipboard |
| 10.5 | If 10.1 shows "Clipboard access was refused" | Record the device/ROM: some ROMs don't treat overlay focus as foreground |

## 11. Settings, presets, export/import
| # | Step | Expected |
|---|---|---|
| 11.1 | Change each of the 24 options | Applied live. Survives an app restart |
| 11.2 | Export → re-install the app → Import | All values restored |
| 11.3 | Import a random JSON / image | Clear error toast. Settings unchanged |
| 11.4 | Light/dark mode + change wallpaper | Material You colours follow (Android 12+) |

## 12. Background survival (critical on Xiaomi)
| # | Step | Expected |
|---|---|---|
| 12.1 | Reboot | The island comes back without opening the app |
| 12.2 | Update the APK (`adb install -r`) | It comes back (MY_PACKAGE_REPLACED) |
| 12.3 | Swipe the app away in Recents | Service keeps running (FGS). On MIUI, lock it in Recents |
| 12.4 | Leave the phone idle overnight | Still running in the morning. If not, re-check Xiaomi guide steps 1–3 |
| 12.5 | Xiaomi guide buttons | Each opens the right MIUI/HyperOS screen. When a screen doesn't exist, a toast appears and App info opens |

## 13. Report template
```
Device / ROM / Android:
Cutout type:
Section.step failed:
What happened:
logcat excerpt:
Screenshot/screen recording:
```
