# NoteSphere

NoteSphere is a local-first notes app for Android built with Kotlin, Jetpack Compose, Material 3, MVVM, Room, StateFlow, Navigation Compose, DataStore and AlarmManager.

The Android project directory is `NoteSphare` and the application ID is `com.ashis.notesphare`; the product name shown in the app is **NoteSphere**.

## Features

- Create, edit, autosave, pin, archive, trash, restore and permanently delete notes.
- Folders with colors. Deleting a folder keeps its notes and moves them to "No folder".
- Multiple tags per note, with create/rename/delete and note counts.
- Checklists attached to notes, including inline item editing and completion state.
- Reminders backed by `AlarmManager`, with notification permission handling and an inexact-alarm fallback when exact alarms are not allowed.
- Reminders are restored after reboot, app update, time change and timezone change.
- Search across active note titles and contents (debounced).
- Light, dark and system themes stored in Preferences DataStore.
- First-run onboarding, splash screen and empty states.
- Exponential-back autosave with save coalescing, plus save-on-exit for notes.
- Simple rich text markup with an in-editor preview.

## Requirements

- JDK 17 with `javac` available to Gradle.
- Android SDK platform 36 and build-tools compatible with AGP 8.13.0.
- Android Studio or AndroidIDE with network access to Google Maven and Maven Central for the first dependency resolution.
- An Android device or emulator running API 21 or newer for manual testing.

`local.properties` must point at the SDK, for example:

```properties
sdk.dir=/opt/android-sdk
```

## Build and run

```bash
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Install it with:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Tests

Unit tests (JUnit 4 and Robolectric):

```bash
./gradlew testDebugUnitTest
```

Instrumented UI tests:

```bash
./gradlew connectedDebugAndroidTest
```

Test coverage written for this MVP:

- `RichTextTest` - marker insertion/removal, bullets, markup parsing.
- `TextFormattingTest` - markup stripping, snippets, relative dates.
- `NoteRepositoryTest` - notes, search, pin/archive/trash/restore, folders, tags, checklists, reminders and trash deletion (Robolectric with in-memory Room).
- `OnboardingScreenTest` - onboarding completion flow.
- `EditorFlowTest` - title/content autosave and checklist persistence through `EditorViewModel`.
- `MigrationTest` / `MigrationSyncTest` - Room schema upgrades, sync metadata backfill and per-account pull cursors.

### Continuous integration

`.github/workflows/android-tests.yml` runs the JVM/Robolectric unit tests on an
x86_64 Ubuntu runner with JDK 17 whenever changes are pushed or a pull request
is opened. This is the recommended verification path because Robolectric's
native SQLite runtime does not support Linux aarch64 hosts.

**Verification status:** `:app:compileDebugKotlin` and
`:app:compileDebugUnitTestKotlin` pass. Robolectric database tests cannot run on
the Linux aarch64 authoring host, and no Android device was available for
instrumentation testing. GitHub Actions or an x86_64 machine is required to
confirm the migration and repository suites. Supabase auth/sync E2E still
requires a configured project; it has not been run.

## Project structure

```
app/src/main/kotlin/com/ashis/notesphare/
  MainActivity.kt              Edge-to-edge activity and notification deep navigation
  NoteSphereApp.kt             Application class and AppContainer
  data/local/                  Room entities, relations, DAOs, database and migration
  data/repository/             Note, folder, tag and settings repositories
  domain/                      AppSettings, ThemeMode and shared exceptions
  notifications/               Notification channel and reminder notifications
  reminders/                   Alarm scheduler, reminder receiver and boot receiver
  ui/components/               Reusable Compose components and dialogs
  ui/navigation/               Type-safe-ish route helpers
  ui/screens/                  Splash, onboarding, home, search, folders, tags,
                               notes lists, settings and editor
  ui/theme/                    Material 3 color schemes and theme wrapper
  ui/util/                     Rich text helpers, date/time picker and permissions
  util/                        Text formatting and date formatting helpers
```

### Persistence

Room database version 2 contains `notes`, `folders`, `tags`, `checklist_items` and `note_tag_cross_ref`. Version 1 was a notes-only prototype. `MIGRATION_1_2` is additive; there is no destructive fallback. Fresh installs create version 2 directly.

Settings use Preferences DataStore (`notesphare_settings`) for the theme mode and onboarding state.

### Reminders

`AlarmReminderScheduler` uses `setExactAndAllowWhileIdle` when exact alarms are available, otherwise `setAndAllowWhileIdle` (or `set` on API 21-22). A `SecurityException` falls back to an inexact alarm. `ReminderReceiver` posts the notification, clears the stored reminder time and opens the note when tapped. `BootReceiver` reschedules future reminders after boot, package replacement, time changes and timezone changes; reminders already in the past are scheduled shortly after rescheduling so they are not silently lost.

Required manifest permissions: `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, `SCHEDULE_EXACT_ALARM` and `VIBRATE`.

## Rich text format

The editor stores lightweight markup in the note content:

| Syntax | Result |
| --- | --- |
| `**text**` | bold |
| `*text*` | italic |
| `__text__` | underline |
| `~~text~~` | strikethrough |
| `` `text` `` | monospace |
| `- text` or `* text` at line start | bullet |

The raw markup stays visible while editing; Preview renders an `AnnotatedString` with the corresponding styles. There is no WYSIWYG span editor in this MVP.

## Known limitations

- Rich text editing is markup based, not a full WYSIWYG editor. There is no escaping syntax, nested-format validation, links, images or attachments.
- A new note row is created as soon as the editor opens. If the app process is killed before an explicit back navigation, an untouched new note may remain until removed.
- Autosave is immediate with write coalescing; there is no debounce interval and no conflict resolution beyond last write wins.
- Reminders are local only and depend on the device's alarm/notification settings and battery optimizations. Exact delivery is not guaranteed.
- Search covers active, non-archived notes only and uses SQL `LIKE` substring matching; it does not search trashed or archived notes.
- Trash is manual: notes are deleted permanently only by user action. There is no automatic retention period.
- No cloud sync, account, import/export, widgets, attachments, sharing, biometric lock or multi-window-specific layout.
- Only one notes database and one preferences store are supported; there is no profile support.
- Android Auto Backup is enabled by the default manifest configuration, so the system may include local app data in device backups. NoteSphere itself performs no network calls.
- The build and tests have not been run in this environment; dependency/toolchain compatibility and runtime behavior still require verification on a real build.

## Tech stack

- Kotlin 2.1.0, AGP 8.13.0, Gradle 9.0.0
- Jetpack Compose BOM 2025.02.00, Material 3
- Room 2.7.1 with KSP 2.1.0-1.0.29
- Navigation Compose 2.8.9
- Lifecycle 2.8.7, Activity Compose 1.10.1
- DataStore Preferences 1.1.4
- minSdk 21, compileSdk 36, targetSdk 34, Java/JVM target 17
- Tests: JUnit 4, Robolectric 4.14.1, Compose UI Test, AndroidX Test
