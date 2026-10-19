# Task Tracker Pro — Final Report

## 1. What Changed

Built a complete, production-quality Android task manager from scratch in the `roh4n-13/muse-taskapp` repository (main branch). The app is **local-first** — fully usable without internet or Google Sheets. All 21 task fields, dashboard, Smart Next Action, daily report, reminders, backup/restore, and optional two-way Google Sheets sync are implemented.

**Key deliverables:**
- Complete Android app (Kotlin, Jetpack Compose, Room)
- Debug APK built and verified (18MB)
- 98 unit tests, all passing
- Google Apps Script backend (Code.gs) for Sheets sync
- Hinglish README with setup instructions
- GitHub Actions CI workflow

## 2. Architecture

**Pattern**: MVVM with Repository pattern, Jetpack Compose UI

**Layers:**
- **UI** (`ui/screens/`): Compose screens — Dashboard, TaskList, TaskDetail, TaskEdit, Completed, Report, Sheets, Settings. Bottom navigation with 6 tabs.
- **Domain** (`data/`): `TaskRepository` (CRUD + sync metadata + tombstones), `Dashboard` (pure logic: stats, Smart Next Action, filters), `Dates` (ISO parsing), `BackupManager` (JSON export/import), `JsonUtil` (pure-Kotlin JSON, JVM-testable)
- **Data** (`data/`): Room database (`TaskDatabase`, `TaskDao`, `Task` entity with 21 fields + sync metadata: `syncState`, `deleted`, `origin`, `lastModified`)
- **Sync** (`data/sync/`): `SyncEngine` (two-way sync: push/pull, conflict resolution, tombstone deletes), `AppsScriptClient` (HTTP), `SheetsSchema` (21 headers, task↔map), `SecurePrefs` (encrypted token storage), `SyncPoller` (60s foreground polling)
- **Reminders** (`reminders/`): AlarmManager-based notifications for deadlines/follow-ups, BootReceiver for reboot persistence

**Tech stack**: Kotlin 1.9.24, Compose BOM 2024.10.01, Room 2.6.1, AGP 8.5.2, OkHttp 4.12.0, DataStore, EncryptedSharedPreferences (via security-crypto 1.0.0)

**Why Kotlin 1.9.24 (not 2.0.21)**: The Kotlin 2.0 Compose compiler Gradle plugin (`org.jetbrains.kotlin.plugin.compose`) is not published to Maven Central (only to the Gradle Plugin Portal, which was unreachable in the sandbox). Downgraded to 1.9.24 with `composeOptions.kotlinCompilerExtensionVersion = "1.5.14"` — a proven, stable combination.

## 3. Google Sheets

**Spreadsheet created**: "Task Tracker Pro — App Data"
- **ID**: `1KvCrSvMhVD2vRiA5bj32pDJq3PyAI617kTjMoVL938o`
- **URL**: https://docs.google.com/spreadsheets/d/1KvCrSvMhVD2vRiA5bj32pDJq3PyAI617kTjMoVL938o/edit
- **Tab**: "Tasks" with 21 headers in exact order (frozen + bold):
  ID, Task Name, Type, Progress, Priority, Deadline, Vendor / Craftsman, Contact Number, Estimated Cost (INR), Actual Cost (INR), Next Follow-up, Delivery Date, Payment Status, Link, Task Focus, Value Add, Notes, Task Added, Task Started, Task Completed, Last Updated

**Sync design (all verified in code, not live-tested):**
- **Optional**: App works 100% without Sheets. Sync is disabled until user explicitly connects via the Sheets tab.
- **New file only**: Sync uses the new spreadsheet above. Never touches the user's existing spreadsheet.
- **Stable IDs**: UUIDs, never row numbers.
- **Two-way**: App→Sheet pushes immediately; Sheet→App via 60s foreground polling (documented, no fake "instant push").
- **Conflicts**: Local unsynced edits win.
- **Deletes**: Tombstones; remote deletions only after validated complete snapshot. Never deletes unsynced local data.
- **First sync**: User chooses MERGE (recommended), UPLOAD_FIRST, or DOWNLOAD_FIRST.
- **Security**: Token stored encrypted; never in backup files; rotation documented.

**Apps Script** (`apps-script/Code.gs`): Implements `ping`, `snapshot`, `upsert`, `delete`, `batchUpsert` (cap 500). Uses `SECRET_TOKEN` from Script Properties with constant-time comparison. Creates "Tasks" tab + headers if missing.

**⚠️ Known issue**: A second empty duplicate spreadsheet with the same title was accidentally created (has "Tasks" tab but NO headers). Drive API was not authorized (only Sheets scope), so it could not be deleted via API. **User must manually delete the empty duplicate from Google Drive.**

## 4. Files Changed

**63 files** committed to `roh4n-13/muse-taskapp` (main branch):

