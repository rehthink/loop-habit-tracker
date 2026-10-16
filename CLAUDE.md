# Loop (Android habit tracker)

Start here:
1. [docs/spec.md](docs/spec.md): the owner's full build spec (phases, rules, data model, design).
2. [docs/HANDOFF.md](docs/HANDOFF.md): status (all 10 phases built and reviewed), architecture,
   modules, conventions, environment, and open items.

Rules from the spec that still apply to any change:
- Ask before adding any library not in spec §3, any new permission, or any network call other
  than Gemini.
- Battery budget: no wake locks, nothing faster than the 15-minute tick, batch writes.
- Missing data is null, never 0. Gemini key and photos are never logged.
- The engines (`:engine:*`) and collector maths stay pure Kotlin with unit tests.

Build with JDK 21: `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./gradlew assembleDebug testDebugUnitTest`.
