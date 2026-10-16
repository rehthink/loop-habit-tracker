# Phase 8: Aggregation, insights and gamification

## What was built
- `AggregateDayUseCase`: every enabled `TrackerSource` produces a `DailySummaryPatch`. These are
  merged (steps take the larger of phone and watch) under the day-boundary rule, plus calories
  out, data completeness and habit score, then upserted. Completeness counts only the core trackers
  the person has used in the last 14 days, so not logging food doesn't make every day
  "incomplete".
- Today and yesterday are refreshed on every tick and whenever a screen opens. `NightlyWorker`
  (about 4:30) detects the day boundary and finalises the last three days.
- `:engine:insights` (pure Kotlin, unit-tested on 30 days of seeded data):
  - baseline: 28-day mean of complete days
  - trend: 7-day mean vs baseline, shown when it changes by 10% or more
  - correlations: Pearson on daily pairs from a data list of 11 rules, only with 14+ days and
    |r| ≥ 0.4
  - anomalies: more than 2 SD from baseline
  - all of these become sentences with numbers ("On days with 7 h or more sleep, your mood
    averages 4.1 out of 5, compared with 3.2 on other days.")
  - also: ring progress, area scores, habit score (needs 2+ areas), calories out, the weekly
    report card (A–E per area) and personal bests
- `:engine:gamification` (pure, unit-tested):
  - XP and levels
  - streaks per habit with one free freeze per week
  - 11 badges defined as data
  - the weekly challenge, picked from last week's weakest area
  - "stay under" goals only count once the day is over
- Today: four rings, habit score, top insight, the challenge with progress, a setup card for
  missing permissions, and a celebration the first time a ring closes each day.
- Progress: level and XP, the report card (this week's finished days, or last week's on a
  Monday), Vico trend charts for 14 days against your usual, insights, streaks, badges with a
  one-time unlock celebration, and personal bests.
- Goals (Me › Goals) and body details for calories (Me › About you).

## How to test
`./gradlew :engine:insights:test :engine:gamification:test` covers 30 days of seeded data. The
correlation, trend, anomaly, baseline and score numbers are checked against hand-worked values.
