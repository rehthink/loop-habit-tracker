# Phase 9: Notifications

## What was built
- `:core:notifications`: three channels. **Aware** (water, sitting, screen-time limit, meal
  reminders) and **Relax** (insight of the day, streak note, Sunday report, wind-down) count
  against the daily cap. **Alert** (check-ins and confirmations) is exempt.
- 18 notification types defined as data (`NotificationType`), each with default times or "when
  needed".
- `NotificationPolicy` (pure, unit-tested) rules:
  - switched-off types and trackers never send
  - a timed note is due once per slot, within a 2-hour grace period
  - quiet hours silence Alerts and skip Aware and Relax notes
  - the global daily cap
  - smart reduce: after 3 ignored in a row, sent half as often; after 6, a one-time "Turn off
    these reminders?" with Turn off / Keep
- "Ignored" means swiped away (delete intent) or still unread when the next one goes out. Any tap
  or answer resets it.
- The engine runs on the 15-minute tick: no exact alarms, no wake locks. Contextual nudges are
  sitting for over an hour (at most every 2 hours, 9 am to 9 pm) and passing the screen-time goal
  (once a day). It also skips notes that would be pointless: mood already logged, water goal met,
  meal logged in the last 3 hours, no insight, no live streak.
- Control panel (Me › Notifications): quiet hours, daily limit, and every type with a switch and
  its times (re-time or reset). A "sent less often" note shows when smart reduce is active.

## How to test
Switch a type off: it stops. Re-time the morning check-in to a few minutes from now; it arrives
within one tick. Swipe away one note type three times: the next one is skipped once.

## Known limits
Times are honoured within the 15-minute tick (WorkManager), not to the minute. That's the
battery trade-off the spec asks for.
