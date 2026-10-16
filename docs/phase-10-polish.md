# Phase 10: Polish

## What was built
- **Battery guide** (Me › Keep Loop running): live status plus brand-specific steps for
  Xiaomi/Redmi/POCO, Samsung, OnePlus/Oppo/realme, vivo/iQOO, Huawei/Honor, Motorola and others.
  Buttons open the app's settings, battery optimisation and the brand's auto-start screen. Every
  OEM screen opens through a guarded intent, so it never crashes.
- **Backup and export** (Me › Backup and export):
  - Auto Backup covers only `loop.db` and settings (not secrets or photos).
  - Export writes versioned JSON (`format: loop-export`, `version: 1`, with the schema version)
    through the system file picker.
  - Import (confirmed by name) replaces everything in one transaction, rejects other files and
    newer versions, and is idempotent.
  - **Delete everything** (named confirmation) wipes every table, settings, secrets, photos and
    cache, cancels background work and returns to onboarding.
- Empty and missing-data states everywhere: rings say "No data yet", null is never shown as 0,
  each feature has permission gate cards, and the friendly empty states point to the next action.
- An instrumented test (`BackupRoundTripTest`) proves export → import restores everything
  (including nulls), that importing twice doesn't duplicate, that bad or newer files are
  rejected, and that delete wipes everything.

## How to test
Export, delete everything, go through onboarding, import the file: the days, meals, check-ins and
settings come back.
