# Play Store declarations (draft)

Text to paste into the Play Console when publishing. Keep it in sync with the manifest.

## Usage access (`PACKAGE_USAGE_STATS`)
**Core feature:** Screen-time tracking. Loop shows how long the user spends in each app, how
often they unlock the phone, and their focus time. It also helps estimate sleep from overnight
screen-off gaps.
**Data:** App package names and foreground durations, stored only on the device. Never
transmitted.

## Notification listener (`BIND_NOTIFICATION_LISTENER_SERVICE`)
**Core feature:** Counts notifications per app (an input to screen-time insights) and reads
active media sessions to measure music-listening time.
**Data:** Notification counts per package and play/pause events per music app. Notification
text is not stored. Nothing is transmitted.

## Health Connect
**Read-only:** steps, exercise sessions, sleep sessions, heart rate, plus "read in background"
so the 15-minute tick can update the day while Loop is closed. Used to show the user's own
activity and sleep next to phone data. No writes, nothing transmitted. The rationale screen is
`HealthPrivacyActivity` (ACTION_SHOW_PERMISSIONS_RATIONALE + VIEW_PERMISSION_USAGE alias).

## Background location (`ACCESS_BACKGROUND_LOCATION`), geofence only
**Core feature:** Logs arrival and leaving times at places the user saves (gym, office, home)
to log gym workouts, office hours and commute time without opening the app.
**Implementation:** Google Play services Geofencing API only. No continuous GPS, no route
tracking. Prominent disclosure: the one-screen explanation shown before the request
("Location, all the time").
**Video:** record the Me › Permissions › Location, all the time flow.

## Activity recognition (`ACTIVITY_RECOGNITION`)
Step counting and detecting walks, runs and rides (Activity Recognition Transition API).

## Camera (`CAMERA`)
Meal photos and packet barcodes (ML Kit, on-device), only when the user opens the food camera.

## Internet (`INTERNET`)
Only for Gemini food recognition with the user's own key (declared in `:core:network`).

## Boot completed (`RECEIVE_BOOT_COMPLETED`)
Re-registers activity transitions, sleep updates and geofences after a restart.

## Data safety form

- Data collected: none sent to the developer. All data is stored on the device.
- Data shared: meal photos are sent to Google Gemini **only** if the user adds their own API
  key. Disclose as "Photos, shared with a third party for app functionality (food recognition)".
  Note that Google's free tier may use content to improve its products (disclosed in
  onboarding and on the key screen).
- Encryption in transit: HTTPS (Gemini).
- Deletion: the user can delete all data in-app (Phase 10) or by uninstalling.
