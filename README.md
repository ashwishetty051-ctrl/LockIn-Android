# LockIn — Native Android app

LockIn is an offline-first Kotlin and Jetpack Compose study-focus app. Preferences use Android DataStore, and focus history uses a local Room database. There is no account, backend, analytics, ad SDK, or data upload.

## Open and install

1. Download and extract `LockIn-Android-Studio.zip`.
2. In Android Studio on Windows, choose **Open**, select the extracted `LockIn` folder (the folder containing `settings.gradle.kts`), and allow Gradle sync to finish.
3. Install a JDK 17 or newer and Android SDK Platform 35 if Android Studio does not offer to install them. This project supports Android 8.0 (API 26) and newer.
4. Connect an Android phone with USB debugging enabled, approve the computer on the phone, choose the phone in Android Studio, and press **Run**.
5. Open **Permissions & settings** in LockIn and grant only the access you want to use. Usage Access and overlay access are special settings screens, not runtime pop-ups.

You can also build from a terminal in the project root:

```sh
./gradlew assembleDebug
```

On Windows, use:

```bat
gradlew.bat assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. To install it with Android Debug Bridge, run `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

## Features

- Installed launcher apps are discovered from the device; choose which apps focus or bedtime should cover.
- Focus durations: 30, 45, 60, 90, or 120 minutes. A countdown and end time remain visible, with a persistent foreground-service notification.
- A Usage Access monitor checks foreground-app events about every two seconds. If a chosen app is in use and overlay access is enabled, a LockIn motivation screen appears with a return-to-study action.
- Emergency focus exit shows a 10-second countdown and records the session as interrupted.
- Daily wake-up alarm uses `AlarmManager.setAlarmClock()`, reschedules after reboot, and asks three randomly generated arithmetic questions before dismissal. There is no snooze action. A 10-second emergency stop is provided as a safety fallback.
- Daily bedtime schedule supports periods that cross midnight. An active focus session takes priority; bedtime blocking starts after focus ends.
- Home and Progress use only locally recorded sessions. A streak day is earned by at least one completed session. The current streak is reset when the newest recorded session is interrupted; best streak counts consecutive days with completed sessions.
- Study Room shows the requested Aman and Riya demo profiles, clearly marked as static examples rather than real people or live activity.

## Permissions and what they do

- **Usage Access** lets LockIn read app-usage events to estimate which app is in the foreground. Without it, focus and bedtime cannot identify selected apps.
- **Display over other apps** lets LockIn show its blocking screen above another app. Without it, the monitor can still detect usage but cannot display the overlay.
- **Notifications** are needed for persistent focus status and alarm notifications on current Android versions.
- **Exact alarms** are requested for reliable clock-time scheduling on Android 12 and newer.
- **Full-screen alarm access** may be restricted by Android 14+ or user settings. LockIn reports the permission state and uses a high-priority alarm notification as a fallback.

LockIn does not grant permissions itself. You can revoke them at any time in Android settings.

## Known Android limitations

- This is best-effort distraction interruption, not device enforcement. Usage events can arrive late, overlays may be restricted, and users can leave or disable the service. It is not tamper-proof and does not block apps at the operating-system level.
- Android background-start, full-screen-intent, notification, exact-alarm, battery-optimization, and manufacturer-specific rules can delay or prevent behavior. An alarm is not guaranteed to appear full-screen or ring under every device condition.
- Bedtime protection keeps a foreground monitoring service active while enabled so it can notice the schedule becoming active. This can use battery; disable the schedule when it is not needed.
- The foreground monitor uses Android's `specialUse` service type because there is no dedicated type for user-started app-usage monitoring. Store distribution may require additional policy review or justification.
- The app lists installed launcher apps using broad package visibility. Google Play distribution may require review or an eligibility declaration for `QUERY_ALL_PACKAGES`; remove or replace that approach if the app's distribution channel does not permit it.
- The default phone dialer, Android settings, System UI, and permission/package installer screens are exempted from app blocking. The app does not use Accessibility Service, root, or device-admin privileges.
- If Android kills the monitor or the user revokes Usage Access, blocking pauses. Reopen LockIn, restore the permission, and restart the session/schedule if needed.
- The alarm service uses the device's default alarm ringtone and volume. Silent mode, Do Not Disturb, power state, and OEM rules can affect audible playback.

## Local data and implementation

- `SettingsRepository.kt`: DataStore settings and schedules.
- `SessionData.kt`: Room session history and progress calculations.
- `FocusMonitorService.kt`: foreground service, UsageStats polling, and optional overlay.
- `AlarmComponents.kt`: alarm scheduling, notification, sound service, and boot rescheduling.
- `MainActivity.kt`: Compose navigation and Home, Focus, Alarm, Bedtime, Progress, Study Room, and permission screens.
- `AlarmActivity.kt`: lock-screen-capable maths challenge and emergency stop.

Settings and session history stay on the device. The database is excluded from Android backup. The app does not make network requests.
