# MdReader

Free, open-source native Android reader for Markdown in GitHub repositories. Kotlin, Jetpack Compose, Material 3.

## Setup
1. Android Studio (Koala or newer) with JDK 17. Open this folder.
2. If `gradlew` is missing: run `gradle wrapper --gradle-version 8.9` once (or let Android Studio use its bundled Gradle).
3. Sync, then Run on a device/emulator (minSdk 26).
4. Tests: `./gradlew testDebugUnitTest` (JVM) and `./gradlew connectedDebugAndroidTest` (Room, needs a device).
5. Release: add your own signing config, then `./gradlew assembleRelease`. Never commit keystores or tokens.

## Using private repositories
Settings -> paste a fine-grained personal access token (read-only Contents + Metadata, only the repos you need).
Stored encrypted (Android Keystore). Signing out deletes the token and cached private repositories.

## Known limitations
- Code blocks have no syntax colouring (monospace, horizontal scroll).
- Search: all matches are highlighted; "current match" is shown at block level.
- Paragraph images are laid out below their paragraph text; HTML other than `<img>` is reduced to text.
- Links to another document drop the `#anchor`.
- PDF: no inline bold/italic, images become `[image: alt]`.
- Very large repos: GitHub truncates the tree; the app flags the list as possibly incomplete.
- Documents over ~1M characters are shown but not cached.
- Auth uses a personal access token (OAuth needs a registered app/client id, which cannot live in source).
- No background sync (WorkManager intentionally not added).
