# Release builds

## Signing key

The release key is on the owner's Mac, outside the repo:

| What | Where |
|---|---|
| Keystore (PKCS12, RSA 4096, valid until 2054) | `~/.android/keystores/loop-release.jks`, alias `loop` |
| Passwords | `~/.gradle/gradle.properties` (`LOOP_RELEASE_*` entries) |
| Certificate SHA-256 | `3f:d1:ae:44:78:da:16:c5:58:48:16:57:dc:ae:ef:c9:ff:73:9f:2f:4f:60:de:f3:89:f8:3c:1f:88:4c:48:83` |

Keep a backup of the keystore file and its password somewhere other than this Mac, such as a
password manager. Android only installs an update when it is signed with the same key, so losing
either one means users have to uninstall and lose their data.

`app/build.gradle.kts` reads the four `LOOP_RELEASE_*` Gradle properties. On a machine without
them, the release build still runs but produces an unsigned APK. To build on another machine, copy
the keystore there and add the same four lines to that machine's `~/.gradle/gradle.properties`.

## Building

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./gradlew :app:assembleRelease   # APK for sideloading: app/build/outputs/apk/release/app-release.apk
./gradlew :app:bundleRelease     # AAB for Google Play:  app/build/outputs/bundle/release/app-release.aab
```

Raise `versionCode` (and usually `versionName`) in `app/build.gradle.kts` before every release.
Android refuses to install an update whose `versionCode` is not higher than the installed one.

Release builds are minified with R8. Keep rules for code that is only reached by reflection go in
`app/proguard-rules.pro`.

## Installing

Debug builds are signed with the debug key and release builds with the release key, and both use
the same application ID (`app.loop.habits`). A phone that has the debug build cannot update to the
release build. Export your data first (Me › Backup and export › Export to a file), uninstall the
debug build, install the release APK, then import the file.

[install.md](install.md) is a step-by-step guide for someone installing the APK on their own
phone. It covers unknown sources, Play Protect, restricted settings and updates.

Signed release APKs are committed to `dist/` as `loop-<versionName>.apk`, the only APKs not
gitignored. After each release build, copy `app/build/outputs/apk/release/app-release.apk` there.

## Google Play

If the app goes to Play, upload the AAB and opt in to Play App Signing. The key above then acts as
the upload key, and Google holds the key that signs what users download.
