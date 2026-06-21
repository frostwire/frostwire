---
name: frostwire-code-reviewer
description: FrostWire code review skill — ensures correctness, performance, safety, security, gubatron+aldenml code style adherence, documentation quality, and regression test coverage for all new code and bug fixes in the FrostWire Desktop, Android, and common/ modules. Use when reviewing code before commit, during PR review, or when auditing existing code for issues.
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
---

# FrostWire Code Reviewer

> Systematic review framework for FrostWire code changes. Covers correctness, performance, safety, security, code style, documentation, and testing. Complements the `frostwire-engineer` skill — that skill defines the rules, this skill enforces them.

---

## When to Use This Skill

- **Before commit**: self-review your own diff
- **During PR review**: review another agent's or contributor's changes
- **Security audit**: deep-dive on code that handles untrusted input
- **Pre-release gate**: final review before tagging a release
- **Post-fix verification**: confirm a bug fix won't regress

---

## Review Process

### Step 1: Understand the Change

Before reviewing code, establish context:

1. **Read the commit message / PR description** — what is the change trying to do?
2. **Identify the threat model** — does the changed code handle untrusted input? (remote peer data, user-supplied strings, network responses, file contents, deserialized objects)
3. **Identify the blast radius** — what modules are affected? (common/ affects both desktop and Android; desktop/ is desktop-only; android/ is Android-only)
4. **Check the diff size** — large diffs (>500 lines) need sectioned review. Ask the author to split if needed.

### Step 2: Review by Category

Walk each category below in order. For each finding, assign a severity:

| Severity | Meaning | Action |
|----------|---------|--------|
| **BLOCK** | Will cause crashes, data loss, security breach, or incorrect behavior | Must fix before commit |
| **HIGH** | Likely to cause issues in production or makes future maintenance hard | Should fix before commit |
| **MEDIUM** | Code quality issue that degrades readability or maintainability | Fix in this commit or next |
| **LOW** | Style nit, minor naming, cosmetic | Mention but don't block |
| **INFO** | Observation, suggestion, or positive note | No action required |

---

## 1. Correctness

The code must do what it claims to do, handle edge cases, and not crash.

### Checklist

- [ ] **Happy path works** — does the code produce the correct result for normal input?
- [ ] **Null safety** — are all parameters, return values, and cross-thread boundaries null-checked? Especially: deserialized objects, Intent extras, JSON fields, network responses, cursor columns.
- [ ] **Empty/edge cases** — empty lists, empty strings, zero, negative numbers, max values, empty arrays, single-element collections, empty Optional.
- [ ] **Boundary conditions** — off-by-one errors, `<=` vs `<`, inclusive vs exclusive ranges, `Integer.MAX_VALUE` wrap, `Math.abs(Long.MIN_VALUE)` returns negative.
- [ ] **Exception handling** — does the code catch the right exceptions? Does it fail closed (safe default) not crashed? Are resources cleaned up in `finally` or try-with-resources?
- [ ] **Concurrency** — if the code crosses thread boundaries, is shared state protected? Are singletons thread-safe? Are volatile/atomic variables used correctly? Is the `synchronized` lock object correct?
- [ ] **Resource leaks** — are all `Cursor`, `Connection`, `InputStream`, `OutputStream`, `Reader`, `Writer`, `PreparedStatement`, `ResultSet` closed via try-with-resources? Are native handles (jlibtorrent SWIG objects) released deterministically?
- [ ] **Transaction atomicity** — if multiple DB operations must be atomic, are they wrapped in a transaction? (`db.beginTransaction()` / `setTransactionSuccessful()` / `endTransaction()`)
- [ ] **Type safety** — are byte arrays cloned on POJO boundaries? (`return bytes.clone()` on getters, `this.x = x.clone()` in constructors). Are mutable collections exposed via defensive copies?
- [ ] **Integer overflow** — `long cutoff = now - threshold` can underflow if `threshold > now`. `Math.multiplyExact` for multiplications that could overflow. Use `Integer.compareUnsigned` for sequence numbers that wrap.
- [ ] **UTF-8 handling** — are string lengths measured in bytes (for network/DB) or chars (for display)? `String.getBytes(UTF_8).length` vs `String.length()`.

