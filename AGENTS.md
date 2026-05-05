# FrostWire Agent Coding Guidelines

> Combined best practices distilled from **gubatron** (FrostWire project owner, lead developer) and **aldenml** (core contributor, JLibTorrent architect) working styles, mantras, and coding philosophies across the FrostWire Desktop, Android, and JLibTorrent codebases.

---

## 1. Core Philosophies

### Keep It Simple, Stupid (KISS)
- If it's too hard to explain what you're doing, you're probably making things more complex than they already are.
- Prefer well-named methods and code re-usability over excessive comments. **Code should be self-explanatory.**
- We prefer deleting code over adding code. Net-negative line count is often a sign of a good refactor.

### Do Not Repeat Yourself (DRY)
- Re-use your own code and our code. It'll be faster to code and easier to maintain.
- DRY code behaves like a perfect equation as it grows — it starts being written by itself.

### Minimum Scope Principle
- Keep variables as close to their local scope as possible.
- Classes should expose as little as possible:
  1. Local variable first
  2. Private member if it must outlive the method
  3. Protected only for inheritance
  4. Public only when you are certain no consumer can break internal state
- Tight scopes prevent bugs in multithreaded environments where objects are accessed concurrently.

### Composition Over Inheritance
- If you're extending a non-abstract class, you should probably be composing it instead.
- If you don't have access to the code of the class you are extending, things may not behave as expected.

### Immutability Where Possible
- Favor immutable objects and immutable state. This is related to minimum scope and reduces entire classes of concurrency bugs.

---

## 2. Concurrency & Threading Rules

### Never Block the UI Thread (EDT on Desktop / Main Thread on Android)
- **All heavy operations** (JNI, I/O, network, parsing, database writes) must run on background threads.
- Desktop: Use `DesktopParallelExecutor` or `GUIMediator.safeInvokeLater()` for UI updates.
- Android: Use background threads / AsyncTask successors / coroutines; never perform network or disk I/O on the main thread.
- **JNI calls to jlibtorrent must never happen on the EDT.** Always offload to a background executor.

### Thread-Safe Singletons
- If you use singletons, make them thread-safe (double-checked locking or static holder pattern).
- Clear singleton data models before reloading to prevent stale state.

### Defensive Programming in Concurrent Contexts
- Null-check everything that crosses thread boundaries.
- Wrap potentially crash-prone native code in try/catch so the app **fails closed, not crashed**.
- Example: If Python ELF initialization fails on Android, catch it, log it, and fall back to `"<unavailable>"` — do not crash the app on every startup.

---

## 3. Native Code & JNI (JLibTorrent)

### Memory Management
- SWIG-generated objects hold native memory. Be explicit about ownership.
- Close / dispose / finalize native resources deterministically when possible.
- Do not rely solely on finalizers for native cleanup.

### Prefer Library-Native Persistence
- If the underlying library (libtorrent/jlibtorrent) already persists state (e.g., `save_state_flags_t.all()`), **do not build a parallel persistence layer**.
- Example: Removed `ip_filter.db` because jlibtorrent persists `ip_filter` via session state. Net result: −149 lines, fewer bugs.

### Lazy Loading
- Defer expensive initialization until the user actually needs it.
- Example: The IP Filter table is lazily loaded only when the panel is opened, not at app startup.

---

## 4. UI & User Experience

### Internationalization (I18n)
- **All user-facing strings must use `I18n.tr()`** (Desktop) or Android string resources.
- Never hardcode English strings in UI code.

### Skin / Theme Consistency
- Use themed components (`SkinPopupMenu`, `SkinButton`, etc.) instead of raw Swing/Android defaults.
- Test UI changes across light and dark themes.

### Graceful Degradation
- If a feature fails (e.g., downloading a blocklist, parsing a file), show a helpful error message and allow the user to continue.
- Never let an optional feature crash the entire application.

---

## 5. Testing & Quality

### Tests Are Non-Negotiable
- If you can include tests for your patch, you get extra bonus points.
- Tests should cover:
  - Happy path
  - Edge cases (empty input, malformed data, max values)
  - Decompression / I/O paths
  - Format detection heuristics
- Real-world fixtures are valuable: use actual sample files (e.g., real `iblocklist.com` downloads) for integration tests.

