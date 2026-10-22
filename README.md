# Task Tracker Pro

**Aapke saare tasks ek jagah — simple, fast, aur aapke control mein.**

Task Tracker Pro ek Android app hai jo aapke kaam (tasks) ko track karta hai. Har task mein 21 details hote hain — naam, deadline, vendor, cost, follow-up, notes, aur bahut kuch. App bina internet ke bhi poori tarah kaam karta hai (local-first). Agar aap chahein, toh Google Sheets se sync bhi kar sakte hain — lekin woh bilkul optional hai.

---

## A. App kya karta hai? (Features)

- **Dashboard**: Aaj kya karna hai, overdue tasks, Smart Next Action (sabse zaroori kaam ka suggestion)
- **Task List**: Saare active tasks, search aur filter ke saath
- **Task Detail**: Har task ki poori 21-field detail
- **Add/Edit Task**: Naya task banao ya purana edit karo
- **Completed**: Poore hue tasks ka history
- **Daily Report**: Aaj ka summary — kya complete hua, kya pending hai
- **Reminders**: Deadline aur follow-up ke liye notification
- **Backup/Restore**: Apna data JSON file mein save karo, kabhi bhi wapas lao
- **Google Sheets Sync** (optional): Tasks ko Google Sheet mein sync karo

## B. Kaise install karein? (APK)

1. **GitHub Actions** se latest APK download karo:
   - Is repo ke **Actions** tab mein jao
   - Sabse upar wale successful run (green tick) par click karo
   - **Artifacts** section mein `app-debug` download karo
   - ZIP ko extract karo — andar `app-debug.apk` milega
2. APK ko apne Android phone mein copy karo
3. Phone mein APK par tap karo — agar "Unknown sources" ki permission maange, toh allow karo
4. Install ho jayega!

**Minimum requirement**: Android 8.0 (API 26) ya usse naya.

## C. Pehli baar app kholo toh kya dikhega?

- App khulte hi **Dashboard** dikhega
- Neeche 6 tabs hain: Dashboard, Tasks, Completed, Report, Sheets, Settings
- **+** button (neeche right corner) se naya task add karo
- Koi login ya signup nahi hai — app turant kaam karta hai

## D. Task kaise add karein?

1. **+** button dabao
2. Task ka naam likho (zaroori hai)
3. Baaki details bharo — Type, Priority, Deadline, Vendor, Cost, etc.
4. **Save** dabao
5. Ho gaya! Task list mein dikhega.

**21 fields**: ID (auto-generated), Task Name, Type, Progress, Priority, Deadline, Vendor/Craftsman, Contact Number, Estimated Cost (INR), Actual Cost (INR), Next Follow-up, Delivery Date, Payment Status, Link, Task Focus, Value Add, Notes, Task Added, Task Started, Task Completed, Last Updated.

## E. Google Sheets sync kaise setup karein? (Optional)

**Yaad rakho**: Ye bilkul optional hai. App bina Sheets ke poori tarah kaam karta hai.

Agar sync chahiye, toh:

