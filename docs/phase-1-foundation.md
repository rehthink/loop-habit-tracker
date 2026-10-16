# Phase 1: Foundation

> Written at the end of Phase 1. The "known limits" below were resolved in Phases 2–10.

## What was built

- **Project setup.** 16 Gradle modules with convention plugins in `build-logic`. One version
  catalog in `gradle/libs.versions.toml`. Hilt everywhere; `@Dispatcher(IO/DEFAULT)` and
  `@ApplicationScope` are injected, never hard-coded.
- **Room (`:core:database`).** Every table from the spec except the bundled Food table, at
  schema v1 with the schema exported to `core/database/schemas/`. Instants are epoch millis plus
  `zoneId`. Days are ISO `LocalDate` text. Missing values are `null`. Food will be a separate,
  read-only prepopulated database in Phase 5, so updating the food list never touches user data
  or backups.
- **DataStore (`:core:datastore`).** Theme, onboarding flag, app lock, biometric, feature flags
  (switched-off trackers), day boundary (default 4 am), quiet hours and the daily alert cap.
  Secrets (PIN hash, Gemini key) are in EncryptedSharedPreferences, excluded from backup.
- **Local profile.** A UUID is created on first launch (`ProfileRepository.ensureProfile`).
- **Design system (`:core:designsystem`).** Tokens come from the Loop logo canvas: Ink #111418,
  Paper #F6F7F5, Move #2F9E6A, Sleep #5B5BD6, Screen #E8743B, Fuel #D9A21B, and Sora
  (bundled, OFL). Components: Loop mark, wordmark, four-area mark, rings, cards, buttons, rows,
  status pills, empty states, and an in-house icon set. Light and dark themes. No dynamic
  colour, so area colours stay fixed.
- **Navigation.** Single activity, Navigation Compose with type-safe routes. Three tabs (Today,
  Progress, Me) and a + quick-log button. The quick-log sheet lists Food photo, Water, Mood and
  Health issue; each shows a friendly "coming soon" until its phase.
- **Onboarding.** Welcome, Private by design (including the Gemini free-tier notice), What Loop
  can notice (every permission explained; tap a row for the full reason), optional name, done.
- **Permissions screen** (Me › Permissions). Live status of all 9 permissions, refreshed every
  time the screen resumes. Tapping one opens a one-screen explanation with a single
  "Turn on" action that starts the right system flow (runtime dialog, special-access settings
  page, or app settings when permanently denied).
- **App lock.** 4–6 digit PIN (PBKDF2-SHA256, salted) with optional fingerprint/face unlock via
  BiometricPrompt. Locks on cold start and after 30 s in the background. There's a 30 s
  cool-down after 5 wrong PINs. App content is hidden from accessibility while locked and
  from the recent-apps preview (Android 13+).
- **Weekly cleanup job.** WorkManager deletes raw events older than 60 days (idle + battery
  not low).
- **Backup.** Auto Backup includes only `loop.db` and the settings file.

## How to test

1. `./gradlew installDebug`, open Loop: onboarding appears. Step through; the permissions step
   lists all 9 with reasons. Add or skip a name, then **Open Loop**.
2. **Today** shows four empty rings ("No data yet"), habit score "–", insight and challenge empty
   states. Greeting uses your name.
3. **Me › Permissions**: e.g. tap *Screen time* › *Turn on* › enable Loop in Usage access ›
   press back. The pill flips to **On** without restarting. Turn it off in system settings and
   come back: it flips to **Off**. Same for Notifications, Location (then "all the time"),
   Camera, Physical activity, Notification access, Run in the background.
4. **Me › Settings › App lock**: choose a PIN, confirm it. Leave the app for over 30 s, return:
   the lock screen shows. Wrong PIN shakes; 5 wrong tries start a 30 s cool-down. Turn on
   fingerprint unlock (if the phone has one enrolled) and relaunch.
5. **Me › Settings**: switch Auto/Light/Dark; switch trackers off and on.
6. **Me › Food photo key**: paste any text, Save: "Saved, ends in …". Remove clears it.
7. Unit tests: `./gradlew testDebugUnitTest`. Room/migration tests:
   `./gradlew :core:database:connectedDebugAndroidTest`.

## Known limits

- No tracking yet: the rings, score, insight and challenge are empty states until Phases 2–8.
- Health Connect shows "Coming soon"; its SDK is added in Phase 3.
- The notification listener is registered (so access can be granted) but collects nothing yet.
- "Run in the background" opens Android's battery-optimisation list; the per-brand guide comes in Phase 10.
- `security-crypto` 1.1.0 marks EncryptedSharedPreferences deprecated. It's still the option the
  spec names and works on all supported versions. A Keystore-only replacement would touch only
  `SecureStore`.
- The PIN and API key are not backed up (by design); after a restore the user sets them again.
