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
| `View.setTag(int, …)` keys | Must be **application resource ids** — `generateViewId()` keys throw `IllegalArgumentException` |
| Base `strings.xml` keys | Must be copied to **all** `values-*` locales in the same commit (parity test gates CI) |

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

### Deploy & Artifact Verification

- [ ] **"up-to-date" ≠ latest commit** — gradle up-to-date only means the artifact matches that checkout's sources. Verify the deploy host's `git rev-parse HEAD` and the artifact's own version banner (e.g. IceBridge `software version = 1.1.0` line); a missing banner block once proved a stale jar despite a "successful" build (MentisDB #896).
- [ ] **Suite width matches change width** — for `common/` constants, shared helpers, or wire behavior, require the FULL module suite, not only the package suite. Deterministic failures shipped to CI because only the relay package ran (MentisDB #902).
- [ ] **Constant/label changes audit** — when a public constant value or user-visible label changes, grep every test asserting the old value before approving.

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

## Quick-Reference: Top 30 Most Common Findings

Based on the FrostWire codebase history + MentisDB frostwire chain lessons:

1. **common/ uses desktop-only API** — `java.net.http`, `java.awt`, `java.sql`, `java.nio.file` in code that Android also compiles. BLOCK. Known offenders to re-check: `IdentityKeys`, `IceBridgeTokens`, `IceBridgeHostCache` (`Files`).
2. **LIKE wildcard injection** — remote peer sends `%`, matches entire local index. Escape with `ESCAPE '\\'`.
3. **No transaction in multi-step DB writes** — crash mid-write leaves DB inconsistent. Wrap in `beginTransaction()`.
4. **`close()` race condition** — close without acquiring the same lock as reads/writes.
5. **`Math.abs(Long.MIN_VALUE)` is negative** — timestamp skew bypass. Use manual sign flip.
6. **jlibtorrent on UI thread** — StrictMode violation or EDT freeze. Always background.
7. **Missing defensive `byte[].clone()`** — shared mutable state across threads.
8. **Resource leak** — Cursor/Connection not in try-with-resources.
9. **`System.out` instead of `Logger`** — against house style (except intentional headless CLI UX in `IceBridgeServer.main`).
10. **No regression test for bug fix** — the bug will come back.
11. **Secrets in logs** — auth tokens, private keys, full JSON payloads logged on error. Use `[set]` / never print tokens after generate-token path is done.
12. **Spotless violations** — `./gradlew spotlessCheck` fails. Fix with `./gradlew spotlessApply`.
13. **ScheduledExecutorService on Android** — dies in doze mode. Use WorkManager for periodic tasks.
14. **Memory leak via static Activity reference** — Android-specific. Use WeakReference or static inner class.
15. **Wire protocol break** — canonical bytes changed without version bump / without keeping old decode. IdentityRecord v3 write must still **read** v1/v2.
16. **Self-discovery / co-located ports** — PeerDiscovery must skip own Ed25519 pub + loopback; configurable ports for co-located standalone.
17. **Async transport race** — register response listeners *before* send; async fakes must deliver off-thread.
18. **rUDP without app-level fragmentation** — IP frags drop whole datagrams; use DATA_FRAG/DATA_END + byte-equal reassembly tests.
19. **ProcessBuilder.inheritIO under Gradle** — child blocks on full pipes; redirect to temp files. Tests use `IdentityKeys.generate(0)`, never PoW 20.
20. **App multi-hop re-sign vs requester verify** — re-signing with forwarder key while verify checks `requesterPub` always fails. Dual-envelope (sig over query-only) is the fix; flag re-sign paths as **BLOCK**.
21. **HELLO_ACK without identity** — initiator `remotePub` stays null; mesh RELAY sourcePub checks fail one way. ACK must be signed HELLO-shaped + `setRemotePub` (MentisDB #875).
22. **Dual `connect()` overwrite** — second initiator session replaces authenticated responder session on `sessionsByAddress`. `connect` must reuse (MentisDB #875).
23. **Unauthenticated RELAY_RESPONSE** — spoofable sourcePub into inbound queue / amp. Session + rate limit required; local clients use `/poll` delivery (MentisDB #876).
24. **Mesh flood without bounds** — fan-out × hop TTL × payload without `MAX_APP_PAYLOAD`, rate limit, or TTL cap is amplification **BLOCK**.
25. **DHT list ConcurrentModificationException** — never iterate live jlibtorrent `dhtGetPeers` lists; snapshot first.
26. **TTL boundary early-return** — shared clamp + `newTtl<=0 → drop` kills ttl=1 forwards; old contract forwards once with ttl=0 and lets the next hop's guard stop it. Boundary values are the contract (MentisDB #902).
27. **Constant/label change with stale assertions** — `MAX_FORWARD_TARGETS` 3→30, `"Local"`→`"Local (test)"`; grep tests for the old value before push (MentisDB #902).
28. **Test extraction blind spot** — greedy regex `[^>]*` swallows the `/` of self-closing XML tags and hides keys from parity checks; use `[^>]*?` and test the extractor (MentisDB #902).
29. **Singleton leakage across test classes** — process-wide topology/settings mutated by one class, asserted by another; reset in `@BeforeEach`/`@AfterEach` or CI becomes order-dependent.
30. **Benchmark ranking without CRN** — per-candidate seeds = each candidate on a different random network; the winner is seed luck (±30pp rare-hit swings on identical configs). Common random numbers, or replications with confidence intervals (MentisDB #903).

---

## 12. Distributed Relay Network / IceBridge Review (MANDATORY for relay code)

Use this section whenever the change touches `common/.../search/relay/**`, IceBridge, DHT advertiser/discovery, karma, or distributed search wiring on desktop/Android.

### Architecture truth (hybrid model)

| Plane | Mechanism | Role |
|-------|-----------|------|
| Identity / bootstrap | Direct TCP (`IncomingRelayServer`, port default 6888) + BEP 46 `IdentityRecord` | Learn real Ed25519 pub + rudpPort + role |
| Discovery | BEP 5 topics `frostwire-peers-v1`, `frostwire-relays-v1`, `frostwire-bootstrap-v1` | Multi-writer rendezvous only |
| Data / search transport | IceBridge rUDP mesh + HTTP control API | Opaque payload routing for signed search messages |
| Search application | `DistributedSearchPerformer` + `RelaySearchService` + `SearchResponseVerifier` | Sign/verify keywords & results |
| Trust | `PeerDirectory.topByTrustVerified` + karma chains | Never query placeholder `SHA-256(host:port)` peers for search |

Design source of truth: repo root `DESIGN_RELAY_REGISTRY.md`. MentisDB chain: `frostwire` (agent_id `gubatron` for durable writes).

### Checklist — Identity & discovery

- [ ] **Own-pub self-skip** — `PeerDiscovery` receives `ownEd25519Pub`; after identity verify, skip if pub matches own (MentisDB #831).
- [ ] **Loopback skip** — `isLocalEndpoint` rejects 127.0.0.1 / localhost / ::1 before TCP auth.
- [ ] **Verified-only search** — `DistributedSearchPerformer` uses `topByTrustVerified`, never raw `topByTrust` / placeholders.
- [ ] **IdentityRecord v2** — `rudp_port` + `role`; v1 compat (`rudpPort==0` → fallback 6889).
- [ ] **DHT topics** — peers announce peers topic; FORWARDER/BOTH (or auto-elect when connectable) announce relays; discovery prefers relays first (no `<10 peers` gate — MentisDB #832).
- [ ] **Placeholder policy** — SHA-256(host:port) allowed only as temporary directory keys; never for trust scoring or search targets.
- [ ] **Identity file path** — desktop: `CommonUtils.getUserSettingsDir()/libtorrent/identity.dat` (not settings dir alone). Import/export/restore must use the same path as `Initializer`.

### Checklist — IceBridge transport

- [ ] **Control auth** — non-`/health` endpoints require bearer token; multi-token file; no token in logs; `--generate-token` prints once.
- [ ] **Client HTTP** — OkHttp in `common/` (not `java.net.http`). Defensive copies on `InboundMessage`; response size caps; `close()`.
- [ ] **Poller model** — single shared `IceBridgeSearchTransport` poller fans out to permanent `IncomingSearchRequestHandler` + transient performer listeners (no dual-poll race).
- [ ] **Listener-before-send** — performer registers listener + latch before any `transport.send` (MentisDB #809).
- [ ] **rUDP fragmentation** — payloads > `MAX_FRAGMENT_PAYLOAD` (1024) split into DATA_FRAG/DATA_END; reassembly byte-equal tests; max assembled size / concurrent groups bounded (MentisDB #810).
- [ ] **Hole punch / connectivity** — unsolicited HELLO marks connectable; hole-punch response parses host:port and `connect()`; auto-elect forwarder when connectable.
- [ ] **PeerRegistrySync** — uses peer `rudpPort` from directory, not hardcoded 6889.
- [ ] **Process launcher** — no `inheritIO()`; redirect stdout/stderr to temp files; pass `--relay-port` and configurable rUDP; health wait with process-alive check.
- [ ] **Local vs remote** — settings: ENABLE, USE_REMOTE, URL, token, bind host, ports, role. Structured config dump at startup without secrets (MentisDB #837).
- [ ] **Endpoint ownership** — for each advertised `host:rudpPort`, name the process that binds it in every local/remote mode. A remote HTTP client alone cannot receive rUDP; reject configurations that advertise an endpoint with no listener.
- [ ] **Control/data-plane alignment** — a peer registered through one control API is routable only by the rUDP server behind that same registry. Verify Android, desktop, and forwarder use the intended registry rather than independent local registries.
- [ ] **Relay frame reality** — `sendRelay` must serialize `RudpPacket.Type.RELAY`, and the forwarder must receive that type before emitting `RELAY_RESPONSE`. A relay-shaped payload inside `DATA` is not relay fallback.
- [ ] **Bidirectional route** — test request and response independently through the exact topology. Direct delivery to a known endpoint and fallback relay delivery for an unknown/NATed endpoint are separate cases.
- [ ] **Delivery semantics** — `/send` returning HTTP success must mean documented queue acceptance only, unless there is an authenticated acknowledgement from the destination. Callers must not count it as a delivered request without an application response.
- [ ] **CLI System.out** — allowed only in `IceBridgeServer.main` / help / generate-token; library paths use `Logger`.

### Checklist — rUDP session auth & multi-hop RELAY (BLOCK-class if violated)

MentisDB frostwire **#873–#876**. Review every change to `RudpSessionManager` / `RelayFrame` / HELLO path against this list.

- [ ] **HELLO_ACK proves responder** — ACK payload is HELLO-shaped (pub + ts + sig over connectionId||ts). Initiator `handleHelloAck` must `setRemotePub`. Empty ACK → initiator `remotePub` stays null forever → **HIGH/BLOCK** for mesh RELAY.
- [ ] **No dual-session overwrite** — `connect(addr)` reuses existing `sessionsByAddress` entry. Bidirectional warm that creates a second initiator session and overwrites authenticated `remotePub` is a **BLOCK** correctness bug.
- [ ] **RELAY sourcePub = hop peer** — `frame.sourcePub` must equal `session.remotePub()` for the authenticated sender. Prefer `sessionsByRemoteId.get(connectionId)` then address map. sendRelay rewrites source to **this** node's identity each hop.
- [ ] **RELAY_RESPONSE authenticated** — session required; rate-limited; attribute to `session.remotePub()`, **never** spoofable header bytes. Unauthenticated `write(RELAY_RESPONSE)` fire-and-forget is **BLOCK** (amp + queue injection).
- [ ] **Local registry /poll delivery** — client registered on this node's own rUDP host:port → `notifyListener` / local inbound queue, not self-UDP RELAY_RESPONSE loop.
- [ ] **Amplification bounds all present** —
  - `RelayFrame.MAX_APP_PAYLOAD` enforced on encode **and** decode
  - hop TTL default ≤ 3; mesh fan-out ≤ 3
  - per-peer RELAY / RELAY_RESPONSE rate limit
- [ ] **No RELAY fragmentation claim** — if app payload can exceed one fragment, either reject (current policy) or implement frag for RELAY; do not silently fail multi-hop for large search frames.
- [ ] **Auth change ⇒ re-run multi-hop E2E** — after removing unauthenticated paths, `MultiRelayMeshSearchTest` (or 3-forwarder equivalent) must still pass with session warm + topology assert.

### Checklist — Search protocol correctness

- [ ] **Request verify** — Ed25519 over canonical bytes; timestamp skew; rate limit by **requesterPub**; fail-closed `Optional.empty()`.
- [ ] **Response verify** — client checks expected responder pub, nonce, skew, signature (`SearchResponseVerifier`).
- [ ] **TTL / multi-hop policy (app layer)** — dual-envelope is shipped (`RemoteSearchRequest` v2: signature over query-only; hop fields mutable). **BLOCK** any re-sign-with-forwarder-key that still verifies against `requesterPub`. Preserve original requester signature on hop.
- [ ] **TTL / multi-hop policy (transport layer)** — mesh `Type.RELAY` is separate from app ttl. Transport hop auth rules above still apply even when app dual-envelope is correct.
- [ ] **TTL boundary contract** — a request with ttl=1 must be forwarded ONCE with ttl=0; the next hop's `ttl>0` guard stops it. `clampRemainingTtl(...) == 0` is a valid forward, not exhaustion — an early-return on `newTtl<=0` silently kills single-hop forwarding (MentisDB #902).
- [ ] **Catalog browse** — signature + skew verified; desktop wiring must pass `LocalIndex` if feature is claimed; Android already passes index.
- [ ] **FTS5 fixtures** — whole-word match; no kebab-case only names; sanitizer strips non-alnum.
- [ ] **Trust check on requester** — spam/trust floor evaluated in *target's* directory for the *requester*, not the target (MentisDB #798).
- [ ] **Canonical path bytes** — `pathLengthBytes()` includes count + each length-prefixed hop; non-empty path signatures must verify.

### Checklist — Karma

- [ ] Load chain from store on writer construct (no genesis reset every launch).
- [ ] Epoch commitment before endorsements; energy budget enforced in verify.
- [ ] WoT trust is **BFS with hop decay**, not recursive double-count (MentisDB #795).
- [ ] BTEngineListener heavy work offloaded via `ThreadExecutor` / chain with dedup (`BTEngineListenerChain`).

### Checklist — Cross-platform module placement

- [ ] MCP Streamable-HTTP / `com.sun.net.httpserver` / virtual threads / BouncyCastle → **desktop only** (`com.frostwire.mcp.desktop.transport`).
- [ ] IceBridge process launcher → desktop only.
- [ ] Android in-process `IceBridgeServer` + OkHttp client in common; no subprocess.
- [ ] JDBC (`LocalIndexTable`, `KarmaChainTable`) → desktop only; Android has `AndroidLocalIndex` / `AndroidKarmaChainStore`.
- [ ] Prefer abstracting `Files` usage behind injected `File` / streams in common so Android never depends on `java.nio.file`.

### Checklist — Tests required for IceBridge/relay changes

| Area | Minimum tests |
|------|----------------|
| Peer discovery | self-skip by pub, local endpoint skip, verified upsert, unauth drop, custom relay port; snapshot DHT endpoint lists before iterate (no CME) |
| Transport | listener-before-send race, multi-listener fanout, poll failure isolation |
| rUDP | fragment reassembly byte equality (pattern `i % 256`), oversize reject, stale group eviction |
| rUDP session | HELLO_ACK sets initiator `remotePub`; connect reuse does not drop auth; RELAY rejected without session / wrong sourcePub |
| RELAY_RESPONSE | unauthenticated sender dropped; authenticated path delivers; no spoofed header attribution |
| RelayFrame | encode/decode round-trip; reject app payload > `MAX_APP_PAYLOAD`; hop TTL clamp |
| Multi-hop mesh E2E | ≥3 FORWARDER + seeder/searcher on different homes; warm HELLO/HELLO_ACK; assert seeder **absent** from searcher home registry; signed search hit arrives (`MultiRelayMeshSearchTest`) |
| Three-node topology | Android requester to desktop index node via cloud forwarder; request and signed response both arrive; assert packet type at forwarder and no LocalIndex use by FORWARDER |
| Search | signed request/response round-trip, bad sig/nonce/stale reject; dual-envelope hop preserves requester sig |
| Wire versions | IdentityRecord v1/v2 still decode when writing v3; RemoteSearchResponse version covers chunk/final domain |
| Multi-instance | publish → find → unpublish → not-find (TCP or IceBridge fake transport) |
| Process launcher | redirect IO (not inheritIO), health check, `--relay-port` parse |
| Identity tests | always `IdentityKeys.generate(0)` |
| MCP (desktop) | SSE GET body empty + session header; `initialized` → JSON `"accepted"` |

### MentisDB operating rule for reviewers / implementers

1. Chain: `frostwire`. Prefer agent_id **`gubatron`** for durable project memory (do not invent new agent IDs).
2. `ranked_search` before append; link `refs` to prior lessons (#809 async, #810 frag, #831 self-skip, #832 ports, #795 karma, #769 architecture, **#875 dual-session/HELLO_ACK**, **#876 RELAY bounds**).
3. On any non-obvious fix: `LessonLearned` / `Constraint` immediately; fold durable rules into this skill + `frostwire-engineer` in the same change set when possible.
4. Checkpoint before handoff/compaction.
5. After skill updates: granular `[all]` commits; do not mix skill edits with product code unless the user asked for a single docs+code commit.

---

## Companion Skills

- **`frostwire-engineer`** — defines the code style rules this skill enforces
- **`systematic-debugging`** — for diagnosing bugs found during review
- **`verification-before-completion`** — evidence before assertions, always
- **`mentisdb`** — record lessons learned from review findings as `LessonLearned` thoughts on the `frostwire` chain

---

*When a review finds a new class of bug not covered here, add it to the checklist. This skill is a living document — it improves with every review. Last major expansion: TTL boundary contract, constant-change audit, test extraction blind spots, singleton test isolation, CRN benchmarks, deploy artifact verification (MentisDB #896–#903, 2026-07-20).*
