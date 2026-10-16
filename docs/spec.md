# Loop: build spec (original, from the product owner)

This is the source of truth. Copied verbatim in substance from the owner's build prompt
(also published as the claude.ai artifact "Habit Tracker – Build Prompt").

## 1. Role and goal
You are a senior Android engineer. Build a native Android app called Loop, a habit tracker, that
automatically tracks a person's daily habits, links them to mood and context, and shows
plain-language insights.

Work in the phases listed in section 7, one phase at a time. At the end of each phase: the app
must build, run, and meet that phase's acceptance criteria. Explain what you built, list any
assumptions, then wait for confirmation before the next phase.

Ask before adding any library not listed in section 3, any new permission, or any network call
other than the Gemini API.

## 2. Product summary and constraints
Tracks: steps, activity, sitting time, workouts, sleep, screen time per app, unlocks,
notifications, focus time, music listening, food (photo), water, tea/coffee, mood and energy (3x a
day), office/home with arrival and leaving time, commute, illness and medicines, weekly weight,
one-line journal.

Hard constraints
- Android only. Min SDK 28, target the latest stable SDK.
- Zero running cost: no backend server, no paid APIs.
- All user data stored on-device only. The only network call is the Gemini API for food photos.
- Food recognition uses the Gemini API free tier. The user enters their own free API key from
  Google AI Studio in Settings. Never hardcode a key in the APK.
