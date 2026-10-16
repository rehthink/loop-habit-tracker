# Phase 6: Check-ins

## What was built
- Mood check-ins at 9:30, 14:30 and 21:30, each re-timeable, answered **in the notification**:
  Good / Okay / Low, then a follow-up "And your energy?" High / Okay / Low. The energy question
  isn't skipped if mood is answered first.
- One-line journal (inline reply at 22:00), weekly weigh-in (inline reply on Sundays, validated
  20–350 kg) and office/home on weekdays (Office / Home / Elsewhere buttons). All are saved
  without opening the app.
- Medicine reminders at each medicine's times with a **Taken** action, logged to `medicine_dose`.
- `:feature:checkins`: check-in screen (mood and energy 1–5, note, journal and recent lines), health
  log (illnesses with severity, "I'm better", medicines with times), and weight with change since
  first weigh-in.
- `:collector:checkins` gives average mood and energy and `isSick`; office/home feeds `place`.

## How to test
Grant notifications. When the morning check-in arrives, tap Good, then High: the check-in appears
under Me › Mood and journal without opening Loop.
