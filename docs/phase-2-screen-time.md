# Phase 2: Screen time

## What was built
- `:collector:screentime`: `ScreenTimeSource` reads `UsageEvents` every 15 minutes (the shared tick),
  on unlock while Loop's process is alive, and whenever Today or Screen time opens. It reads from a
  saved cursor and writes APP_OPEN / APP_CLOSE / SCREEN_OFF / UNLOCK raw events in one batch. The
  first run backfills 3 days.
- `ScreenTimeCalculator` (pure, unit-tested) turns events into foreground sessions the way Digital
  Wellbeing does. A session runs until the app leaves, another app opens or the screen turns off.
  It gives per-app minutes rounded to the nearest minute, the total rounded from exact
  milliseconds, minutes per clock hour, unlocks and focus time. Focus time is gaps of 25+ minutes
  with no use and no unlock, inside work hours, Monday to Friday. The home screen and system UI
  are excluded.
- `LoopNotificationListener` counts notifications per app (package and time only, never the text).
  Writes are batched every 60 s or every 25 events.
- `:feature:screentime`: day picker, totals, hourly bar chart, apps list (top 5, expandable),
  notifications per app, listening time, and permission gates for usage and notification access.
- Today's Screen ring shows "2 h 10 m of 3 h".

## How to test
Turn on Usage access (Me › Permissions › Screen time). Use a few apps, open Loop › tap the Screen
ring, and compare with Settings › Digital Wellbeing for the same day.

## Known limits
- Screen time is counted **midnight to midnight** (like Digital Wellbeing), not by the 4 am day
  boundary used elsewhere, so the numbers line up.
- "On unlock" collection only happens while Loop's process is alive (Android doesn't deliver
  USER_PRESENT to manifest receivers). Otherwise the 15-minute tick catches up.
- Android keeps about 7 days of usage events, so history from before install isn't available.