### FrostWire-specific correctness checks

- [ ] **BTEngine off UI thread** — JNI calls to jlibtorrent (`BTEngine`, `SessionManager`, `Ed25519`, `TorrentInfo`) must NEVER happen on the EDT (desktop) or main thread (Android). Check for `SystemUtils.postToHandler(MISC, ...)` or `GUIMediator.safeInvokeLater()`.
- [ ] **Android lifecycle** — are Activity/Fragment/Service callbacks guarded against null/destroyed state? Is `getContext()` null-checked?
- [ ] **SharedPreferences thread safety** — `OnPreferenceChangeListener` callbacks run on the main thread. Any BTEngine/DB call inside must be dispatched to a background thread.
- [ ] **Search input sanitization** — `LocalIndex.search()` is called with keywords from remote peers (via `RelaySearchService`). All search input must be treated as untrusted. FTS5 queries must be sanitized (`sanitizeFtsQuery`). LIKE queries must escape wildcards (`sanitizeLikeQuery` with `ESCAPE '\\'`).

---

## 2. Security

Code that handles untrusted input must be hardened against malicious actors.

### Threat Model Questions

1. **Where does input come from?** — remote peer (signed request), network response (HTTP), user input (search bar), file on disk (torrent, config), DHT (untrusted)
2. **What can a malicious actor control?** — keywords in search requests, JSON payloads, HTTP response bodies, file paths, info hashes, public keys, DHT items
3. **What is the impact of misuse?** — data leak (entire local index via `%` wildcard), crash (malformed JSON), resource exhaustion (large payload), injection (SQL, FTS5, command)

### Checklist

- [ ] **SQL injection** — all queries use parameterized `?` placeholders, never string concatenation with user input. `db.rawQuery(sql, selectionArgs)` not `db.rawQuery(sql + userInput, null)`.
- [ ] **FTS5 injection** — FTS5 MATCH expressions are sanitized. Reserved words (`OR`, `AND`, `NOT`, `NEAR`) are quoted. Non-alphanumeric chars are stripped. (See `sanitizeFtsQuery` pattern.)
- [ ] **LIKE wildcard injection** — `%` and `_` in user input are escaped with `ESCAPE '\\'`. Without this, a remote peer can send `%` to match the entire local index in one query.
- [ ] **Path traversal** — file paths from untrusted sources are validated. No `../../etc/passwd`. Use `File.getCanonicalPath()` and verify it starts with the allowed root.
- [ ] **Deserialization** — JSON parsing is wrapped in try-catch. Malformed JSON returns empty/null, not crash. `JsonParser.parseString()` can throw on any input.
- [ ] **Signature verification** — remote requests are Ed25519-signed. The signature is verified BEFORE processing. Timestamp skew is checked (anti-replay). Rate limiting is per-source.
- [ ] **Auth token** — control API endpoints (except `/health`) require `X-IceBridge-Token` header. The token is generated server-side, not client-supplied.
- [ ] **Input length caps** — all untrusted inputs are length-capped. Keywords ≤256 chars. JSON payloads ≤16MB. File lists ≤10,000 entries. Prevents resource exhaustion.
- [ ] **No secrets in logs** — private keys, auth tokens, passwords, BIP39 mnemonics are NEVER logged. `LOG.warn("failed: " + json)` is dangerous if json contains sensitive data. Truncate to 200 chars.
- [ ] **No secrets in commits** — `git diff --cached` reviewed before commit. No `.pem`, `.key`, `.p12`, `.crt` files. No hardcoded API keys or passwords.
- [ ] **Integer overflow as attack** — `Math.abs(Long.MIN_VALUE)` is negative. Attackers can craft timestamps that bypass skew checks. Use manual sign flip: `long diff = a - b; long abs = diff >= 0 ? diff : -diff;`
- [ ] **Rate limiting** — per-source rate limiting on all incoming peer requests. Sliding window. (See `IncomingSearchRequestHandler.MAX_REQUESTS_PER_MINUTE`.)

