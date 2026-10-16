# Phase 3: Movement

## What was built
- `:collector:steps`: one step-counter reading per tick (the sensor answers immediately), stored as
  a STEP raw event. Steps are the differences between readings; a reboot resets the counter, which
  is handled. No wake locks.
- `:collector:activity`: the Activity Recognition Transition API (walk, run, ride, still, vehicle),
  registered on every tick so reboots and updates are covered. A walk, run or ride over 10 minutes
  becomes an AUTO workout, confirmed straight away. Sitting time is still time between 7 am and
  11 pm, minus recorded sleep.
- `:collector:healthconnect`: read-only Health Connect (steps, exercise sessions, sleep sessions,
  heart rate, plus background reading where the phone supports it). Sessions are copied in as WATCH
  workouts and sleep. Daily steps take the larger of phone and watch.
- `:collector:geofence`: the Geofencing API for saved Home / Office / Gym, with "use where I am
  now" via a single balanced-power location fix (no continuous GPS). Office and home arrival and
  leaving times go to LocationCheckIn. Commute is home→office plus office→home. A gym visit of
  20+ minutes is saved as an unconfirmed GEOFENCE workout, with a one-tap "Log it as a workout?"
  notification.
- `:feature:movement`: steps ring, sitting, commute, workouts this week (confirm, delete, add one
  by hand), heart-rate range, places setup, and today's office/home times.
- `BootReceiver` (BOOT_COMPLETED, MY_PACKAGE_REPLACED, TIMEZONE_CHANGED) runs a tick, and every
  collector re-registers.

## How to test
Grant Physical activity. Walk with the phone for a few minutes; steps appear on Today within one
tick without opening the app. Save the gym at your current location, grant "Allow all the time",
leave and come back after 20+ minutes. You get a confirmation notification and the workout
appears under Movement.

## Known limits
- The step counter is read every 15 minutes, so Today's steps can lag by up to one tick.
- Sitting is approximate: it relies on the phone being still and assumes awake hours of 7 am to
  11 pm.
- Geofence timing depends on Play services (usually within a few minutes; responsiveness is set
  to 2 minutes).
