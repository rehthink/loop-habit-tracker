# Handoff: where Loop stands (as of 2026-10-16)

Read [spec.md](spec.md) first (the owner's full build prompt), then this file. Each phase has its
own note: [phase-1](phase-1-foundation.md) … [phase-10](phase-10-polish.md).

## Status

**All 10 phases are built.** The owner asked to build everything in one go and then review, rather
than confirming each phase. Two review passes have been done (see "Review fixes" and "Second
review" below).

Verified after the second review (2026-10-16, branch `review-fixes`):
- `assembleDebug`, `assembleRelease` (R8) and `:app:lintDebug` pass. The release build was signed
  with the debug key and smoke-tested on the emulator: lock, Today, check-in, food review and the
  barcode scanner (which needed ML Kit keep rules in `app/proguard-rules.pro`).
- 121 JVM unit tests pass, including new ones for the PIN lockout, refresh modes, registration gate,
  device clock, collector-state move, gym visits and the restore lockout.
- 11 instrumented tests pass on the API 35 emulator, including migration 3→4 with data.
- On the emulator: onboarding to Today; PIN setup; turning the lock off asks for the PIN; five
  wrong PINs start a cool-down that survives a force-stop; a spoofed `loop.route` from outside is
  ignored and the deep-link alias refuses outside callers; a real mood notification (private on the
  lock screen) opens Check in after unlocking.

Verified at the end of the build (before the second review):
- `assembleDebug` passes; lint was run on all modules.
- 100+ JVM unit tests pass across engines, collectors, the notification policy, Gemini parsing
  (MockWebServer), the food matcher, the draft builder, DataStore, PIN hashing, onboarding and
  app lock.
- 10 instrumented tests pass on an API 35 emulator: Room DAOs, migration v1→v2, schema
  validation, and backup export/import/delete.
- Walked through on the emulator: onboarding, Today rings, Screen time, Movement, Sleep, Food
  (camera → review → search → log), Check-in, Health log, Progress, Notifications settings and Me.
- A real mood check-in notification was answered in the shade (mood, then energy) and saved
  without opening the app.

### Open items for the owner
1. All spec-implied permissions are declared (usage access, notification listener, notifications,
   activity recognition, location including background for geofences only, camera, biometric,
   boot-completed, Health Connect reads plus background read, and INTERNET for Gemini only).
2. applicationId is `app.loop.habits`.
3. The food table is a hand-built IFCT-style list of 183 dishes, not an exact IFCT import.
   `tools/build_food_db.py` can merge an Open Food Facts export for packaged foods with barcodes.
4. The Gemini model is `gemini-3.8-flash` (free tier), set in `core/network/.../GeminiConfig.kt`.
5. The code is on GitHub (`rehthink/loop-habit-tracker`, private), all on `main`, including the
   second review's changes (see "Second review" below) and the signed APK in `dist/`.
6. Release signing is set up. The keystore and its passwords live outside the repo. See
   [release.md](release.md) for where they are, how to build, and how to back them up.

## Environment notes
- Run Gradle with JDK 21: `export JAVA_HOME=$(/usr/libexec/java_home -v 21)`. The shell's default
  `java` is 17.
- Versions: AGP 9.4.1 (built-in Kotlin, so no `kotlin.android` plugin), Kotlin 2.4.21, KSP 2.3.12,
  Gradle 9.8.1, Hilt 2.60.1, Compose BOM 2026.09.00, Room 2.8.5, Navigation 2.10.2, Vico 3.3.1,
  CameraX 1.6.2, Glance 1.2.0, Retrofit 3.0.0, Health Connect 1.1.0, compile/target SDK 37,
  min SDK 28.
- The network is slow: cold dependency downloads can take 20+ minutes.
- Devices: the owner's Pixel 10a (Android 17, serial `65281XEA35F49F`) and the AVD
  `Medium_Phone_API_35`. That one boots with `emulator -avd Medium_Phone_API_35 -no-window -gpu
  swiftshader_indirect`; it's slow, so wait about 10 s after launch.
- Force WorkManager jobs on Android 14+:
  `adb shell cmd jobscheduler run -f -n androidx.work.systemjobscheduler app.loop.habits <id>`.
  Periodic work is skipped if forced before it's due.
- Commands: `./gradlew assembleDebug installDebug testDebugUnitTest :core:model:test :core:domain:test :engine:insights:test :engine:gamification:test`
  and `./gradlew :core:database:connectedDebugAndroidTest :core:data:connectedDebugAndroidTest`.
- Rebuild the food DB with `python3 tools/build_food_db.py` (it reads Room's exported schema).

## Architecture (as built)
```
collectors (one per source)  ->  raw_event / sessions (Room)  ->  AggregateDayUseCase  ->  daily_summary
   screentime, steps, activity,       via domain repositories        (every 15-min tick +       |
   healthconnect, geofence, sleep,                                    nightly finalise)          v
   notifications(+music), intake,                                                    engine:insights / gamification
   checkins                                                                                     |
                                                                                    Today / Progress / notifications
```
- Background work runs on one 15-minute `TickWorker`: `RefreshUseCase` in FULL mode (collect from
  every enabled tracker with permissions, then aggregate today and yesterday), then
  `NotificationEngine.tick()`, the widget refresh and the "analyse later" retry.
- Unlocks and opening a screen run `RefreshUseCase` in QUICK mode: only `quick` sources (screen
  time, steps) and today, at most once every 2 minutes. Nothing heavier runs faster than the tick.
- Geofences, activity transitions and sleep updates register through `RegistrationGate`: again
  only after a reboot or app update (`DeviceEpoch`), a change of request, a lost registration, or
  once a day. Re-adding geofences on every tick used to replay an "arrived" event. `NightlyWorker` (about 4:30) detects the day
  boundary and finalises the last 3 days. `RawEventCleanupWorker` runs weekly.
- `BootReceiver` and finishing onboarding both trigger an immediate tick.
- Deep links: notifications and the widget open the non-exported `app.loop.DeepLink` alias of
  MainActivity with `loop.route` (see `core/model/.../SystemComponents.kt` `Routes`). MainActivity
  ignores route extras that arrive any other way. `LoopApp.openRoute` maps them to destinations.
- Time: inject `Clock` (a `DeviceClock`, which follows time-zone changes even in singletons).

## Modules
| Module | Contents |
|---|---|
| `build-logic` | Convention plugins: application, library, library.compose, feature, room, hilt, jvm.library, collector |
| `:core:model` | Domain types: enums, DaySummary, DailySummaryPatch, DayClock (day boundary), Records, NotificationType catalogue, LoopPermission, Routes |
| `:core:domain` | Repository interfaces, TrackerSource, readers (ScreenTime, Sleep, Music, HeartRate), use cases (Aggregate, AggregateToday, Refresh, DetectDayBoundary, LogDrink, CompleteOnboarding), FoodMatcher, PIN rules |
| `:core:database` | LoopDatabase v4 (23 tables) + Migrations 1→2→3→4, FoodDatabase (prepopulated asset), DAOs, schemas |
| `:core:datastore` | Settings (theme, lock, trackers, quiet hours, cap, work hours, day boundary, notification times, seen badges), collector state (its own file, not backed up) and SecureStore |
| `:core:data` | All repository implementations, permission checker, PinHasher, BackupRepository (export/import/delete) |
| `:core:network` | Gemini recognizer (the only network code) |
| `:core:notifications` | NotificationPolicy (pure), NotificationEngine (decides), TimedNotes (content), NotificationPoster (builds, posts, counts), NotificationActionReceiver |
| `:core:designsystem`, `:core:ui` | Tokens, components (rings, charts, stepper, celebration…), icons; permission copy, request flow (incl. Health Connect) and gate cards |
| `:engine:insights`, `:engine:gamification` | Pure Kotlin engines |
| `:collector:*` | screentime, notifications (count + music), steps, activity, healthconnect, geofence, sleep, intake (food + drinks), checkins |
| `:feature:*` | onboarding, today, progress, me (goals, about you, settings, key, privacy, data, battery), permissions, applock, screentime, movement, sleep, food, checkins, notifications, widgets, cycle |

## Conventions
- Features depend only on `core:model`, `core:domain`, `core:designsystem`, `core:ui` (and the pure
  engines). Collectors depend only on `core:model` and `core:domain`. Database access goes only
  through `core:data`.
- Timestamps are epoch millis plus `zoneId`. Days are `LocalDate` (ISO text). Missing data is
  `null`, never 0.
- New tracker: add a `:collector:x` module that implements `TrackerSource` and binds it with
  `@Binds @IntoSet`.
- Schema change: bump `LoopDatabase.VERSION`, add a migration (generate it from
  `schemas/<n>.json`), keep `MigrationTest` green.
- UI copy goes in `strings.xml`: escape `'` and `"`, and use `\n` for new lines.

## Review fixes made after the build
- Habit score needs 2+ areas; a quiet morning used to show "100" from screen time alone.
- "Stay under" goals (screen time) only count for XP, streaks and the report card once the day
  is over.
- Data completeness counts only the trackers the person uses, so insights now appear for people
  who don't log food or water.
- The day boundary is detected from wake times.
- Today rolls over at midnight while open.
- Screens load first and refresh after, so they never sit blank while a refresh runs or after
  one fails.
- Screen time totals are rounded from exact time (closer to Digital Wellbeing).
- Deep links to a tab clear the stack.
- A tick runs right after onboarding, so tracking starts at once.
- Health Connect background read was added (needed for ticks with the app closed).
- Calories out and heart rate are now shown (both were computed or requested but never
  displayed).
- Queued meal photos are retried once a key is added.
- Search results now show while typing. The camera title is readable. Chart labels are no longer
  clipped. Bundle-unsafe dialog state is no longer saved. Apostrophes and quotes in strings are
  escaped.

## Second review (2026-10-16, branch `review-fixes`)
Security and privacy
- A restore (Auto Backup or device transfer) with the app lock on no longer locks you out: the PIN
  can't travel, so the lock turns itself off and says so (`AppLockManager`).
- Wrong-PIN attempts are kept in SecureStore, so killing the app doesn't reset them; after 5 the
  wait doubles each time, up to an hour (`PinLockout`).
- Turning the lock off, exporting and deleting everything ask for the PIN first (`AppLockGate`).
- With the lock on, notifications hide their content on the lock screen and their buttons need the
  phone unlocked. Medicine reminders are always private. "Discard" (deletes a guessed workout) and
  "Turn off" always need an unlock. Recents previews are hidden on Android 9–12 too (FLAG_SECURE).
- SecureStore recovers from a lost Keystore key instead of crashing (secrets start empty).
- Meal photos are deleted with their meal, when a review closes unsaved, and after 30 days.
- The export screen says the file isn't encrypted. Export and import stream instead of building one
  big string.

Correctness and battery
- Gym visits work again (see the geofence note above), and `PlacesMath.gymVisit` ignores repeated
  arrivals.
- Unlock and screen-open refreshes are quick and throttled (see above).
- Collector bookkeeping moved out of the settings file (it grew without limit and woke every
  settings observer each tick); per-day keys became one key per thing.
- The notification tick runs one part at a time (one failure can't silence the rest) and never
  twice at once.
- `runSuspendCatching` replaces `runCatching` around suspend calls, so cancelled work stops.
- The app scope logs failures (type only in release) instead of crashing; the tick logs which
  collector failed.
- Nightly WAL checkpoint so Auto Backup (which copies loop.db alone) is current.
- Gemini: a 400 is a bad key only when Google says `API_KEY_INVALID`.
- Schema v4: raw events have a `tag` column (activity transition, place kind); `packageName` now only
  ever holds an app package. Older exports are translated on import.

UI polish
- Type scale uses three weights (no Bold), line heights in range, 14sp secondary text in rows.
- Cards have a hairline edge; card actions line up with card text (`CardAction`, `QuietButton(alignToText)`).
- Today shows neutral placeholders until data loads (no "No data yet" flash).
- Mood and energy pick from a one-row 5-step `ScaleSelector`; "Save check-in" always works and says
  what's missing.
- Streaks say "Not started" instead of a column of bold zeros; plural fixed.
- Button and notification labels start with a verb ("Log workout", "Mark as taken", "Set end time").
- Data screen errors are written for people, in the error colour.

## Ideas not built (all optional)
- Passphrase encryption for export files.
- An Open Food Facts India subset bundled by default (needs a licence check and an export).
- A Compose UI test suite per feature; only the JVM and Room/backup instrumented tests exist.
- Exact-time reminders would need an exact-alarm permission; deliberately not used.
