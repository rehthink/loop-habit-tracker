# Handoff: where Loop stands (as of 2026-10-16)

Read [spec.md](spec.md) first (the owner's full build prompt), then this file. Each phase has its
own note: [phase-1](phase-1-foundation.md) … [phase-10](phase-10-polish.md).

## Status

**All 10 phases are built.** The owner asked to build everything in one go and then review, rather
than confirming each phase. A review pass has been done (see "Review fixes" below).

Verified on 2026-10-16:
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
5. The app isn't a git repository yet; nothing is committed.

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
- Background work runs on one 15-minute `TickWorker`: `RefreshUseCase` (collect from every enabled
  tracker with permissions, then aggregate today and yesterday), then `NotificationEngine.tick()`,
  the widget refresh and the "analyse later" retry. `NightlyWorker` (about 4:30) detects the day
  boundary and finalises the last 3 days. `RawEventCleanupWorker` runs weekly.
- `BootReceiver` and finishing onboarding both trigger an immediate tick.
- Deep links: notifications and the widget open the launch intent with `loop.route` (see
  `core/model/.../SystemComponents.kt` `Routes`). `LoopApp.openRoute` maps them to destinations.

## Modules
| Module | Contents |
|---|---|
| `build-logic` | Convention plugins: application, library, library.compose, feature, room, hilt, jvm.library, collector |
| `:core:model` | Domain types: enums, DaySummary, DailySummaryPatch, DayClock (day boundary), Records, NotificationType catalogue, LoopPermission, Routes |
| `:core:domain` | Repository interfaces, TrackerSource, readers (ScreenTime, Sleep, Music, HeartRate), use cases (Aggregate, AggregateToday, Refresh, DetectDayBoundary, LogDrink, CompleteOnboarding), FoodMatcher, PIN rules |
| `:core:database` | LoopDatabase v2 (22 tables) + Migration 1→2, FoodDatabase (prepopulated asset), DAOs, schemas |
| `:core:datastore` | Settings (theme, lock, trackers, quiet hours, cap, work hours, day boundary, notification times, seen badges, collector cursors) and SecureStore |
| `:core:data` | All repository implementations, permission checker, PinHasher, BackupRepository (export/import/delete) |
| `:core:network` | Gemini recognizer (the only network code) |
| `:core:notifications` | NotificationPolicy (pure), NotificationEngine, NotificationActionReceiver |
| `:core:designsystem`, `:core:ui` | Tokens, components (rings, charts, stepper, celebration…), icons; permission copy, request flow (incl. Health Connect) and gate cards |
| `:engine:insights`, `:engine:gamification` | Pure Kotlin engines |
| `:collector:*` | screentime, notifications (count + music), steps, activity, healthconnect, geofence, sleep, intake (food + drinks), checkins |
| `:feature:*` | onboarding, today, progress, me (goals, about you, settings, key, privacy, data, battery), permissions, applock, screentime, movement, sleep, food, checkins, notifications, widgets |

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

## Ideas not built (all optional)
- An Open Food Facts India subset bundled by default (needs a licence check and an export).
- A Compose UI test suite per feature; only the JVM and Room/backup instrumented tests exist.
- Exact-time reminders would need an exact-alarm permission; deliberately not used.
