---
name: frostwire-code-reviewer
description: FrostWire code review skill — ensures correctness, performance, safety, security, gubatron+aldenml code style adherence, documentation quality, and regression test coverage for all new code and bug fixes in the FrostWire Desktop, Android, and common/ modules. Context-aware: applies different checks depending on whether code targets desktop, Android, or common. Enforces common/ JDK compatibility with Android (the limiting factor). Use when reviewing code before commit, during PR review, or when auditing existing code for issues.
triggers:
  - code review
  - review
  - review my code
  - audit
  - security review
  - correctness
  - regression test
  - PR review
  - pull request review
  - before commit
  - spotless
  - lint
---

# FrostWire Code Reviewer

> Systematic review framework for FrostWire code changes. Covers correctness, performance, safety, security, code style, documentation, testing, and **cross-platform compatibility**. Complements the `frostwire-engineer` skill — that skill defines the rules, this skill enforces them.

---

## When to Use This Skill

- **Before commit**: self-review your own diff
- **During PR review**: review another agent's or contributor's changes
- **Security audit**: deep-dive on code that handles untrusted input
- **Pre-release gate**: final review before tagging a release
- **Post-fix verification**: confirm a bug fix won't regress

---

## Step 0: Determine Project Context

Before reviewing, determine which module(s) the change touches. Each module has different constraints:

### Module: `common/`

**Compiled by BOTH desktop and Android.** This is the most restrictive target.