---

## 3. Performance

The code must not degrade the user experience or waste system resources.

### Checklist

- [ ] **No O(n²) on hot paths** — search results, UI lists, peer directories. If iterating a collection inside another iteration, consider a Set/Map lookup instead.
- [ ] **No DB calls on UI thread** — all `SQLiteDatabase` operations are on background threads. `synchronized(db)` blocks must be short.
- [ ] **No network on UI thread** — all HTTP, rUDP, DHT operations are off the main thread.
- [ ] **Batch DB operations** — multiple INSERTs use `beginTransaction()/endTransaction()` not individual auto-commits. 10,000 files = 1 transaction, not 10,000 auto-commits.
- [ ] **Cursor management** — Cursors are closed via try-with-resources. Large result sets are paginated (`LIMIT`).
- [ ] **Memory bounds** — collections from untrusted sources are capped (`MAX_FILES_PER_TORRENT = 10_000`). Response bodies are size-capped (16MB string, 32MB bytes). Inbound message queues have eviction.
- [ ] **Thread pool sizing** — daemon threads are marked `setDaemon(true)`. Thread pools are sized appropriately. Named threads for debugging (`new Thread(r, "icebridge-transport-poller")`).
- [ ] **Lazy initialization** — expensive resources are loaded on first use, not at startup. (See `IdentityKeys.loadOrCreate` pattern.)
- [ ] **Static final for constants** — regex patterns, Gson instances, Logger instances are `private static final`, not created per-call.
- [ ] **Avoid unnecessary copies** — `byte[].clone()` on POJO boundaries is required for safety, but don't clone in hot loops. Use `System.arraycopy` for known-size copies.
- [ ] **Connection reuse** — OkHttp `OkHttpClient` instances are reused (they have connection pools). Don't create a new client per request.

### FrostWire-specific performance checks

- [ ] **BTEngine calls are batched** — don't call `BTEngine.getInstance().swig()` in a loop. Cache the reference.
- [ ] **jlibtorrent Ed25519 over JDK** — `IdentityKeys.generate()` uses `com.frostwire.jlibtorrent.Ed25519.createKeypair(seed)` (native, 50-100x faster than JDK `KeyPairGenerator`).
- [ ] **WorkManager over ScheduledExecutorService on Android** — periodic tasks use WorkManager to respect doze mode and background limits. (Phase 3-4 consideration.)

---

## 4. Safety

The code must fail gracefully, not crash the app or corrupt data.

### Checklist

- [ ] **Fail closed, not crashed** — native code, deserialization, network calls are wrapped in try-catch. The app falls back to a safe default, never crashes on every startup. (See FrostWire mantra: "Fail closed, not crashed")
- [ ] **Defensive copies** — mutable fields (`byte[]`, `List`) are cloned on input and output of immutable POJOs.
- [ ] **Race conditions** — `close()` methods acquire the same lock as read/write methods. Double-checked `open` flag. No "set flag then close without lock" patterns.
- [ ] **Shutdown ordering** — components are shut down in reverse order of startup. Listeners are removed before transports are closed. Transports are closed before servers.
- [ ] **Thread interruption** — long-running loops check `Thread.interrupted()` or use `ScheduledExecutorService.shutdownNow()`.
- [ ] **Native init wrapped** — `try/catch` around all native initialization (Python, ffmpeg, jlibtorrent `.so` load). Corrupted native binaries on user devices are a real-world occurrence.
- [ ] **Synchronous cleanup before async** — if state is cleaned up and then an async callback fires, the callback must see the cleaned state, not stale data. Do cleanup BEFORE posting the async notification.

---

## 5. Code Style (gubatron + aldenml)

The code must follow the FrostWire house style. See `frostwire-engineer` skill for the full spec.

### Checklist

