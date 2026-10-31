# Gitread.me

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

## Offline reading and backup
- In Open from GitHub, use a repository's three-dot menu -> Make available offline. You can also download repositories from Settings -> Manage saved repositories. Files over 1,000,000 characters are skipped.
- The Bookmarks screen has tabs for bookmarked files and repositories whose Markdown files are all cached for offline reading.
- Reader position is saved per document and restored when you open it again.
- Settings -> Backup exports theme, reader appearance, font size, and bookmarks as JSON. Tokens and cached documents are never included.
- To restore bookmarks on another device, save the matching repositories there first, then import the JSON backup.

## Known limitations
- Code syntax colors are lightweight highlighting, not a full language parser.
- Search: all matches are highlighted; "current match" is shown at block level.
- Paragraph images are laid out below their paragraph text; HTML other than `<img>` is reduced to text.
- Links to another document drop the `#anchor`.
- PDF: no inline bold/italic, images become `[image: alt]`.
- Very large repos: GitHub truncates the tree; the app flags the list as possibly incomplete.
- Documents over ~1M characters are shown but not cached.
- Auth uses a personal access token (OAuth needs a registered app/client id, which cannot live in source).
- No background sync (WorkManager intentionally not added).