**Build config:**
- `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `app/build.gradle.kts`
- `.github/workflows/android.yml` (CI: JDK 17, build, test, APK artifact)
- `.gitignore`

**App source** (`app/src/main/java/com/tasktracker/pro/`):
- `MainActivity.kt`, `TaskTrackerApp.kt` (DI container)
- `data/`: Task.kt, TaskDao.kt, TaskDatabase.kt, TaskRepository.kt, Dates.kt, Dashboard.kt, BackupManager.kt, JsonUtil.kt, SettingsStore.kt
- `data/sync/`: SheetsSchema.kt, SheetsApi.kt, AppsScriptClient.kt, SyncEngine.kt, SyncPoller.kt, SecurePrefs.kt
- `ui/screens/`: DashboardScreen, TaskListScreen, TaskDetailScreen, TaskEditScreen, CompletedScreen, ReportScreen, SheetsScreen, SettingsScreen
- `ui/components/`: TaskCard.kt
- `ui/`: MainScreen.kt (nav), FormatHelpers.kt, Theme
- `reminders/`: ReminderScheduler.kt, ReminderReceiver.kt, BootReceiver.kt
- `AndroidManifest.xml`, `res/values/themes.xml`, `tools/generate_icons.py`

**Backend:**
- `apps-script/Code.gs`, `apps-script/DEPLOY_NOTES.md`

**Docs:**
- `README.md` (Hinglish, sections A–N)

**Tests** (`app/src/test/`): 7 files, 98 tests (see §5)

**Note**: Launcher PNGs (`mipmap-*/ic_launcher.png`) are binary and were excluded from the GitHub push (API limitation). Run `python3 tools/generate_icons.py` to regenerate them, or use Android Studio's Image Asset tool.

## 5. Testing: PASS / FAIL / BLOCKED

**Unit tests** (`./gradlew :app:testDebugUnitTest`):
- **TOTAL: 98 tests, 0 failures, 0 errors — ALL PASS** ✅

| Test Suite | Tests | Result |
|------------|-------|--------|
| DashboardTest | 20 | PASS — stats, Smart Next Action, filters, daily report |
| DatesTest | 14 | PASS — ISO parsing (with/without offset), formatting |
| TaskRepositoryTest | 25 | PASS — CRUD, tombstones, sync metadata, queries |
| SheetsSchemaTest | 15 | PASS — 21 headers order, task↔map conversion |
| SyncEngineTest | 16 | PASS — push/pull, conflicts (local wins), deletions, first-sync modes |
| BackupManagerTest | 8 | PASS — JSON export/import, merge (no overwrite), malformed handling |

All tests are JVM-only (JUnit4 + kotlinx-coroutines-test). No Robolectric, no emulator needed.

**BLOCKED (not tested):**
- Live Google Sheets sync (requires user to deploy Apps Script + set token — code is written and unit-tested, but end-to-end not verified)
- Physical device reminders (AlarmManager code written, but notification delivery not tested on real hardware)
- Release build signing (debug APK only)

**No failures.** All reported results are from actual test runs, not fabricated.

## 6. APK Location

**Local build** (verified):
- Path: `app/build/outputs/apk/debug/app-debug.apk`
- Size: 18MB
- Built: 2026-10-19 19:53 (Asia/Kolkata)
- Status: `BUILD SUCCESSFUL`

**GitHub Actions** (after push completes):
- Workflow: `.github/workflows/android.yml`
- Triggers on push to main
- Builds debug APK, runs unit tests, uploads APK as artifact
- Download: Repo → Actions tab → latest green run → Artifacts → `app-debug` → extract ZIP

## 7. Setup

**For users (install APK):**
1. Download `app-debug.apk` from GitHub Actions artifacts (see §6)
2. Copy to Android phone (Android 8.0+)
3. Tap APK → allow "Install from unknown sources" → Install
4. Open app — no login needed, works immediately

**For developers (build from source):**
```bash
git clone https://github.com/roh4n-13/muse-taskapp.git
cd muse-taskapp
# Generate launcher icons (required for build)
python3 tools/generate_icons.py
# Build
./gradlew :app:assembleDebug
# Test
./gradlew :app:testDebugUnitTest
```
Requirements: JDK 17, Android SDK API 34.

**For Google Sheets sync** (optional): See README.md Section E for the 3-step setup (deploy Apps Script, connect in app, choose first-sync mode).

## 8. Known Limitations

1. **Live sync not verified**: The sync engine is implemented and unit-tested (16 tests), but end-to-end sync with a real Google Sheet was not tested because it requires the user to deploy the Apps Script and configure the token. The code follows the spec exactly, but treat live sync as "needs user verification."
2. **Reminders not device-tested**: AlarmManager + BootReceiver code is written, but notification delivery was not tested on physical hardware. Emulator testing recommended.
3. **Duplicate spreadsheet**: An empty duplicate ("Task Tracker Pro — App Data" without headers) exists in Drive. Delete it manually. The real one has ID `1KvCrSvMhVD2vRiA5bj32pDJq3PyAI617kTjMoVL938o`.
4. **Kotlin downgrade**: Using 1.9.24 instead of 2.0.21 due to sandbox network restrictions (Compose plugin not on Maven Central). Functionally equivalent for this app.
5. **Debug only**: No release signing configured. For Play Store, add signing config.
6. **Launcher icons**: PNGs excluded from git push (binary). Regenerate with `tools/generate_icons.py`.
7. **Sandbox build quirks** (for reproducibility): The sandbox required a local forward proxy (127.0.0.1:8888) for Gradle downloads, IPv4 forcing (`-Djava.net.preferIPv4Stack=true`), and importing the sandbox's MITM CA into JDK cacerts (which rotates periodically). These are environment-specific, not app issues. GitHub Actions builds without these workarounds.

## 9. Next Steps

**For rohan (user):**
- [ ] Delete the empty duplicate spreadsheet from Google Drive
- [ ] Download APK from GitHub Actions and install on phone
- [ ] Try the app: add tasks, test reminders, try backup/restore
- [ ] (Optional) Set up Google Sheets sync via README Section E
- [ ] Report any bugs or request features via GitHub Issues

**Future enhancements:**
- [ ] Verify live Sheets sync end-to-end
- [ ] Test reminders on physical device
- [ ] Signed release build for Play Store
- [ ] Home screen widget (today's tasks)
- [ ] Dark mode
- [ ] Multiple spreadsheet support

---

**Build info**: Completed 2026-10-19. All code in `roh4n-13/muse-taskapp` (main). The old `roh4n-13/task-tracker-android` repo was not modified.