- [ ] **File header** — standard FrostWire GPL v3 header on all new files. `@author gubatron` and/or `@author aldenml` in class javadoc.
- [ ] **Package placement** — common code in `com.frostwire.*`, desktop UI in `com.limegroup.gnutella.*`, Android in `com.frostwire.android.*`. No misplaced classes.
- [ ] **Imports** — no wildcard imports. Unused imports removed in the same commit.
- [ ] **Logging** — `com.frostwire.util.Logger` only. Never `System.out`, `System.err`, `printStackTrace()`, SLF4J, or `java.util.logging`. Throwables passed as second arg: `LOG.warn("msg", e)`.
- [ ] **No comments unless asked** — code should be self-explanatory. Method names describe what they do at the caller level.
- [ ] **No @SuppressWarnings("unused")** — delete dead code, don't silence the compiler.
- [ ] **No magic numbers** — named constants for buffer sizes, port numbers, table names, DHT key prefixes.
- [ ] **Reuse before building** — `grep com.frostwire.util.*` before writing any utility. `Hex.encode`, `Hex.decode`, `UrlUtils`, `StringUtils`, `HttpClientFactory`, `ThreadPool`, `TaskThrottle` already exist.
- [ ] **Minimum scope** — local variables over fields, private over public, final where possible.
- [ ] **Composition over inheritance** — wrap, don't extend non-abstract classes.
- [ ] **Commit message format** — `[scope] imperative description (#issue)`. Scopes: `[android]`, `[desktop]`, `[common]`, `[all]`, `[test]`, `[docs]`, `[build]`.
- [ ] **One change per commit** — don't mix features, refactors, and formatting in the same commit.
- [ ] **Changelog updated** — `desktop/changelog.txt` and/or `android/changelog.txt` updated for user-facing changes. Common/ changes update BOTH.

---

## 6. Documentation

Code must be documented well enough that a new contributor can understand it without reading the implementation.

### Checklist

- [ ] **Class javadoc** — every public class has a javadoc explaining what it does, its role in the system, and key design decisions. One paragraph is enough for simple classes; complex classes get more.
- [ ] **Public method javadoc** — every public method has `@param`, `@return`, `@throws`. For non-obvious methods, include a brief explanation of the algorithm or approach.
- [ ] **Thread safety** — if a class is thread-safe, document how (e.g., "all public methods are synchronized on the internal db lock"). If not, document which thread must call it.
- [ ] **Security notes** — if a method handles untrusted input, document the sanitization performed. Example: "Sanitizes LIKE wildcards (% and _) to prevent wildcard injection from remote peer search requests."
- [ ] **Design notes** — non-obvious design decisions are documented inline. Why FTS5 with external content? Why manual FTS index management instead of triggers? Why bind to 0.0.0.0 on mobile?
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
- [ ] **Constants documented** — non-obvious constant values have a comment explaining the choice. `MAX_FILES_PER_TORRENT = 10_000` → "Prevents unbounded row growth for torrents with tens of thousands of files."
- [ ] **No outdated docs** — if the code changed, the docs changed too. No stale `@link` references to moved classes.

---

## 7. Testing

All new code must be tested. Bug fixes must include regression tests.

### Checklist

- [ ] **New public methods have tests** — at least one happy-path test per method.
- [ ] **Edge cases tested** — null input, empty input, max values, boundary conditions, concurrent access.
- [ ] **Security tests** — if the code handles untrusted input, write a test that proves the attack is blocked. Example: `search_percentWildcard_doesNotMatchAll`.
- [ ] **Bug fix includes regression test** — the test must FAIL without the fix and PASS with it. The test name should describe the bug: `searchLike_wildcardInjection_leaksEntireIndex`.
- [ ] **Test isolation** — each test sets up its own state (`@Before`) and cleans up (`@After`). Tests don't depend on execution order.
- [ ] **Real fixtures over synthetic** — use actual torrent metadata, real search responses, real DHT items. Synthetic data misses the bugs that ship.
- [ ] **Robolectric for Android** — `@RunWith(RobolectricTestRunner.class)` with `@Config(sdk = 34)`. Be aware of Robolectric limitations (no FTS5, no native jlibtorrent) and document fallbacks.
- [ ] **Test compile check** — `./gradlew compilePlus1DebugUnitTestJavaWithJavac` (Android) or `./gradlew compileTestJava` (desktop) passes.
- [ ] **Test execution** — at least the affected test class runs and passes. Don't claim "all tests pass" without running them.
- [ ] **Test naming** — `methodName_scenario_expectedResult` (e.g., `search_byTorrentName_returnsMatch`, `upsert_replacesExisting`).

