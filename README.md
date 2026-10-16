# Loop

A habit tracker for Android. It tracks your day on its own (screen time, steps, activity,
sleep, places, music), adds quick check-ins (mood, energy, food photos, water, health), and
explains it all in plain words. Everything stays on the phone. The only network call is Gemini
for meal photos, using the user's own free API key.

- Min SDK 28, target/compile SDK 37
- Kotlin 2.4, AGP 9.4, Gradle 9.8, Compose + Material 3, Hilt, Room, DataStore, WorkManager,
  CameraX, ML Kit, Health Connect, Play services location, Glance, Vico, Retrofit

## Build and run

Needs JDK 17+ (built with JDK 21) and the Android SDK.

```sh
./gradlew assembleDebug installDebug
./gradlew testDebugUnitTest :core:model:test :core:domain:test :engine:insights:test :engine:gamification:test
./gradlew :core:database:connectedDebugAndroidTest :core:data:connectedDebugAndroidTest
python3 tools/build_food_db.py   # rebuilds the bundled food table
```

Food photos need a free Gemini key from Google AI Studio: Me › Food photo key.

## Where to look

- [docs/spec.md](docs/spec.md): the product spec
- [docs/HANDOFF.md](docs/HANDOFF.md): status, architecture, modules, conventions
- `docs/phase-*.md`: what each phase built, how to test it, known limits
- [docs/play-store-declarations.md](docs/play-store-declarations.md): Play Console text
