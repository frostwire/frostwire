---
name: frostwire-crash-triage
description: |
  Triage and fix FrostWire Android crashes and ANRs using Firebase Crashlytics
  BigQuery plus MentisDB memory. Use when asked to inspect the latest FrostWire
  Android release health, pick the next unclosed Crashlytics issue, study prior
  fixes and regressions on the frostwire chain as agent gubatron, patch the
  Android app, add regression tests, update changelog.txt, commit or push the
  fix, and track issue closure so fixed issues are not selected again.
triggers:
  - frostwire crash
  - android crash
  - crash triage
  - fix crash
  - ANR
  - pending crash
  - release health
  - 9090774
  - 9070774
  - 9060774
---

# FrostWire Android Crash Triage

## ⚡ MANDATORY STARTUP

1. `MENTISDB_REST_PORT=9472 mentisdb search "" --chain frostwire --limit 20 --url http://192.168.4.36:9472` → load recent context
2. `MENTISDB_REST_PORT=9472 mentisdb search "crash" --chain frostwire --limit 20 --url http://192.168.4.36:9472` → study prior fixes
3. Read `changelog.txt` → understand current release state
4. Read `AndroidManifest.xml` → confirm versionCode/versionName
5. Search codebase for the specific crash pattern before patching

## 🔍 CRASH TRIAGE WORKFLOW

### Step 1: Identify Pending Crashes
- Search MentisDB frostwire chain for unaddressed crash thoughts
- Check changelog.txt for "UNRELEASED" or pending fixes
- Search codebase for:
  - `getActivity()` without null checks in async callbacks
  - `requireActivity()`/`requireContext()` in background threads
  - `runOnUiThread()` without `isAdded()` guards in fragments
  - Native code (JNI/jlibtorrent) without try/catch
  - Missing `isAdded()` checks before fragment transactions
  - Background thread UI mutations

### Step 2: Study Prior Fixes
For each crash class, search MentisDB:
```bash
MENTISDB_REST_PORT=9472 mentisdb search "<crash-class-name>" --chain frostwire --limit 10 --url http://192.168.4.36:9472
```

Look for:
- Root cause patterns (e.g., detached fragments, wrong-thread ExoPlayer access)
- Fix strategies (e.g., WeakReference, isAdded() guards, background posting)
- Regressions caused by prior fixes
- Test patterns for the crash class

### Step 3: Patch the Code
**Fix principles (per AGENTS.md):**
- **Fail closed, not crashed** — wrap native code in try/catch
- **Off the main thread** — move JNI/I/O/parsing to background threads
- **Scope to the minimum** — capture values locally before async posts
- **Detached-safe** — check `isAdded()` / `Ref.alive()` before UI updates
- **Self-explanatory code** — well-named methods over comments

Common fixes:
| Crash Pattern | Fix |
|--------------|-----|
| `IllegalStateException: Fragment not attached` | Add `isAdded()` guard before `getActivity()`/`getContext()` |
| `NullPointerException: getActivity() returned null` | Capture `getActivity()` into `WeakReference` before async post |
| `ForegroundServiceStartNotAllowedException` | Catch in `onUpdateNotification()` or service lifecycle |
| `RemoteServiceException: startForeground not called` | Always call `startForeground()` before any early return |
| `ExoPlayer wrong thread` | Post to `mPlayerHandler` instead of direct access |
| `RejectedExecutionException` | Switch `SynchronousQueue` → `LinkedBlockingQueue`, increase pool size |
| `DeadSystemException` | Guard `WindowManager`/`NavigationView` inset listeners, swallow gracefully |
| `NoClassDefFoundError` after JNI init failure | Broaden catch to `Throwable`/`LinkageError`, add fallback flags |
| `IllegalStateException: Can not perform this action after onSaveInstanceState` | Check `getParentFragmentManager().isStateSaved()` before `show()` |

### Step 4: Add Regression Tests
If the project has tests:
- Add a test proving the crash scenario no longer crashes
- Test edge cases: null input, detached state, thread-safety
- Run `./gradlew test` or `./gradlew compilePlus1DebugJavaWithJavac`

### Step 5: Update Changelog
Add to `changelog.txt` under the UNRELEASED section:
```
FrostWire X.Y.Z build NNN MONTH/DAY/YEAR
  - fix:Short imperative description of crash fix
```

Follow the format from existing entries.

### Step 6: Commit and Push
Granular, focused commits per AGENTS.md:
```
[android] Fix <crash-name>: <short description>
```

One logical change per commit. Never merge master into feature branch — rebase.

### Step 7: Track in MentisDB
After each fix, append a `LessonLearned` or `TaskComplete` thought to the frostwire chain:
```bash
MENTISDB_REST_PORT=9472 mentisdb add "Fixed <crash>: <root-cause> → <fix>" \
  --type LessonLearned --chain frostwire --url http://192.168.4.36:9472 \
  --tag "android,crash-fix,<crash-class>"
```

## 📋 CRASH CATEGORIES

### Fragment Lifecycle
- Detached fragment access in `runOnUiThread` / `Handler.post`
- `getParentFragmentManager()` after `onSaveInstanceState`
- `requireActivity()` / `requireContext()` in background threads

### Service Lifecycle
- `startForeground()` not called before early return
- Null intent in `onStartCommand()` sticky restarts
- `ForegroundServiceStartNotAllowedException` from background

### Native / JNI
- `NoClassDefFoundError` after jlibtorrent init failure
- Invalid torrent handle access
- SWIG object disposal on wrong thread

### Threading / ANR
- JNI calls on UI thread
- `ContentResolver.query()` on UI thread
- File I/O on UI thread
- `CountDownLatch.await()` on UI thread

### Notification
- `BadForegroundServiceNotificationException` on Android 14+
- Custom `RemoteViews` rejected by system
- `Media3` foreground self-start from background

## 🧪 VERIFICATION

Before claiming complete:
- `./gradlew compilePlus1DebugJavaWithJavac` must pass
- `./gradlew assembleDebug` must pass
- If tests exist, `./gradlew test` must pass
- No new compiler warnings introduced
- Changelog updated with new entry

## 🎯 AGENT IDENTITY

Always operate as **gubatron** on the **frostwire** chain.
If agent identity is unclear, list agents and reuse `gubatron`.