### Regression test pattern for bug fixes

```java
@Test
public void searchLike_percentWildcard_doesNotLeakEntireIndex() {
    // This test reproduces a wildcard injection vulnerability where
    // a remote peer sends "%" as search keywords and matches all torrents.
    index.upsert(makeTorrent("a001", "Alpha", 100, 1));
    index.upsert(makeTorrent("b002", "Beta", 200, 1));
    index.upsert(makeTorrent("c003", "Gamma", 300, 1));

    List<LocalSharedTorrent> results = index.search("%", 100);
    assertEquals("Percent wildcard must not match all torrents", 0, results.size());
}
```

---

## 8. Build Verification

Code must compile and tests must pass on all affected targets.

### Commands

| Target | Compile | Test |
|--------|---------|------|
| Desktop | `cd desktop && ./gradlew compileJava` | `cd desktop && ./gradlew test --tests "com.frostwire.search.relay.*"` |
| Desktop (full) | — | `cd desktop && ./gradlew test` |
| Android (compile) | `cd android && ./gradlew compilePlus1DebugJavaWithJavac` | — |
| Android (test) | `cd android && ./gradlew compilePlus1DebugUnitTestJavaWithJavac` | `cd android && ./gradlew testPlus1DebugUnitTest --tests "com.frostwire.android.search.*"` |
| IceBridge JAR | `cd desktop && ./gradlew icebridgeJar` | — |

### Known flaky tests (pre-existing, not our fault)

- `InternetArchiveSearchPatternTest` — archive.org timeout
- `MagnetDLSearchPatternTest` — site moved, TLS cert changed
- `TelluridePlaylistTests` — YouTube source name changed

These are external service issues, not code regressions.

---

## Review Output Format

When completing a review, produce a structured report:

```
## Code Review: [commit/PR description]

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
- [ ] Changelog updated
- [ ] No secrets in diff

### Recommendation
[APPROVE / REQUEST CHANGES / BLOCK]
```

---

## Quick-Reference: Top 10 Most Common Findings

Based on the FrostWire codebase history:

1. **LIKE wildcard injection** — remote peer sends `%`, matches entire local index. Escape with `ESCAPE '\\'`.
2. **No transaction in multi-step DB writes** — crash mid-write leaves DB inconsistent. Wrap in `beginTransaction()`.
3. **`close()` race condition** — close without acquiring the same lock as reads/writes.
4. **`Math.abs(Long.MIN_VALUE)` is negative** — timestamp skew bypass. Use manual sign flip.
5. **jlibtorrent on UI thread** — StrictMode violation or EDT freeze. Always background.
6. **Missing defensive `byte[].clone()`** — shared mutable state across threads.
7. **Resource leak** — Cursor/Connection not in try-with-resources.
8. **`System.out` instead of `Logger`** — against house style, no log level control.
9. **No regression test for bug fix** — the bug will come back.
10. **Secrets in logs** — auth tokens, private keys, full JSON payloads logged on error.

---

## Companion Skills

- **`frostwire-engineer`** — defines the code style rules this skill enforces
- **`systematic-debugging`** — for diagnosing bugs found during review
- **`verification-before-completion`** — evidence before assertions, always
- **`mentisdb`** — record lessons learned from review findings as `LessonLearned` thoughts

---

*When a review finds a new class of bug not covered here, add it to the checklist. This skill is a living document — it improves with every review.*