### Step 1: Apps Script deploy karo
1. [script.google.com](https://script.google.com) kholo
2. **New project** banao
3. Is repo ke `apps-script/Code.gs` file ka poora content copy karke wahan paste karo
4. `apps-script/DEPLOY_NOTES.md` mein diye gaye steps follow karo:
   - Script Properties mein `SECRET_TOKEN` set karo (koi strong random string)
   - **Deploy** → **New deployment** → **Web app** chuno
   - "Execute as" = **Me**, "Who has access" = **Anyone** rakho
   - Deploy karo — tumhe ek URL milega jo `/exec` par khatam hota hai

### Step 2: App mein connect karo
1. App mein **Sheets** tab kholo
2. **Connect Google Sheets** dabao
3. Web App URL paste karo (jo Step 1 mein mila)
4. Secret Token daalo (jo tumne Script Properties mein set kiya tha)
5. **Test Connection** dabao — agar "Connected" dikhe, toh sahi hai!
6. **Enable Sync** karo

### Step 3: Pehli sync
- Pehli baar 3 options milenge:
  - **Merge**: Phone aur Sheet dono ka data milao (recommended)
  - **Upload first**: Pehle phone ka data Sheet mein bhejo
  - **Download first**: Pehle Sheet ka data phone mein lao

**Important**: Sync ek NAYI spreadsheet file banata hai — tumhari purani sheet ko haath nahi lagata. Headers hamesha isi order mein hote hain (21 columns).

## F. Sync kaise kaam karta hai? (Technical details)

- **Polling**: App har 60 second mein Sheet se naya data check karta hai (jab app khula ho)
- **Push**: Tumhare changes turant Sheet mein bheje jaate hain
- **Conflict**: Agar dono jagah change hua, toh phone ka unsynced change jeet-ta hai
- **Delete**: Phone se delete karne par Sheet se bhi delete hota hai (tombstone system)
- **Safety**: Bina sync kiya hua local data kabhi delete nahi hota

## G. Testing checklist (khud test karo)

- [ ] Naya task banao — list mein dikhe
- [ ] Task edit karo — changes save hon
- [ ] Task complete karo — Completed tab mein jaye
- [ ] Deadline set karo — reminder aaye (phone ki notification settings check karo)
- [ ] Settings se backup lo — JSON file bane
- [ ] Backup file se restore karo — tasks wapas aayein
- [ ] (Agar Sheets setup hai) Sync on karo — Sheet mein data dikhe

## H. Troubleshooting (Problem aaye toh)

| Problem | Solution |
|---------|----------|
| App install nahi ho raha | "Install from unknown sources" allow karo |
| Reminder nahi aaya | Phone Settings → Apps → Task Tracker Pro → Notifications ON karo. Battery optimization off karo. |
| Sheets connect nahi ho raha | URL `/exec` par khatam hona chahiye. Token bilkul sahi hona chahiye (copy-paste karo). |
| Sync mein error | Internet check karo. Phir "Test Connection" dabao. |
| Data gayab lag raha hai | Ghabrao mat! App local database use karta hai. Settings → Backup se check karo. |

## I. Security (Suraksha)

- **Token**: Tumhara Sheets secret token phone mein encrypted (EncryptedSharedPreferences) mein store hota hai
- **Token rotation**: Agar token leak ho jaye, toh Apps Script ki Script Properties mein naya token set karo, phir app mein update karo
- **Backup**: Backup file mein sirf task data hota hai — URL ya token kabhi nahi
- **Permissions**: App sirf zaroori permissions maangta hai (notifications, storage for backup)

## J. Project structure (Developers ke liye)

```
app/src/main/java/com/tasktracker/pro/
├── data/
│   ├── Task.kt              # 21-field Room entity
│   ├── TaskDao.kt           # Database queries
│   ├── TaskDatabase.kt      # Room database
│   ├── TaskRepository.kt    # Business logic + sync metadata
│   ├── Dates.kt             # Date parsing/formatting
│   ├── Dashboard.kt         # Dashboard stats + Smart Next Action
│   ├── BackupManager.kt     # JSON backup/restore
│   ├── JsonUtil.kt          # Pure-Kotlin JSON (testable)
│   └── sync/
│       ├── SheetsSchema.kt  # 21 headers, task<->map conversion
│       ├── SheetsApi.kt     # Backend interface
│       ├── AppsScriptClient.kt  # HTTP client
│       ├── SyncEngine.kt    # Two-way sync logic
│       ├── SyncPoller.kt    # 60s foreground polling
│       └── SecurePrefs.kt   # Encrypted token storage
├── ui/
│   ├── screens/             # Dashboard, TaskList, Detail, Edit, Report, Sheets, Settings
│   └── components/          # Reusable UI (TaskCard, etc.)
├── reminders/               # AlarmManager notifications
└── MainActivity.kt          # App entry point

apps-script/
├── Code.gs                  # Google Apps Script backend
└── DEPLOY_NOTES.md          # Deployment instructions
```

## K. Build kaise karein? (Developers)

**Requirements**: JDK 17, Android SDK (API 34), Gradle 8.7

```bash
# Debug APK
./gradlew :app:assembleDebug

# Unit tests
./gradlew :app:testDebugUnitTest
```

**Note**: Agar tumhare network mein proxy hai, toh `~/.gradle/gradle.properties` mein proxy settings chahiye honge. Details ke liye `.github/workflows/android.yml` dekho (CI automatically build karta hai).

## L. Automated tests

```bash
./gradlew :app:testDebugUnitTest
```

**Test results** (last run):
- **98 tests, 0 failures** ✅
- DashboardTest: 20 tests — dashboard stats, Smart Next Action
- DatesTest: 14 tests — date parsing (ISO with/without offset)
- TaskRepositoryTest: 25 tests — CRUD, tombstones, sync metadata
- SheetsSchemaTest: 15 tests — 21 headers, task<->map conversion
- SyncEngineTest: 16 tests — push/pull, conflicts, deletions
- BackupManagerTest: 8 tests — JSON export/import, merge logic

Saare tests JVM par chalte hain (koi emulator nahi chahiye).

## M. Known limitations (Pehle se pata hona chahiye)

1. **Live Sheets sync verify nahi hua**: Code likha aur test kiya gaya hai, lekin asli Google Sheet se live sync test nahi hua (tumhe khud Apps Script deploy karna hoga).
2. **Reminders physical device par test nahi hue**: AlarmManager code likha hai, lekin asli phone par notification test nahi hua.
3. **Duplicate spreadsheet**: Galti se ek khaali duplicate spreadsheet ban gayi thi ("Task Tracker Pro — App Data" naam se). Asli wali mein headers hain. Khaali wali ko Google Drive se manually delete kar dena.
4. **Kotlin 1.9.24**: Original plan 2.0.21 tha, lekin sandbox network restrictions ki wajah se 1.9.24 use karna pada (Compose compiler plugin Maven Central par nahi mila).
5. **Debug APK**: Ye debug build hai, release build nahi. Play Store par daalne ke liye release signing chahiye hogi.

## N. Agle steps (Future mein)

- [ ] Live Sheets sync ko asli Sheet se test karo
- [ ] Physical device par reminders test karo
- [ ] Release APK banao (signed)
- [ ] Widget: Home screen par aaj ke tasks dikhao
- [ ] Dark mode
- [ ] Multiple sheets support

---

## License

Ye project personal use ke liye banaya gaya hai.

## Questions?

Agar kuch samajh na aaye, toh is repo mein **Issue** kholo ya code mein comments padho — har important jagah Hindi/English mix mein samjhaya gaya hai.