### Compile Before Commit
- Always verify with `./gradlew compileJava` (Desktop) or `./gradlew compilePlus1DebugJavaWithJavac` (Android) before claiming completion.
- Fix compiler warnings; do not suppress them unless justified.

### Bug Fix Pattern
1. Reproduce and understand root cause.
2. Write the smallest possible fix.
3. Add regression test if feasible.
4. Update `changelog.txt` for user-facing fixes.
5. Verify the build passes.

---

## 6. Git & Commit Hygiene

### Granular, Focused Commits
- One logical change per commit.
- Example: "remove ip_filter.db persistence" and "move JNI calls off EDT" were split into two separate commits even though they were done together.

### Clean History
- Squash cosmetic / WIP commits before pushing.
- Force-push with lease (`git push --force-with-lease`) when rewriting history on a feature branch.
- **Never merge `master` into your feature branch.** Rebase instead:
  ```bash
  git fetch origin master
  git checkout my-branch
  git rebase origin/master
  ```

### Commit Message Format
```
[desktop] short imperative description (#issue)
[android] short imperative description (#issue)
[common] short imperative description (#issue)
[all] short imperative description
```
- Prefix with scope tag in brackets.
- Reference issue numbers so GitHub auto-links them.
- Use imperative mood: "Fix NPE" not "Fixed NPE".

### Feature Branches
- All pull requests must come from a feature branch on your fork.
- Name the branch descriptively: `issue-1291` or `fix-ip-filter-edt-crash`.

---

## 7. Code Style (Java / Android)

### Readability First
- Well-named methods > comments.
- Self-explanatory code is the goal.

### Resource Cleanup
- Use try-with-resources for all streams, readers, and closeables.
- Clean up temp files after use (`gunzipped_blocklist.temp`, `unzipped_blocklist.temp`, etc.).

### Logging
- **Always use `com.frostwire.util.Logger`** — never `org.apache.commons.logging`, `java.util.logging`, or other frameworks.
- Example: `private static final Logger LOG = Logger.getLogger(MyClass.class);`

### Null Safety
- Null-check deserialized objects, especially when parsing external data.
- Guard against NPEs at API boundaries (intents, bundles, JSON, network responses).

### Avoid Magic Numbers & Strings
- Use named constants for regex patterns, buffer sizes, and format signatures.

### Android Specifics
- Guard Android lifecycle callbacks against null / destroyed state.
- Use `try/catch` around native initialization (Python, ffmpeg, etc.) because corrupted native binaries on user devices are a real-world occurrence.
- Check for null `listData`, `srList`, and other deserialized objects before use.

---

## 8. Dependency & Build Hygiene

### Careful Updates (aldenml style)
- Update Gradle plugins, build tools, and third-party SDKs one at a time.
- Verify builds on all targets after a dependency bump.
- Avoid duplicate resources in `build.gradle`.

### Build Verification
- Desktop: `./gradlew compileJava` and `./gradlew test`
- Android: `./gradlew assembleDebug`
- JLibTorrent: native builds must pass on all platforms before merging.

---

## 9. Pull Request & Review Culture

### PR Description
- Explain what issue you're fixing and **how** you're fixing it in detail.
- If it's too hard to explain, simplify the solution.
- Do not include formatting noise — it makes review impossible for a small team.

### Review Checklist
- [ ] Build passes
- [ ] No formatting noise
- [ ] Tests included or justified
- [ ] UI strings are i18n'd
- [ ] Heavy work is off the main/UI thread
- [ ] Native resources are cleaned up
- [ ] Changelog updated if user-facing

---

## 10. Quick Reference Mantras

| Mantra | When to apply |
|--------|---------------|
| **"Fail closed, not crashed"** | Native code, deserialization, network calls |
| **"Off the EDT / off the main thread"** | JNI, I/O, parsing, heavy computation |
| **"jlibtorrent already handles this"** | Before building custom persistence |
| **"Delete code, don't hoard it"** | Refactors, cleanup, removing dead layers |
| **"One change, one commit"** | Git history hygiene |
| **"If you can't explain it, simplify it"** | Design review, PR description |
| **"Scope to the minimum"** | Variable lifetime, visibility, mutability |
| **"Real fixtures, real tests"** | Test data — use actual files when possible |

---

*Last updated: 2026-05-05 by agent `gubatron` on the FrostWire chain.*
