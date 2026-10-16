# Phase 4: Sleep

## What was built
- `:collector:sleep`: Sleep API segment updates (SLEEP_API sessions), registered every tick.
- `SleepEstimator` (pure, unit-tested) picks the best estimate for the night (6 pm to 2 pm).
  Order of trust: anything the user confirmed, then watch (Health Connect), then the Sleep API,
  then the longest night-time screen-off gap. A gap needs 3+ hours, a middle between 10 pm and
  9 am, and stillness (no more than 300 steps). An in-memory gap estimate is saved when the user
  is asked about it.
- A morning confirmation notification goes out once per day, between 5 am and 1 pm and 30+
  minutes after waking: "You slept 6 h 40 m. Does that look right?" with Yes / Fix it.
- `:feature:sleep`: last night with its source, "Looks right", "Fix it" (bedtime and wake time
  pickers save a confirmed USER session), and a 7-night chart.
- The nightly job detects the **day boundary** from recent wake times. It takes the 20th
  percentile of the last 14 nights, rounded down to the hour and kept between 3 am and 6 am, so
  the day ends at your wake time as the spec asks. The default is 4 am.

## How to test
After a night with the phone, open Sleep in the morning, or wait for the notification. Tap Fix it,
change the times and check that the new sleep shows as "You set this".

## Known limits
- Without Physical activity or Usage access there's nothing to estimate from. The screen asks for
  both.
- Naps aren't tracked separately.