- Tell the user in onboarding that Google may use free-tier content (meal photos) to improve its products.
- No microphone. No continuous GPS. Battery-light by design.
- Backup only via Android Auto Backup (user's own Google Drive) and manual export. No login and no
  account: the app works fully on first open, with a local profile only.

## 3. Tech stack
| Area | Choice |
|---|---|
| Language | Kotlin, coroutines + Flow |
| UI | Jetpack Compose, Material 3, dark mode |
| Architecture | MVVM + clean layers (data / domain / ui), single-activity, Navigation Compose |
| Dependency injection | Hilt |
| Local database | Room |
| Settings | DataStore (Preferences) |
| Background work | WorkManager (periodic + one-time jobs) |
| Steps and activity | Step counter sensor, Activity Recognition Transition API (Google Play services) |
| Sleep | Sleep API (Google Play services) + screen-off gaps + morning confirm |
| Health data | Health Connect SDK (steps, exercise, sleep, heart rate from watches) |
| Screen time | UsageStatsManager / UsageEvents |
| Notifications + music | NotificationListenerService + MediaSessionManager |
| Gym location | Geofencing API (Google Play services) |
| Camera | CameraX |
| Barcode | ML Kit Barcode Scanning (on-device, free) |
| Food recognition | Gemini API free tier via REST (Retrofit + kotlinx.serialization), structured JSON output |
| Food database | Indian Food Composition Tables + Open Food Facts subset, bundled as a prepopulated Room asset |
| Charts | Vico |
| Home widgets | Jetpack Glance |
| App lock | BiometricPrompt + PIN fallback |
| Testing | JUnit, Turbine, Room in-memory tests, Compose UI tests |

Use the current Gemini Flash or Flash-Lite model available on the free tier; keep the model name
in one config constant so it can be switched.

## 4. Architecture
Six modules, each with one job. Data flows: collectors → raw events → nightly aggregation → daily
summaries → insights → UI and notifications.
1. Collectors: one class per source (steps, activity, sleep, usage stats, notifications/music,
   geofence, check-ins, food). Each writes timestamped raw events to Room. Scheduled by
   WorkManager or triggered by system callbacks.
2. Data layer: Room DAOs + repositories. The only place that reads or writes the database.
3. Aggregation engine: nightly WorkManager job rolls raw events into one DailySummary per day.
   Applies the day-boundary rule (a day ends at the user's detected wake time, default 4 am).
4. Insights engine: pure Kotlin, no Android dependencies, fully unit-tested. Computes baselines,
   trends, correlations, anomalies, habit score and insight messages from DailySummary rows.
5. Notification engine: schedules Aware / Alert / Relax notifications, enforces daily cap, quiet
   hours and smart reduce.
6. UI: Compose screens in three tabs (Today, Progress, Me) plus a quick-log button; see section 9.

Raw events older than 60 days are deleted by a weekly cleanup job. Daily summaries are kept forever.

Built to scale and change
- Multi-module Gradle: :core:model, :core:database, :core:designsystem, :engine:insights,
  :engine:gamification, one :collector:* per data source, one :feature:* per screen area.
- Every data source implements one TrackerSource interface (collect(), summarize(day),
  requiredPermissions). Sources register through Hilt multibinding, so adding a new tracker
  touches no existing code.
- FoodRecognizer interface with a Gemini implementation. Swapping models later is a one-class change.
- Insight rules, badges and challenges are defined as data (Kotlin config lists), not hard-coded in screens.
- Feature flags in DataStore so any tracker or screen can be switched off.
- Room schema versioned with migration tests; export file is versioned JSON.
- A local profile UUID exists from day one, so optional login or sync can be added later behind
  the repository layer without UI changes.

## 5. Data model
Store all timestamps as epoch millis plus the zone ID. Use null for missing data, never 0.

| Table | Key fields |
|---|---|
| RawEvent | id, type (STEP, ACTIVITY, APP_OPEN, APP_CLOSE, UNLOCK, NOTIFICATION, MUSIC_PLAY, MUSIC_PAUSE, SCREEN_OFF, GEOFENCE_ENTER/EXIT), packageName?, value?, startTime, endTime? |
| SleepSession | id, start, end, source (SLEEP_API, USAGE_GAP, WATCH, USER), confirmed |
| Workout | id, type, start, end, source (AUTO, GEOFENCE, WATCH, USER), confirmed |
| Meal | id, time, mealType, photoUri?, totalKcal |
| MealItem | id, mealId, foodId, name, quantity, unit (PIECE, KATORI, PLATE, GRAM, CUP, ML), kcal, protein, carbs, fat |
| Food (bundled) | id, name, aliases, defaultUnit, kcalPerUnit, gramsPerUnit, macros |
| MoodCheckIn | id, time, slot (MORNING, AFTERNOON, NIGHT), mood 1–5, energy 1–5, note? |
| LocationCheckIn | id, date, place (OFFICE, HOME, OTHER), arrival?, leaving? |
| WaterLog | id, time, glasses |
| HealthIssue | id, name, start, end?, severity 1–5, notes? |
| Medicine | id, issueId?, name, dose, times[], active |
| WeightLog | id, date, kg |
| Goal | metric, target, direction |
| NotificationStat | notificationKey, sentCount, ignoredStreak, lastSent, enabled |
| DailySummary | date, steps, sittingMin, sleepMin, bedtime, wakeTime, screenMin, unlocks, focusMin, musicMin, perAppJson, kcalIn, kcalOut, waterGlasses, caffeineCount, avgMood, avgEnergy, place, commuteMin, isSick, habitScore, dataCompleteness |

## 6. Feature implementation details
**Food photo with Gemini free tier**
1. Widget or meal notification opens a CameraX screen.
2. Compress the photo (longest side ~1024 px, JPEG) to stay light on tokens.
3. Send to Gemini with a prompt asking for strict JSON: list of items, each with name, quantity,
   unit (PIECE, KATORI, PLATE, GRAM, CUP, ML), confidence. Tell it the food is likely Indian home
   or restaurant food.
4. Match each name to the bundled Food table (fuzzy match on name and aliases). Calories come from
   the table, not from Gemini. Use Gemini's calorie guess only when there is no match.
5. Show the review card pre-filled. Every field editable: dish, quantity (+ / −), unit, add item,
   delete item. Calories update live. Tap Log to save.
6. Remember the user's corrected portions per dish and pre-fill those next time.
7. On no internet, no API key, or a rate-limit error (HTTP 429): open the same card empty with
   search and recent meals. Queue the photo and offer "analyse later".

**Sleep:** combine Sleep API segments, longest screen-off gap at night and phone stillness. Pick
the best estimate, then ask for confirmation in the morning notification.

**Workouts:** activity transitions for walk/run/cycle above 10 minutes. Geofence enter/exit for
the saved gym. Health Connect exercise sessions. Ask one-tap confirm for geofence and
routine-based guesses.

**Screen time:** read UsageEvents every 15 minutes and on unlock, save to RawEvent. Focus time =
25+ minutes in work hours with no unlock.

**Music:** in NotificationListenerService, watch active media sessions; log play/pause per package.

**Calories out:** Mifflin-St Jeor resting burn + step-based and workout-based activity burn.

**Notifications:** separate channels per category. Smart reduce: after 3 ignored in a row, halve
frequency; after 6, ask once whether to turn it off. Global daily alert cap and quiet hours from settings.

**Insights:** baseline = rolling 28-day mean; trend = 7-day rolling mean; correlation = Pearson on
daily pairs, shown only when 14+ days and |r| ≥ 0.4; anomaly = more than 2 standard deviations
from baseline. Turn findings into short sentences with numbers.

## 7. Build phases
| Phase | Scope | Done when |
|---|---|---|
| 1. Foundation | Project setup, Hilt, Room, DataStore, navigation, theme, onboarding, permissions screen, app lock | App opens, onboarding explains each permission, permissions screen shows live status |
| 2. Screen time | Usage access flow, UsageEvents collector, per-app list, hourly timeline, unlocks, focus time | Today screen shows correct per-app minutes matching Android's Digital Wellbeing within ~5% |
| 3. Movement | Step sensor, Health Connect read, activity transitions, sitting time, workouts + gym geofence | Steps and detected walks appear without opening the app; gym visit logs a workout |
| 4. Sleep | Sleep API, screen-off gaps, stillness, morning confirm | Last night's sleep shown each morning; user correction saved |
| 5. Food and water | CameraX, Gemini call, Food table, review card, barcode, water, widgets | Photo → editable pre-filled card in under 5 s on Wi-Fi; offline fallback works |
| 6. Check-ins | Mood/energy 3x a day, office/home + times, journal, weight, health issues, medicines | All loggable from notification actions without opening the app |
| 7. Music | Media session tracking | Listening minutes per app shown per day |
| 8. Aggregation + insights | Nightly summary job, baselines, trends, correlations, anomalies, habit score, goals, reviews | Insights screen shows correct numbers on 30 days of seeded test data |
| 9. Notifications | All Aware/Alert/Relax notifications, control panel, cap, quiet hours, smart reduce | Every notification can be toggled and re-timed; ignored ones reduce |
| 10. Polish | Battery optimisation guide per brand, export/delete, backup, empty and missing-data states | Export file re-imports cleanly; delete wipes everything |

## 8. Engineering rules
- Request each permission only when its feature is first used, with a one-screen explanation.
- Every collector must survive app kill and reboot (re-register on BOOT_COMPLETED).
- Never block the main thread; all I/O in coroutines on Dispatchers.IO.
- Store the Gemini API key in EncryptedSharedPreferences; never log it or the photos.
- Show "no data" for gaps; track dataCompleteness per day and exclude incomplete days from baselines.
- Insights engine and aggregation must have unit tests with seeded data before UI work on them.
- Keep a battery budget: no wake-locks, no polling faster than 15 minutes, batch writes.
- Prepare Play Store declarations for usage access, notification listener, Health Connect and
  background location (geofence only).
- Write a short README per phase: what was built, how to test it, known limits.

## 9. Design and gamification
Minimal, calm and quick to read: anyone should understand the Today screen in 3 seconds.

Navigation
- Bottom bar with three tabs: Today, Progress, Me.
- One floating + button for quick log: food photo, water, mood, health issue.
- Today: four daily rings (Move, Sleep, Screen, Fuel), habit score, one insight card, today's challenge.
- Progress: trends, insights, streaks, badges, weekly report card.
- Me: goals, health log, weight, settings, permissions, privacy, API key.

Design principles
- One main action per screen; at most 3 key numbers visible before scrolling.
- Details open on tap (progressive disclosure), never all at once.
- One fixed colour per area across the app (Move green, Sleep indigo, Screen orange, Fuel amber).
- Plain words, no jargon: "You slept 6 h 40 m", not "Sleep duration: 400 min".
- Large touch targets, light and dark mode, friendly empty states.
- Design tokens (colours, type, spacing) live in :core:designsystem only.

Gamification
- XP and levels: points for check-ins, logging meals, and hitting goals.
- Streaks: per habit, with one free "streak freeze" per week so a missed day doesn't reset everything.
- Badges: e.g. first 10k-step day, 7-day sleep streak, 5 days under screen-time goal, 30 meals logged.
- Weekly challenge: auto-picked from the user's weakest area, e.g. "Phone down by 11:30 pm, 4 nights".
- Personal bests and a Sunday report card with a grade per area.
- Small celebration animation when a ring closes or a badge unlocks.
- Compete only with your past self; no leaderboards (no login).
- Keep it kind: no guilt messages, at most one streak reminder a day.

Build the design system and three-tab navigation in Phase 1, and the gamification engine in
Phase 8 alongside insights.

**Logo:** the owner's Claude Design canvas "Loop Logo"
(https://claude.ai/artifact/3Nu32jqkWmSDKoBLJqVJAC). It has a ring that's almost closed with a dot
in the gap, accent #2F9E6A, Ink #111418, Paper #F6F7F5, Sora font. Area colours: Move #2F9E6A,
Sleep #5B5BD6, Screen #E8743B, Fuel #D9A21B. These are already applied in `:core:designsystem`
and the launcher icon.