| Constraint | Value | Why |
|------------|-------|-----|
| Java source/target | **17** (Android's `sourceCompatibility`) | Android build.gradle sets VERSION_17 |
| `java.net.http.*` | **FORBIDDEN** | Not available on Android without core library desugaring |
| `java.awt.*`, `javax.swing.*` | **FORBIDDEN** | Desktop-only APIs |
| `java.nio.file.*` (Path, Files) | **FORBIDDEN** | Not available on Android API 26 |
| `ProcessBuilder` | **AVOID** | Android can't spawn JVM subprocesses |
| `java.sql.*` (JDBC) | **FORBIDDEN** | Android uses `android.database.sqlite`, not JDBC |
| `System.getProperty("user.home")` | **FORBIDDEN** | Doesn't exist on Android; use injected `File` paths |
| `ScheduledExecutorService` | **OK but flag for Android** | Works but dies in doze mode; consider WorkManager for periodic tasks |
| OkHttp | **OK** | Available on both desktop (4.12.0) and Android (5.3.2) |
| Gson | **OK** | Available on both |
| jlibtorrent | **OK** | Available on both |
| Netty | **OK** | Available on both (desktop 4.2.0.Final, Android same) |
| BouncyCastle | **DESKTOP ONLY** | Not an Android dependency |

**When reviewing `common/` changes, ALWAYS check:**
- Does the code import anything from `java.awt`, `javax.swing`, `java.net.http`, `java.sql`, `java.nio.file`?
- Does the code use `System.getProperty("user.home")` or other desktop-only system properties?
- Does the code compile against Java 17 (not Java 19)?

### Module: `desktop/`

| Constraint | Value |
|------------|-------|
| Java source/target | **19** |
| Full JDK available | Yes |
| Swing, AWT | OK |
| JDBC (sqlite-jdbc) | OK |
| `java.net.http` | OK (but prefer OkHttp for shared code) |
| `ProcessBuilder` | OK (IceBridge subprocess) |

### Module: `android/`

| Constraint | Value |
|------------|-------|
| Java source/target | **17** |
| `compileSdk` | 36 |
| `minSdk` | **26** (Android 8.0) |
| UI framework | Android Views / Kotlin |
| DB | `android.database.sqlite.SQLiteDatabase` |
| Background tasks | **WorkManager** (not `ScheduledExecutorService` for periodic work) |
| `Build.VERSION.SDK_INT < 24/26` guards | **DEAD CODE** — minSdk is 26 |
| `EditTextPreference` values | Stored as **String**, not Integer |

---

## Step 1: Understand the Change

1. **Read the commit message / PR description** — what is the change trying to do?
2. **Identify the threat model** — does the changed code handle untrusted input? (remote peer data, user-supplied strings, network responses, file contents, deserialized objects)
3. **Identify the blast radius** — what modules are affected? `common/` affects both desktop and Android.
4. **Check the diff size** — large diffs (>500 lines) need sectioned review. Ask the author to split if needed.

---

## Step 2: Review by Category

Walk each category. For each finding, assign a severity:

| Severity | Meaning | Action |
|----------|---------|--------|
| **BLOCK** | Will cause crashes, data loss, security breach, or incorrect behavior | Must fix before commit |
| **HIGH** | Likely to cause issues in production or makes future maintenance hard | Should fix before commit |
| **MEDIUM** | Code quality issue that degrades readability or maintainability | Fix in this commit or next |
| **LOW** | Style nit, minor naming, cosmetic | Mention but don't block |
| **INFO** | Observation, suggestion, or positive note | No action required |

---

## 1. Cross-Platform Compatibility

**Most important for `common/` changes.** If code is in `common/`, it must compile and run on Android.

### Checklist

- [ ] **No desktop-only imports** — grep the diff for `import java.awt`, `import javax.swing`, `import java.net.http`, `import java.sql`, `import java.nio.file`. If found in `common/`, BLOCK.
- [ ] **No `System.getProperty("user.home")`** in `common/` — Android doesn't have a home directory. Use injected `File` paths or `Context.getFilesDir()`.
- [ ] **No JDBC in `common/`** — `java.sql.Connection`, `DriverManager`, `PreparedStatement`, `ResultSet` are desktop-only. Android uses `android.database.sqlite.SQLiteDatabase`. Abstract behind an interface (like `LocalIndex`).
- [ ] **Java 17 compatible** — no `record` patterns, no `sealed` classes, no `switch` expressions with pattern matching, no `java.util.random.RandomGenerator` interface (use `java.util.Random` or `SecureRandom`).
- [ ] **No `ProcessBuilder` in `common/`** — Android can't spawn JVM subprocesses. If a feature needs subprocess launch, put the launcher in `desktop/` only.
- [ ] **OkHttp over `java.net.http`** — when writing HTTP client code in `common/`, use OkHttp (available on both platforms), not `java.net.http.HttpClient` (Java 11+ desktop only).
- [ ] **Android `minSdk` = 26** — all `Build.VERSION.SDK_INT < 26` guards are dead code. Don't add them.
- [ ] **Test on both targets** — `cd desktop && ./gradlew compileJava` AND `cd android && ./gradlew compilePlus1DebugJavaWithJavac`.

---

## 2. Correctness

### Checklist

- [ ] **Happy path works** — does the code produce the correct result for normal input?
- [ ] **Null safety** — are all parameters, return values, and cross-thread boundaries null-checked? Especially: deserialized objects, Intent extras, JSON fields, network responses, cursor columns.
- [ ] **Empty/edge cases** — empty lists, empty strings, zero, negative numbers, max values, empty arrays, single-element collections, empty Optional.
- [ ] **Boundary conditions** — off-by-one errors, `<=` vs `<`, inclusive vs exclusive ranges, `Integer.MAX_VALUE` wrap, `Math.abs(Long.MIN_VALUE)` returns negative.
- [ ] **Integer overflow** — `long cutoff = now - threshold` can underflow if `threshold > now`. `Math.multiplyExact` for multiplications that could overflow. `Integer.compareUnsigned` for sequence numbers that wrap.
- [ ] **Exception handling** — does the code catch the right exceptions? Does it fail closed (safe default) not crashed? Are resources cleaned up in `finally` or try-with-resources?
- [ ] **Concurrency** — if the code crosses thread boundaries, is shared state protected? Are singletons thread-safe? Are volatile/atomic variables used correctly? Is the `synchronized` lock object correct?
- [ ] **Resource leaks** — are all `Cursor`, `Connection`, `InputStream`, `OutputStream`, `Reader`, `Writer`, `PreparedStatement`, `ResultSet` closed via try-with-resources? Are native handles (jlibtorrent SWIG objects) released deterministically?
- [ ] **Transaction atomicity** — if multiple DB operations must be atomic, are they wrapped in a transaction? (`db.beginTransaction()` / `setTransactionSuccessful()` / `endTransaction()`)
- [ ] **Defensive copies** — are byte arrays cloned on POJO boundaries? (`return bytes.clone()` on getters, `this.x = x.clone()` in constructors). Are mutable collections exposed via defensive copies?
- [ ] **UTF-8 handling** — are string lengths measured in bytes (for network/DB) or chars (for display)? `String.getBytes(UTF_8).length` vs `String.length()`.

### FrostWire-specific correctness checks

- [ ] **BTEngine off UI thread** — JNI calls to jlibtorrent (`BTEngine`, `SessionManager`, `Ed25519`, `TorrentInfo`) must NEVER happen on the EDT (desktop) or main thread (Android). Check for `SystemUtils.postToHandler(MISC, ...)` or `GUIMediator.safeInvokeLater()`.
- [ ] **Android lifecycle** — are Activity/Fragment/Service callbacks guarded against null/destroyed state? Is `getContext()` null-checked?
- [ ] **SharedPreferences thread safety** — `OnPreferenceChangeListener` callbacks run on the main thread. Any BTEngine/DB call inside must be dispatched to a background thread.
- [ ] **Search input sanitization** — `LocalIndex.search()` is called with keywords from remote peers (via `RelaySearchService`). All search input must be treated as untrusted. FTS5 queries must be sanitized. LIKE queries must escape wildcards.

---

## 3. Security

Code that handles untrusted input must be hardened against malicious actors.

### Threat Model Questions

1. **Where does input come from?** — remote peer (signed request), network response (HTTP), user input (search bar), file on disk (torrent, config), DHT (untrusted)
2. **What can a malicious actor control?** — keywords in search requests, JSON payloads, HTTP response bodies, file paths, info hashes, public keys, DHT items
3. **What is the impact of misuse?** — data leak (entire local index via `%` wildcard), crash (malformed JSON), resource exhaustion (large payload), injection (SQL, FTS5, command)

### Checklist

- [ ] **SQL injection** — all queries use parameterized `?` placeholders, never string concatenation with user input.
- [ ] **FTS5 injection** — FTS5 MATCH expressions are sanitized. Reserved words (`OR`, `AND`, `NOT`, `NEAR`) are quoted. Non-alphanumeric chars are stripped.
- [ ] **LIKE wildcard injection** — `%` and `_` in user input are escaped with `ESCAPE '\\'`. Without this, a remote peer can send `%` to match the entire local index in one query.
- [ ] **Path traversal** — file paths from untrusted sources are validated. No `../../etc/passwd`. Use `File.getCanonicalPath()` and verify it starts with the allowed root.
- [ ] **Deserialization** — JSON parsing is wrapped in try-catch. Malformed JSON returns empty/null, not crash.
- [ ] **Signature verification** — remote requests are Ed25519-signed. The signature is verified BEFORE processing. Timestamp skew is checked (anti-replay). Rate limiting is per-source.
- [ ] **Auth token** — control API endpoints (except `/health`) require `X-IceBridge-Token` header. The token is generated server-side, not client-supplied.
- [ ] **Input length caps** — all untrusted inputs are length-capped. Keywords ≤256 chars. JSON payloads ≤16MB. File lists ≤10,000 entries.
- [ ] **No secrets in logs** — private keys, auth tokens, passwords, BIP39 mnemonics are NEVER logged. Truncate logged payloads to 200 chars.
- [ ] **No secrets in commits** — `git diff --cached` reviewed before commit. No `.pem`, `.key`, `.p12`, `.crt` files. No hardcoded API keys or passwords.
- [ ] **Integer overflow as attack** — `Math.abs(Long.MIN_VALUE)` is negative. Use manual sign flip: `long diff = a - b; long abs = diff >= 0 ? diff : -diff;`
- [ ] **Rate limiting** — per-source rate limiting on all incoming peer requests. Sliding window.
- [ ] **Error messages don't leak** — rejection responses to remote peers must not reveal the rejection reason (helps attackers tune bypasses). Log details locally only.

---

## 4. Performance

### Checklist

- [ ] **No O(n²) on hot paths** — search results, UI lists, peer directories. If iterating a collection inside another iteration, consider a Set/Map lookup instead.
- [ ] **No DB calls on UI thread** — all `SQLiteDatabase` operations are on background threads. `synchronized(db)` blocks must be short.
- [ ] **No network on UI thread** — all HTTP, rUDP, DHT operations are off the main thread.
- [ ] **Batch DB operations** — multiple INSERTs use `beginTransaction()/endTransaction()` not individual auto-commits.
- [ ] **Cursor management** — Cursors are closed via try-with-resources. Large result sets are paginated (`LIMIT`).
- [ ] **Memory bounds** — collections from untrusted sources are capped. Response bodies are size-capped. Inbound message queues have eviction.
- [ ] **Thread pool sizing** — daemon threads are marked `setDaemon(true)`. Named threads for debugging.
- [ ] **Lazy initialization** — expensive resources are loaded on first use, not at startup.
- [ ] **Static final for constants** — regex patterns, Gson instances, Logger instances are `private static final`, not created per-call.
- [ ] **Connection reuse** — OkHttp `OkHttpClient` instances are reused (they have connection pools). Don't create a new client per request.

### FrostWire-specific performance checks

- [ ] **jlibtorrent Ed25519 over JDK** — `IdentityKeys.generate()` uses `com.frostwire.jlibtorrent.Ed25519.createKeypair(seed)` (native, 50-100x faster than JDK `KeyPairGenerator`).
- [ ] **Polling intervals appropriate for mobile** — 300ms transport poller is OK for foreground. Consider adaptive intervals: faster when screen on, slower when backgrounded. DHT advertiser every 15-30 min on mobile (not 5 min like desktop).

---

## 5. Safety

### Checklist

- [ ] **Fail closed, not crashed** — native code, deserialization, network calls are wrapped in try-catch. The app falls back to a safe default, never crashes on every startup.
- [ ] **Race conditions** — `close()` methods acquire the same lock as read/write methods. Double-checked `open` flag. No "set flag then close without lock" patterns.
- [ ] **Shutdown ordering** — components are shut down in reverse order of startup. Listeners removed before transports closed. Transports closed before servers.
- [ ] **Native init wrapped** — `try/catch` around all native initialization (Python, ffmpeg, jlibtorrent `.so` load). Corrupted native binaries on user devices are a real-world occurrence.
- [ ] **Synchronous cleanup before async** — if state is cleaned up and then an async callback fires, the callback must see the cleaned state, not stale data.
- [ ] **Timeout audit** — every network/IO operation has a timeout. No `Thread.sleep(Long.MAX_VALUE)` without a shutdown path. No infinite `Object.wait()` without a notify.

### Android-specific safety checks

- [ ] **Memory leaks** — no static references to Activity/Fragment/View. Non-static inner classes holding implicit outer reference to Activity = leak. Use `static` inner classes with `WeakReference` or standalone classes.
- [ ] **Listener/Receiver cleanup** — `BroadcastReceiver`, `ContentObserver`, `Cursor` registered in `onCreate`/`onResume` must be unregistered in `onDestroy`/`onPause`.
- [ ] **Background execution limits** — Android 8+ requires foreground service or WorkManager for background work. `ScheduledExecutorService` daemons get killed in doze mode. Document which components need WorkManager migration.
- [ ] **Battery impact** — polling intervals should be adaptive. GPS, DHT, and network polling drain battery. Consider `WorkManager` with `NetworkType.CONNECTED` constraints.

---

## 6. Code Style (gubatron + aldenml)

The code must follow the FrostWire house style. See `frostwire-engineer` skill for the full spec.

### Formatting

- [ ] **Spotless check passes** — `cd desktop && ./gradlew spotlessCheck` (enforces `google-java-format`). Fix with `./gradlew spotlessApply`. Only enforced on files changed since `origin/master` (`ratchetFrom`).
- [ ] **No wildcard imports** — Spotless removes unused imports but doesn't collapse wildcards. Ensure no `import java.util.*`.
- [ ] **File header** — standard FrostWire GPL v3 header on all new files. `@author gubatron` and/or `@author aldenml` in class javadoc.
- [ ] **Logging** — `com.frostwire.util.Logger` only. Never `System.out`, `System.err`, `printStackTrace()`, SLF4J, or `java.util.logging`.
- [ ] **No comments unless asked** — code should be self-explanatory. Method names describe what they do at the caller level.
- [ ] **No @SuppressWarnings("unused")** — delete dead code, don't silence the compiler.
- [ ] **No magic numbers** — named constants for buffer sizes, port numbers, table names, DHT key prefixes.
- [ ] **Reuse before building** — `grep com.frostwire.util.*` before writing any utility.
- [ ] **Commit message format** — `[scope] imperative description (#issue)`. Scopes: `[android]`, `[desktop]`, `[common]`, `[all]`, `[test]`, `[docs]`, `[build]`.
- [ ] **One change per commit** — don't mix features, refactors, and formatting in the same commit.
- [ ] **Changelog updated** — `desktop/changelog.txt` and/or `android/changelog.txt` updated for user-facing changes. `common/` changes update BOTH.

### Git History Review

- [ ] **Commits are granular** — one logical change per commit. A branch with 35+ granular commits is normal.
- [ ] **No formatting noise mixed with product fixes** — formatting changes go in their own commit.
- [ ] **Branch is rebased, not merged** — `git fetch origin master && git rebase origin/master`. Never `git merge master` into a feature branch.
- [ ] **Force-push with lease** — `git push --force-with-lease`, never `--force`.

---

## 7. Documentation

Code must be documented well enough that a new contributor can understand it without reading the implementation.

### Checklist

- [ ] **Class javadoc** — every public class has a javadoc explaining what it does, its role in the system, and key design decisions.
- [ ] **Public method javadoc** — every public method has `@param`, `@return`, `@throws`. For non-obvious methods, include a brief explanation of the algorithm or approach.
- [ ] **Thread safety** — if a class is thread-safe, document how (e.g., "all public methods are synchronized on the internal db lock"). If not, document which thread must call it.
- [ ] **Security notes** — if a method handles untrusted input, document the sanitization performed. Example: "Sanitizes LIKE wildcards (% and _) to prevent wildcard injection from remote peer search requests."
- [ ] **Design notes** — non-obvious design decisions are documented inline. Why FTS5 with external content? Why bind to 0.0.0.0 on mobile?
- [ ] **Working examples** — where the API is non-trivial, include a code example in the javadoc:
  ```java
  /**
   * Open a local index backed by SQLite + FTS5.
   *
   * <p>Example:
   * <pre>{@code
   * AndroidLocalIndex index = AndroidLocalIndex.open(context);
   * index.upsert(torrent);
   * List<LocalSharedTorrent> results = index.search("ubuntu", 10);
   * index.close();
   * }</pre>
   */
  ```
- [ ] **Constants documented** — non-obvious constant values have a comment explaining the choice.
- [ ] **No outdated docs** — if the code changed, the docs changed too. No stale `@link` references to moved classes.

---

## 8. Testing

All new code must be tested. Bug fixes must include regression tests.

### Checklist

- [ ] **New public methods have tests** — at least one happy-path test per method.
- [ ] **Edge cases tested** — null input, empty input, max values, boundary conditions, concurrent access.
- [ ] **Security tests** — if the code handles untrusted input, write a test that proves the attack is blocked. Example: `search_percentWildcard_doesNotMatchAll`.
- [ ] **Bug fix includes regression test** — the test must FAIL without the fix and PASS with it. The test name should describe the bug: `searchLike_wildcardInjection_leaksEntireIndex`.
- [ ] **Mutation testing mindset** — not just "does the test pass" but "if I delete this line, does the test fail?" A test that passes regardless of the implementation is worthless.
- [ ] **Test isolation** — each test sets up its own state (`@Before`) and cleans up (`@After`). Tests don't depend on execution order. Each Robolectric test uses a unique DB name to avoid cross-test contamination.
- [ ] **Real fixtures over synthetic** — use actual torrent metadata, real search responses, real DHT items. Synthetic data misses the bugs that ship.
- [ ] **Integration test coverage** — unit tests cover individual methods, but does the wiring work? If you added a new component, write a test that exercises the full path (start server → send request → get response).
- [ ] **Robolectric for Android** — `@RunWith(RobolectricTestRunner.class)` with `@Config(sdk = 34)`. Be aware of Robolectric limitations (no FTS5, no native jlibtorrent) and document fallbacks.
- [ ] **Test naming** — `methodName_scenario_expectedResult` (e.g., `search_byTorrentName_returnsMatch`, `upsert_replacesExisting`).
- [ ] **Test compile check** — `./gradlew compilePlus1DebugUnitTestJavaWithJavac` (Android) or `./gradlew compileTestJava` (desktop) passes.
- [ ] **Test execution** — at least the affected test class runs and passes. Don't claim "all tests pass" without running them.

### Regression test pattern for bug fixes

```java
@Test
public void searchLike_percentWildcard_doesNotLeakEntireIndex() {
    // Reproduces wildcard injection: remote peer sends "%" and matches all torrents.
    index.upsert(makeTorrent("a001", "Alpha", 100, 1));
    index.upsert(makeTorrent("b002", "Beta", 200, 1));

    List<LocalSharedTorrent> results = index.search("%", 100);
    assertEquals("Percent wildcard must not match all torrents", 0, results.size());
}
```

---

## 9. Dependency & Build Review

When adding or changing dependencies:

### Checklist

- [ ] **License compatibility** — GPL v3 compatible? (Apache 2.0, MIT, BSD are OK. LGPL, EPL need care. GPL-incompatible = BLOCK.)
- [ ] **APK size impact** — check `./gradlew assembleDebug` before/after. Netty added ~2MB — acceptable for this feature.
- [ ] **Transitive dependency count** — run `./gradlew dependencies` to see what comes along. Avoid dependencies that pull in 50+ transitive jars.
- [ ] **CVE/advisory check** — check the dependency version against known CVEs. Use OWASP Dependency Check or manual search.
- [ ] **Actively maintained** — last commit < 1 year ago? Issues being responded to?
- [ ] **Same version on desktop and Android** — if a dependency is used on both, the version must match. (e.g., OkHttp: desktop 4.12.0, Android 5.3.2 — major version mismatch, verify API compatibility.)
- [ ] **ProGuard/R8 keep rules** — Gson-reflected classes, reflection-based code, and JNI signatures need keep rules in `proguard-rules.pro` and `multidex-config.txt`. If adding Netty, check if R8 strips needed classes.

---

## 10. Wire Protocol & Schema Compatibility

When changing serialized data that crosses process/network boundaries:

### Checklist

- [ ] **Wire protocol version** — `RemoteSearchRequest`, `RemoteSearchResponse`, `SearchPayloadCodec`: does the change break communication with older peers? Is the version field bumped? Are old versions still accepted?
- [ ] **Canonical bytes** — if `canonicalBytes()` changes, signatures from old peers will fail verification. Backward-compatible additions go at the end, never reorder fields.
- [ ] **DB schema migration** — if the SQLite schema changes: is `SCHEMA_VERSION` bumped? Does `onUpgrade()` handle the old→new path? Is the upgrade path tested? Does it preserve existing data?
- [ ] **BIP39 mnemonic compatibility** — if `IdentityKeys` serialization changes, old mnemonics must still restore correctly. Test with a known mnemonic.
- [ ] **DHT item format** — BEP 44/46 items: are they backward-compatible? Old clients receiving new-format items should ignore unknown fields, not crash.

---

## 11. Build Verification

Code must compile and tests must pass on all affected targets.

### Commands

| Target | Compile | Test | Format |
|--------|---------|------|--------|
| Desktop | `cd desktop && ./gradlew compileJava` | `cd desktop && ./gradlew test --tests "com.frostwire.search.relay.*"` | `cd desktop && ./gradlew spotlessCheck` |
| Desktop (full test) | — | `cd desktop && ./gradlew test` | — |
| Desktop (format fix) | — | — | `cd desktop && ./gradlew spotlessApply` |
| Desktop (lint) | — | — | `cd desktop && ./gradlew lint` |
| Android (compile) | `cd android && ./gradlew compilePlus1DebugJavaWithJavac` | — | — |
| Android (test compile) | `cd android && ./gradlew compilePlus1DebugUnitTestJavaWithJavac` | — | — |
| Android (test) | — | `cd android && ./gradlew testPlus1DebugUnitTest --tests "com.frostwire.android.search.*"` | — |
| IceBridge JAR | `cd desktop && ./gradlew icebridgeJar` | — | — |

### Known flaky tests (pre-existing, external service issues)

- `InternetArchiveSearchPatternTest` — archive.org timeout
- `MagnetDLSearchPatternTest` — site moved, TLS cert changed
- `TelluridePlaylistTests` — YouTube source name changed

These are external service issues, not code regressions.

---

## Review Output Format

When completing a review, produce a structured report:

```
## Code Review: [commit/PR description]

### Context
- Module(s): [desktop / android / common]
- Threat model: [trusted input / remote peer / user input / file]
- Blast radius: [desktop-only / android-only / both via common]

### Summary
[1-2 sentence summary of what the change does and whether it's ready]

### Findings

#### BLOCK
- [file:line] Description of the blocking issue and how to fix it.

#### HIGH
- [file:line] Description of the high-severity issue.

#### MEDIUM
- [file:line] Description of the medium issue.

#### LOW
- [file:line] Style nit or minor suggestion.

#### INFO
- Positive observations, things done well.

### Verification
- [ ] Compiles on [desktop/android/both]
- [ ] Tests pass on [desktop/android/both]
- [ ] Spotless check passes (`./gradlew spotlessCheck`)
- [ ] Changelog updated
- [ ] No secrets in diff
- [ ] common/ code is Android-compatible (no java.awt, java.net.http, java.sql, java.nio.file)

### Recommendation
[APPROVE / REQUEST CHANGES / BLOCK]
```

---

## Quick-Reference: Top 15 Most Common Findings

Based on the FrostWire codebase history:

1. **common/ uses desktop-only API** — `java.net.http`, `java.awt`, `java.sql`, `java.nio.file` in code that Android also compiles. BLOCK.
2. **LIKE wildcard injection** — remote peer sends `%`, matches entire local index. Escape with `ESCAPE '\\'`.
3. **No transaction in multi-step DB writes** — crash mid-write leaves DB inconsistent. Wrap in `beginTransaction()`.
4. **`close()` race condition** — close without acquiring the same lock as reads/writes.
5. **`Math.abs(Long.MIN_VALUE)` is negative** — timestamp skew bypass. Use manual sign flip.
6. **jlibtorrent on UI thread** — StrictMode violation or EDT freeze. Always background.
7. **Missing defensive `byte[].clone()`** — shared mutable state across threads.
8. **Resource leak** — Cursor/Connection not in try-with-resources.
9. **`System.out` instead of `Logger`** — against house style, no log level control.
10. **No regression test for bug fix** — the bug will come back.
11. **Secrets in logs** — auth tokens, private keys, full JSON payloads logged on error.
12. **Spotless violations** — `./gradlew spotlessCheck` fails. Fix with `./gradlew spotlessApply`.
13. **ScheduledExecutorService on Android** — dies in doze mode. Use WorkManager for periodic tasks.
14. **Memory leak via static Activity reference** — Android-specific. Use WeakReference or static inner class.
15. **Wire protocol break** — canonical bytes changed without version bump. Old peers' signatures fail.

---

## Companion Skills

- **`frostwire-engineer`** — defines the code style rules this skill enforces
- **`systematic-debugging`** — for diagnosing bugs found during review
- **`verification-before-completion`** — evidence before assertions, always
- **`mentisdb`** — record lessons learned from review findings as `LessonLearned` thoughts

---

*When a review finds a new class of bug not covered here, add it to the checklist. This skill is a living document — it improves with every review.*
