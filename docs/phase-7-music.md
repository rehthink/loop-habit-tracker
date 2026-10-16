# Phase 7: Music

## What was built
- `LoopNotificationListener` watches active media sessions (MediaSessionManager) and logs
  MUSIC_PLAY / MUSIC_PAUSE per app when playback starts or stops. Open sessions are closed when
  the listener disconnects.
- `MusicCalculator` (pure, unit-tested) gives listening minutes per app. An unmatched play is
  capped at 4 hours.
- The Screen time screen's Listening card shows minutes per app for the selected day; `musicMin`
  feeds insights.

## Known limits
Any media session counts: video apps that use media sessions are included.
