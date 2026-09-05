---
name: frostwire-code-reviewer
description: "FrostWire code review skill — ensures correctness, performance, safety, security, gubatron+aldenml code style adherence, documentation quality, and regression test coverage for all new code and bug fixes in the FrostWire Desktop, Android, and common/ modules. Context-aware: applies different checks depending on whether code targets desktop, Android, or common. Enforces common/ JDK compatibility with Android (the limiting factor). Use when reviewing code before commit, during PR review, or when auditing existing code for issues."
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

Review current source and real call paths, not a historical bug list. A review request is read-only unless fixes are authorized. Historical examples below retain their diagnostic context, not a claim that each defect is still present.

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

Verify these values against current Gradle source/target, minSdk, desugaring, dependency declarations/resolution, and R8 configuration. Language support, runtime API availability, and repository policy are separate checks; version examples are not permanent pins.

### Module: `common/`

**Compiled by BOTH desktop and Android.** This is the most restrictive target.

| Constraint | Value | Why |
|------------|-------|-----|
| Java source/target | **17** (Android's `sourceCompatibility`) | Android build.gradle sets VERSION_17 |
| `java.net.http.*` | **FORBIDDEN** | Desktop HTTP client API; do not assume Android core library desugaring supplies it |
| `java.awt.*`, `javax.swing.*` | **FORBIDDEN** | Desktop-only APIs |
| `java.nio.file.*` (Path, Files) | **FORBIDDEN by shared-code policy** | Path/Files are available on Android API 26; use injected File/streams here by repository policy, not because the APIs are absent |
| `ProcessBuilder` | **AVOID** | Android can't spawn JVM subprocesses |
| `java.sql.*` (JDBC) | **FORBIDDEN by shared-code policy** | Desktop JDBC implementations do not provide Android's `android.database.sqlite` backend |
| `System.getProperty("user.home")` | **FORBIDDEN** | Not a portable app-storage contract on Android; use injected `File` paths |
| `ScheduledExecutorService` | **OK but flag for Android** | In-process scheduling does not survive process death or bypass Doze/background restrictions; use WorkManager for eligible deferrable persistent work |
| OkHttp | **OK** | Available on both desktop (4.12.0) and Android (5.3.2); verify shared API compatibility |
| Gson | **OK** | Available on both |
| jlibtorrent | **OK** | Available on both |
| Netty | **OK** | Available on both (desktop 4.2.0.Final, Android same); verify current resolution |
| BouncyCastle | **Available on both; verify artifact/API** | Android declares `bcprov-jdk18on:1.83`; desktop declares `bcpkix-jdk18on:1.83`. Provider availability does not imply every PKIX API is shared |

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
| Background tasks | **WorkManager** for eligible deferrable persistent work; in-process executors do not provide durable scheduling or bypass background limits |
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
5. **Trace production wiring** — establish revision, requested scope, entry points, supported roles, callers, serializers, persistence, teardown, and tests, not only changed methods.
6. **Check shared-brain ownership** — search `frostwire` as existing agent `gubatron` for related findings, contracts, and file claims. Apply the operating rules below before edits or new findings; distinguish resolved, latent, conditional, and current defects.

---

## Step 2: Review by Category

Walk each category. For each finding, assign a severity:

| Severity | Meaning | Action |
|----------|---------|--------|
| **BLOCK** | Demonstrated contract violation with release-blocking crash, data-loss, security, privacy, or core correctness impact | Must fix before approval |
| **HIGH** | Credible production failure, availability risk, serious regression, or maintenance hazard | Should fix before release or explicitly resolve risk |
| **MEDIUM** | Bounded correctness or code quality issue that degrades readability or maintainability | Fix in this commit or schedule a concrete follow-up |
| **LOW** | Style nit, minor naming, cosmetic | Mention but don't block |
| **INFO** | Observation, suggestion, or positive note | No action required |

Give each finding `path:line`, trigger/preconditions, violated invariant, user impact, minimal remedy, and defensive validation. Severity follows impact and reachability, not dramatic wording. Label evidence as source-confirmed, locally observed, measured, estimated, or conditional; operation counts are not measured latency. A source-confirmed defect need not be exercised against a live service.

---

## 1. Cross-Platform Compatibility

**Most important for `common/` changes.** If code is in `common/`, it must compile and run on Android.

### Checklist

- [ ] **No desktop-only or policy-forbidden imports** — grep the diff for `import java.awt`, `import javax.swing`, `import java.net.http`, `import java.sql`, `import java.nio.file`. If found in `common/`, BLOCK unless an explicit repository policy exception is approved; Files/Path availability on API 26 is not the reason for the policy.
- [ ] **No `System.getProperty("user.home")`** in `common/` — it is not Android app storage. Use injected `File` paths; resolve `Context.getFilesDir()` in Android-specific code.
- [ ] **No JDBC in `common/`** — `java.sql.Connection`, `DriverManager`, `PreparedStatement`, `ResultSet` must not couple shared code to desktop JDBC implementations. Android uses `android.database.sqlite.SQLiteDatabase`. Abstract behind an interface (like `LocalIndex`).
- [ ] **Java 17 compatible** — sealed classes are a Java 17 language feature, not automatically forbidden. Record patterns and pattern-matching switch are not standard Java 17 features. Separately verify Android runtime/desugaring support, including `java.util.random.RandomGenerator`; use established `Random`/`SecureRandom` APIs where needed.
- [ ] **No `ProcessBuilder` in `common/`** — Android can't spawn JVM subprocesses. If a feature needs subprocess launch, put the launcher in `desktop/` only.
- [ ] **OkHttp over `java.net.http`** — when writing HTTP client code in `common/`, use OkHttp (available on both platforms), not `java.net.http.HttpClient` (Java 11+ desktop only).
- [ ] **Android `minSdk` = 26** — all `Build.VERSION.SDK_INT < 26` guards are dead code. Don't add them.
- [ ] **Test on both targets** — coordinator runs `./gradlew compileJava` from `desktop/` AND `./gradlew compilePlus1DebugJavaWithJavac` from `android/`; compilation alone is not runtime verification.

---

## 2. Correctness

### Checklist

- [ ] **Happy path works** — does the code produce the correct result for normal input?
- [ ] **Null safety** — are all parameters, return values, and cross-thread boundaries null-checked? Especially: deserialized objects, Intent extras, JSON fields, network responses, cursor columns.
- [ ] **Empty/edge cases** — empty lists, empty strings, zero, negative numbers, max values, empty arrays, single-element collections, empty Optional.
- [ ] **Boundary conditions** — off-by-one errors, `<=` vs `<`, inclusive vs exclusive ranges, `Integer.MAX_VALUE` wrap, `Math.abs(Long.MIN_VALUE)` returns negative.
- [ ] **Integer overflow** — `long cutoff = now - threshold` may overflow for arbitrary long domains; `threshold > now` alone means a negative result, not necessarily overflow. Validate domains and use checked arithmetic/bounded comparisons. `Math.multiplyExact` for multiplications that could overflow. `Integer.compareUnsigned` for sequence numbers that wrap, with explicit wrap/window semantics.
- [ ] **Exception handling** — does the code catch the right exceptions? Does it fail closed (safe default) not crashed? Are resources cleaned up in `finally` or try-with-resources?
- [ ] **Concurrency** — if the code crosses thread boundaries, is shared state protected? Are singletons thread-safe? Are volatile/atomic variables used correctly? Is the `synchronized` lock object correct?
- [ ] **Resource leaks** — are all `Cursor`, `Connection`, `InputStream`, `OutputStream`, `Reader`, `Writer`, `PreparedStatement`, `ResultSet` closed via try-with-resources? Are native handles (jlibtorrent SWIG objects) released deterministically?
- [ ] **Transaction atomicity** — if multiple DB operations must be atomic, are they wrapped in a transaction? (`db.beginTransaction()` / `setTransactionSuccessful()` / `endTransaction()`)
- [ ] **Defensive copies** — are byte arrays cloned on POJO boundaries? (`return bytes.clone()` on getters, `this.x = x.clone()` in constructors). Are mutable collections exposed via defensive copies?
- [ ] **UTF-8 handling** — are string lengths measured in bytes (for network/DB) or chars (for display)? `String.getBytes(UTF_8).length` vs `String.length()`.

### FrostWire-specific correctness checks

- [ ] **BTEngine off UI thread** — JNI calls to jlibtorrent (`BTEngine`, `SessionManager`, `Ed25519`, `TorrentInfo`) must NEVER happen on the EDT (desktop) or main thread (Android). Use a worker such as `ThreadExecutor` or `SystemUtils.postToHandler(MISC, ...)`. `GUIMediator.safeInvokeLater()` schedules **ON** the EDT and is only for the UI result, not the expensive work.
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
- [ ] **FTS5 injection** — FTS5 MATCH expressions are sanitized. Reserved words (`OR`, `AND`, `NOT`, `NEAR`) are quoted. Historical stripping of non-alphanumeric chars needs Unicode-safe token handling and fixtures, not an assumption that punctuation removal alone is safe or preserves search semantics.
- [ ] **LIKE wildcard injection** — `%` and `_` in user input are escaped with `ESCAPE '\\'`. Without this, a remote peer can send `%` to match the entire local index in one query.
- [ ] **Path traversal** — file paths from untrusted sources are validated. No `../../etc/passwd`. Use `File.getCanonicalPath()` for root/candidate and verify containment by path component, not naive string prefix (`/allowed-other` is not inside `/allowed`); account for symlink races where relevant.
- [ ] **Deserialization** — JSON parsing is wrapped in try-catch. Malformed JSON returns empty/null, not crash.
- [ ] **Signature verification** — remote requests are Ed25519-signed. Verify BEFORE processing. Timestamp skew is freshness, not replay prevention; require bounded authenticated requester/nonce replay state as well. Rate limiting is per authenticated source, with cheap ingress budgets before crypto.
- [ ] **Auth token** — control API endpoints (except `/health`) require `X-IceBridge-Token` header. The token is generated server-side, not client-supplied.
- [ ] **Input length caps** — cap all untrusted inputs before materialization, parsing, or scheduling. Historical examples: keywords ≤256 chars, JSON ≤16MB, file lists ≤10,000 entries; verify current per-protocol limits and byte/decoded-expansion/aggregate budgets. Reading a whole body then checking length is not a bound.
- [ ] **No secrets in logs** — private keys, auth tokens, passwords, BIP39 mnemonics are NEVER logged. Truncate only already-redacted nonsecret payloads to 200 chars; truncation does not redact secrets.
- [ ] **No secrets in commits** — `git diff --cached` reviewed before commit. No `.pem`, `.key`, `.p12`, `.crt` files containing secrets. No hardcoded API keys or passwords; public certificate fixtures require explicit provenance/review.
- [ ] **Integer overflow as attack** — `Math.abs(Long.MIN_VALUE)` and manual `-Long.MIN_VALUE` are both negative; `a - b` may already overflow. Validate timestamp domains and use checked subtraction with rejection on overflow or bounded comparisons, not a manual sign-flip workaround.
- [ ] **Rate limiting** — per-source rate limiting on all incoming peer requests. Sliding window; bound limiter-key cardinality and expiry as well as QPS.
- [ ] **Error messages don't leak** — rejection responses to remote peers must not reveal the rejection reason (helps attackers tune bypasses). Log details locally only.

---

## 4. Performance

### Checklist

- [ ] **No O(n²) on hot paths** — search results, UI lists, peer directories. If iterating a collection inside another iteration, consider a Set/Map lookup instead.
- [ ] **No DB calls on UI thread** — all `SQLiteDatabase` operations are on background threads. `synchronized(db)` blocks must be short.
- [ ] **No network on UI thread** — all HTTP, rUDP, DHT operations are off the main thread.
- [ ] **Batch DB operations** — multiple INSERTs use `beginTransaction()/endTransaction()` not individual auto-commits.
- [ ] **Cursor management** — Cursors are closed via try-with-resources. Large result sets are paginated (`LIMIT`).
- [ ] **Memory bounds** — cap untrusted collections/bodies before materialization. Reserve inbound queue capacity before acceptance; never silently evict accepted work. Reject/backpressure unaccepted work and give accepted expiry/cancellation an explicit outcome.
- [ ] **Thread pool sizing** — daemon threads are marked `setDaemon(true)`. Named threads for debugging.
- [ ] **Lazy initialization** — expensive resources are loaded on first use, not at startup.
- [ ] **Static final for constants** — regex patterns, Gson instances, Logger instances are `private static final`, not created per-call.
- [ ] **Connection reuse** — OkHttp `OkHttpClient` instances are reused (they have connection pools). Don't create a new client per request.

### FrostWire-specific performance checks

- [ ] **jlibtorrent Ed25519 over JDK** — `IdentityKeys.generate()` uses `com.frostwire.jlibtorrent.Ed25519.createKeypair(seed)`. Historical native speedup reports were 50-100x versus JDK `KeyPairGenerator`; remeasure the actual provider/runtime/workload rather than treating that ratio as universal.
- [ ] **Polling intervals appropriate for mobile** — historical guidance used a 300ms foreground transport poller and DHT advertising every 15-30 min on mobile (versus 5 min desktop). Verify current delivery/battery constraints; consider adaptive screen/background intervals rather than treating these values as fixed requirements.

---

## 5. Safety

### Checklist

- [ ] **Fail closed, not crashed** — native code, deserialization, network calls are wrapped in appropriate exception handling with a safe default, avoiding crash-on-every-startup loops. Catching Java exceptions cannot contain native process crashes.
- [ ] **Race conditions** — coordinate `close()` with resource ownership and in-flight use through appropriate synchronization or atomic state. Serialize database close against database operations where required, but never make transport cancellation wait for the lock held by the blocking I/O it must interrupt. Test concurrent use/close and cancellation, not one mandatory locking pattern.
- [ ] **Shutdown ordering** — components are shut down in reverse order of startup. Listeners removed before transports closed. Transports closed before servers.
- [ ] **Native init wrapped** — `try/catch` around recoverable native initialization failures (Python, ffmpeg, jlibtorrent `.so` load). Corrupted binaries are a real-world occurrence; handle relevant linkage errors without claiming protection from a native crash.
- [ ] **Synchronous cleanup before async** — if state is cleaned up and then an async callback fires, the callback must see the cleaned state, not stale data.
- [ ] **Timeout audit** — every network/IO operation has a timeout. No `Thread.sleep(Long.MAX_VALUE)` without a shutdown path. No infinite `Object.wait()` without a notify. Verify dependency timeout units and use one monotonic absolute deadline covering queueing, serial sends, retries, and reads, not a timer started after them.

### Android-specific safety checks

- [ ] **Memory leaks** — no static references to Activity/Fragment/View. Non-static inner classes holding implicit outer reference to Activity = leak. Use `static` inner classes with `WeakReference` or standalone classes.
- [ ] **Listener/Receiver cleanup** — `BroadcastReceiver`, `ContentObserver`, `Cursor` registered in `onCreate`/`onResume` must be unregistered in `onDestroy`/`onPause`.
- [ ] **Background execution limits** — Android 8+ restricts background execution. WorkManager handles eligible deferrable persistent jobs; permitted foreground services cover appropriate user-visible work. Doze restricts execution/network and processes may die; it does not specifically kill `ScheduledExecutorService`. Document lifecycle ownership and any scheduling migration.
- [ ] **Battery impact** — polling intervals should be adaptive. GPS, DHT, and network polling drain battery. Consider `WorkManager` with `NetworkType.CONNECTED` constraints.

---

## 6. Code Style (gubatron + aldenml)

The code must follow the FrostWire house style. See `frostwire-engineer` skill for the full spec.

### Formatting

- [ ] **Spotless check passes** — coordinator runs `./gradlew spotlessCheck` from `desktop/` (enforces `google-java-format`). Fix with scoped/authorized `./gradlew spotlessApply`; verify `ratchetFrom` against `origin/master` and do not format another owner's changes.
- [ ] **No wildcard imports** — Spotless removes unused imports but doesn't collapse wildcards. Ensure no `import java.util.*`.
- [ ] **File header** — use the applicable FrostWire license/header for original code; preserve upstream license notices for imported or derived code. Record actual authorship, not automatic maintainer attribution. Check compatibility rather than mechanically replacing every header with GPL v3.
- [ ] **Logging** — `com.frostwire.util.Logger` only. Never `System.out`, `System.err`, `printStackTrace()`, SLF4J, or `java.util.logging` in library code; intentional headless CLI UX is qualified below.
- [ ] **No redundant comments** — code should be self-explanatory. Method names describe what they do at the caller level; retain concise non-obvious invariant/API documentation required by the documentation checklist.
- [ ] **No @SuppressWarnings("unused")** — delete dead code, don't silence the compiler.
- [ ] **No magic numbers** — named constants for buffer sizes, port numbers, table names, DHT key prefixes.
- [ ] **Reuse before building** — search `com.frostwire.util.*` before writing any utility.
- [ ] **Commit message format** — `[scope] imperative description (#issue)`. Scopes: `[android]`, `[desktop]`, `[common]`, `[all]`, `[test]`, `[docs]`, `[build]`.
- [ ] **One change per commit** — don't mix features, refactors, and formatting in the same commit.
- [ ] **Changelog updated** — `desktop/changelog.txt` and/or `android/changelog.txt` updated for user-facing changes. `common/` changes update BOTH.
- [ ] **UI/i18n parity** — desktop user-facing strings use `I18n.tr`; Android locale keys, placeholders/plurals, and affected themes remain consistent.

### Git History Review

- [ ] **Commits are granular** — one logical change per commit. A branch with 35+ granular commits is normal.
- [ ] **No formatting noise mixed with product fixes** — formatting changes go in their own commit.
- [ ] **Branch is rebased, not merged** — review the established linear-history convention; `git fetch origin master` then `git rebase origin/master` are examples only when explicitly authorized. Never perform history changes just because this review checklist mentions them.
- [ ] **Force-push with lease** — if a force-push is explicitly authorized, use `git push --force-with-lease`, never `--force`. A review does not authorize any push.

---

## 7. Documentation

Code must be documented well enough that a new contributor can understand it without reading the implementation.

### Checklist

- [ ] **Class javadoc** — every public class has a javadoc explaining what it does, its role in the system, and key design decisions.
- [ ] **Public method javadoc** — every public method documents applicable `@param`, `@return`, `@throws` contracts. For non-obvious methods, include a brief explanation of the algorithm or approach.
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
- [ ] **Security tests** — if the code handles untrusted input, write a defensive local test that proves rejection. Example: `search_percentWildcard_doesNotMatchAll`. Do not reproduce attacks against live peers/services.
- [ ] **Bug fix includes regression test** — the test must FAIL without the fix and PASS with it. The test name should describe the bug: `searchLike_wildcardInjection_leaksEntireIndex`.
- [ ] **Mutation testing mindset** — not just "does the test pass" but "if I delete this line, does the test fail?" A test that passes regardless of the implementation is worthless.
- [ ] **Test isolation** — each test sets up its own state (`@Before`/`@BeforeEach`) and cleans up (`@After`/`@AfterEach`). Tests don't depend on execution order. Each Robolectric test uses a unique DB name to avoid cross-test contamination; reset process-wide singletons and close resources.
- [ ] **Real fixtures over synthetic** — use actual torrent metadata, real search responses, real DHT items. Synthetic data misses the bugs that ship.
- [ ] **Integration test coverage** — unit tests cover individual methods, but does the wiring work? If you added a new component, write a test that exercises the full path (start server → send request → get response).
- [ ] **Robolectric for Android** — historical runner example: `@RunWith(RobolectricTestRunner.class)` with `@Config(sdk = 34)`. Verify current configuration and limitations (FTS5/native jlibtorrent support is not guaranteed); document fallbacks and device/runtime verification gaps.
- [ ] **Test naming** — `methodName_scenario_expectedResult` (e.g., `search_byTorrentName_returnsMatch`, `upsert_replacesExisting`).
- [ ] **Test compile check** — `./gradlew compilePlus1DebugUnitTestJavaWithJavac` (Android) or `./gradlew compileTestJava` (desktop) passes.
- [ ] **Test execution** — at least the affected test class runs and passes. Don't claim "all tests pass" without running them.
- [ ] **Behavior, not labels** — inspect assertions and production wiring. A test name, mocked codec, regex/source-string assertion, or green suite cannot establish behavior it never executes. Prefer focused JUnit 5 behavior tests where established; use the existing Android runner where needed.

### Regression test pattern for bug fixes

```java
@Test
public void searchLike_percentWildcard_doesNotLeakEntireIndex() {
    // Local fixture for wildcard injection: "%" must not match all torrents.
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
- [ ] **APK size impact** — check the current variant's assemble task before/after (`./gradlew assembleDebug` where available). Historical Netty addition was ~2MB and accepted for that feature; measure current APK/AAB and ABI impact rather than inheriting that approval.
- [ ] **Transitive dependency count** — run `./gradlew dependencies` to see what comes along. Avoid dependencies that pull in 50+ transitive jars.
- [ ] **CVE/advisory check** — check the dependency version against known CVEs. Use OWASP Dependency Check or manual search.
- [ ] **Actively maintained** — last commit < 1 year ago? Issues being responded to?
- [ ] **Same version on desktop and Android** — prefer alignment for shared dependencies; where shipped versions differ (e.g., OkHttp desktop 4.12.0, Android 5.3.2), verify the shared API/runtime contract and both resolved graphs rather than pretending versions match.
- [ ] **ProGuard/R8 keep rules** — Gson-reflected classes, reflection-based code, and JNI signatures need appropriate keep rules in `proguard-rules.pro`; inspect `multidex-config.txt` separately for its main-dex role, not as a substitute for R8 rules. If adding Netty, check if R8 strips needed classes and test the minified artifact.

---

## 10. Wire Protocol & Schema Compatibility

When changing serialized data that crosses process/network boundaries:

### Checklist

- [ ] **Wire protocol version** — `RemoteSearchRequest`, `RemoteSearchResponse`, `SearchPayloadCodec`: does the change break communication with older peers? Is the version field bumped? Explicitly define accepted/rejected versions; never preserve an insecure downgrade solely for connectivity.
- [ ] **Canonical bytes** — if `canonicalBytes()` changes, signatures from old peers will fail verification. Preserve field ordering and version/domain rules; appending fields is not automatically signature-compatible. Test old/new encodings and explicit rejection where needed.
- [ ] **DB schema migration** — if the SQLite schema changes: is `SCHEMA_VERSION` bumped? Does `onUpgrade()` handle the old→new path? Is the upgrade path tested? Does it preserve existing data?
- [ ] **BIP39 mnemonic compatibility** — if `IdentityKeys` serialization changes, old mnemonics must still restore correctly. Test with a known mnemonic.
- [ ] **DHT item format** — BEP 44/46 items: are they backward-compatible? Old clients receiving new-format items should ignore unknown fields where the version contract permits, otherwise reject cleanly, not crash or reinterpret signed bytes.

---

## 11. Build Verification

Code must compile and tests must pass on all affected targets. Only the coordinator runs Gradle in the shared tree; workers report required gates rather than starting competing builds. Verify current task names before execution.

### Commands

Run each command from the named target directory.

| Target | Compile | Test | Format |
|--------|---------|------|--------|
| Desktop (`desktop/`) | `./gradlew compileJava` | `./gradlew test --tests "com.frostwire.search.relay.*"` | `./gradlew spotlessCheck` |
| Desktop (full test) | — | `./gradlew test` | — |
| Desktop (format fix) | — | — | `./gradlew spotlessApply` |
| Desktop (lint) | — | — | `./gradlew lint` |
| Android (`android/`, compile) | `./gradlew compilePlus1DebugJavaWithJavac` | — | — |
| Android (test compile) | `./gradlew compilePlus1DebugUnitTestJavaWithJavac` | — | — |
| Android (test) | — | `./gradlew testPlus1DebugUnitTest --tests "com.frostwire.android.search.*"` | — |
| Android (full unit test) | — | `./gradlew testPlus1DebugUnitTest` | — |
| IceBridge JAR (`desktop/`) | `./gradlew icebridgeJar` | — | — |

### Known test failure history (revalidate, not a blanket flaky dismissal)

- `InternetArchiveSearchPatternTest` — historically archive.org timeout
- `MagnetDLSearchPatternTest` — historically site moved, TLS cert changed
- `TelluridePlaylistTests` — historically YouTube source name changed

These records identify prior external-service failures, not proof that a fresh failure is unrelated. Capture current output, compare the baseline/environment, and determine whether changed code or a deterministic assertion is responsible before excluding a failure.

### Verification evidence levels

- **Source review** establishes inspected call paths/invariants, not execution or passing tests.
- **Isolated compilation/tests** establish only the listed current sources and exercised behavior. Name cached classes, mocks, stubs, native libraries, and classpath dependencies; stale build outputs cannot prove current full-source integration.
- **Full-source target gates** compile and test the current affected modules, including shared callers. Report exact commands, revision, environment, failures/skips, and missing gates; a green subset is not the full module suite.
- **Runtime/deployment evidence** remains separate: local SQL is not Android CursorWindow/FTS proof; loopback/simulation is not WAN/EC2 capacity proof. Report delivered work, tail latency, and resource plateaus, not attempted sends or tests paced below a limiter.

### Deploy & Artifact Verification

- [ ] **"up-to-date" ≠ latest commit** — gradle up-to-date only means the artifact matches that checkout's sources. Verify the deploy host's `git rev-parse HEAD` and the artifact's own version banner (e.g. IceBridge `software version = 1.1.0` line); a missing banner block once proved a stale jar despite a "successful" build (MentisDB #896).
- [ ] **Suite width matches change width** — for `common/` constants, shared helpers, or wire behavior, require the FULL module suite, not only the package suite. Deterministic failures shipped to CI because only the relay package ran (MentisDB #902).
- [ ] **Constant/label changes audit** — when a public constant value or user-visible label changes, grep every test asserting the old value before approving.

---

## Review Output Format

When completing a review, produce a structured report with findings first, ordered by severity. Retain context, summary, verification, and recommendation, but do not bury defects beneath an overview. Each finding includes trigger, evidence, impact, minimal fix, defensive validation, and open/fixed/verified/blocked status. If none, say so and list residual risks.

```markdown
## Code Review: [commit/PR description]

### Findings

#### BLOCK
- [file:line] Trigger/preconditions, violated invariant, evidence, impact, minimal fix, defensive validation, status.

#### HIGH
- [file:line] Description of the high-severity issue with the same evidence fields.

#### MEDIUM
- [file:line] Description of the medium issue.

#### LOW
- [file:line] Style nit or minor suggestion.

#### INFO
- Positive observations, accepted costs, or qualified follow-ups; no defect claim.

### Context
- Module(s): [desktop / android / common]
- Threat model: [trusted input / remote peer / user input / file]
- Blast radius: [desktop-only / android-only / both via common]
- Revision, scope, assumptions, and non-findings: [...]

### Summary
[1-2 sentence summary of what the change does and whether it's ready]

### Verification
- [ ] Compiles on [desktop/android/both]: exact command, revision, environment
- [ ] Tests pass on [desktop/android/both]: exact suites/counts, failures/skips, missing gates
- [ ] Spotless check passes (`./gradlew spotlessCheck`)
- [ ] Changelog updated
- [ ] No secrets in diff
- [ ] common/ code is Android-compatible and respects shared-code policy (including java.nio.file)
- Evidence level: [source / isolated tests with dependency caveats / full-source gates / device or deployment]
- Residual risks and unverified claims: [...]

### Recommendation
[APPROVE / REQUEST CHANGES / BLOCK] for [explicit scope]
```

---

## Quick-Reference: Top 30 Most Common Findings

Based on the FrostWire codebase history + MentisDB frostwire chain lessons. Revalidate each example against current source; historical constants, versions, and fixes are not current-defect or topology claims:

1. **common/ uses desktop-only API or violates shared-code policy** — `java.net.http`, `java.awt`, desktop JDBC, or policy-forbidden `java.nio.file`. BLOCK. Historical review targets: `IdentityKeys`, `IceBridgeTokens`, `IceBridgeHostCache` (`Files`); Files exists on API 26, so distinguish policy from availability.
2. **LIKE wildcard injection** — remote peer sends `%`, matches entire local index. Escape with `ESCAPE '\\'`.
3. **No transaction in multi-step DB writes** — crash mid-write leaves DB inconsistent. Wrap in `beginTransaction()`.
4. **`close()` race condition** — resource ownership and concurrent use/close are not coordinated. Database serialization and interruptible transport cancellation need different synchronization; do not require cancellation to wait behind blocking I/O.
5. **`Math.abs(Long.MIN_VALUE)` is negative** — timestamp skew bypass. Manual sign flip also overflows; validate domains and use checked arithmetic/bounded comparisons. Freshness alone does not prevent replay.
6. **jlibtorrent on UI thread** — StrictMode violation or EDT freeze. Always background.
7. **Missing defensive `byte[].clone()`** — shared mutable state across threads.
8. **Resource leak** — Cursor/Connection not in try-with-resources.
9. **`System.out` instead of `Logger`** — against house style (except intentional headless CLI UX in `IceBridgeServer.main`).
10. **No regression test for bug fix** — the bug will come back.
11. **Secrets in logs** — auth tokens, private keys, full JSON payloads logged on error. Use `[set]` / never print tokens after generate-token path is done.
12. **Spotless violations** — `./gradlew spotlessCheck` fails. Fix with authorized/scoped `./gradlew spotlessApply`.
13. **ScheduledExecutorService on Android** — in-process timers do not survive process death or bypass Doze restrictions. Use WorkManager for eligible deferrable persistent tasks, not as an automatic replacement for every live transport timer.
14. **Memory leak via static Activity reference** — Android-specific. Use WeakReference or static inner class.
15. **Wire protocol break** — canonical bytes changed without version/domain handling. Historical IdentityRecord v3 retained v1/v2 reads; preserve safe advertised compatibility, but explicitly reject insecure legacy wire paths rather than downgrading.
16. **Self-discovery / co-located ports** — PeerDiscovery must skip own Ed25519 pub + loopback; configurable ports for co-located standalone.
17. **Async transport race** — register response listeners *before* send; async fakes must deliver off-thread.
18. **rUDP without app-level fragmentation** — IP frags drop whole datagrams; use DATA_FRAG/DATA_END + byte-equal reassembly tests.
19. **ProcessBuilder.inheritIO under Gradle** — historical subprocess tests stalled on IO handling; redirect to temp files and diagnose actual inherited/redirected streams rather than assuming every inherited stream is a pipe. Tests use `IdentityKeys.generate(0)`, never PoW 20.
20. **App multi-hop re-sign vs requester verify** — re-signing with forwarder key while verify checks `requesterPub` always fails. Dual-envelope (origin query signature separate from hop attribution) addresses this; authenticate maximum routing budget too. Flag mismatched re-sign paths as **BLOCK**.
21. **HELLO_ACK without identity** — historical empty ACK left initiator `remotePub` null, breaking mesh RELAY one way (#875). HELLO-shaped pub/ts/sig and `setRemotePub` fixed that wiring, but legacy shape/signature alone is not fresh endpoint key-possession proof; require the current bound handshake.
22. **Dual `connect()` overwrite** — second initiator session replaces authenticated responder session on `sessionsByAddress`. `connect` must reuse or explicitly reconcile simultaneous-open ownership (MentisDB #875).
23. **Unauthenticated RELAY_RESPONSE** — spoofable sourcePub into inbound queue / amp. Authenticated session/packet + rate limit required; CID/address lookup is not authentication. Local clients use `/poll` delivery (MentisDB #876).
24. **Mesh flood without bounds** — fan-out × hop TTL × payload without `MAX_APP_PAYLOAD`, rate limit, or TTL cap is amplification **BLOCK**. Verify current configured budgets, not historical fixed topology numbers.
25. **DHT list ConcurrentModificationException** — never iterate live jlibtorrent `dhtGetPeers` lists; snapshot first.
26. **TTL boundary mismatch** — #902 caught an early return changing the old ttl=1→0 forwarding contract. Retain that regression history, not useless forwarding: align sender/receiver semantics and test boundaries; never preserve a TTL-zero send that the receiver simply discards.
27. **Constant/label change with stale assertions** — historical `MAX_FORWARD_TARGETS` 3→30, `"Local"`→`"Local (test)"`; grep tests for the old value before push (MentisDB #902).
28. **Test extraction blind spot** — greedy regex `[^>]*` swallows the `/` of self-closing XML tags and hides keys from parity checks; use `[^>]*?` and test the extractor (MentisDB #902).
29. **Singleton leakage across test classes** — process-wide topology/settings mutated by one class, asserted by another; reset in `@BeforeEach`/`@AfterEach` or CI becomes order-dependent.
30. **Benchmark ranking without CRN** — per-candidate seeds = each candidate on a different random network; the winner is seed luck (historical ±30pp rare-hit swings on identical configs). Common random numbers, or replications with confidence intervals (MentisDB #903).

---

## 12. Distributed Relay Network / IceBridge Review (MANDATORY for relay code)

Use this section whenever the change touches `common/.../search/relay/**`, IceBridge, DHT advertiser/discovery, karma, or distributed search wiring on desktop/Android.

### Architecture truth (hybrid model)

| Plane | Mechanism | Role |
|-------|-----------|------|
| Identity / bootstrap | Direct TCP (`IncomingRelayServer`, historical default port 6888) + BEP 46 `IdentityRecord` | Learn Ed25519 pub + rudpPort + role; a valid record is not proof of live endpoint key possession |
| Discovery | BEP 5 topics `frostwire-peers-v1`, `frostwire-relays-v1`, `frostwire-bootstrap-v1` | Multi-writer rendezvous only |
| Data / search transport | IceBridge rUDP mesh + HTTP control API | Opaque payload routing for signed search messages |
| Search application | `DistributedSearchPerformer` + `RelaySearchService` + `SearchResponseVerifier` | Sign/verify keywords & results |
| Trust | `PeerDirectory.topByTrustVerified` + karma chains | Never query placeholder `SHA-256(host:port)` peers for search |

Design reference: repo root `DESIGN_RELAY_REGISTRY.md`; reconcile it with current source and versioned contracts before treating it as architectural truth. MentisDB chain: `frostwire` (existing agent_id `gubatron` for durable writes).

### Checklist — Identity & discovery

- [ ] **Own-pub self-skip** — `PeerDiscovery` receives `ownEd25519Pub`; after identity verify, skip if pub matches own (MentisDB #831).
- [ ] **Loopback skip** — `isLocalEndpoint` rejects 127.0.0.1 / localhost / ::1 before TCP auth.
- [ ] **Verified-only search** — `DistributedSearchPerformer` uses `topByTrustVerified`, never raw `topByTrust` / placeholders.
- [ ] **IdentityRecord v2** — historical `rudp_port` + `role` addition retained v1 fallback (`rudpPort==0` → 6889). Verify the current version/fallback contract rather than hardcoding that historical default into new callers.
- [ ] **DHT topics** — peers announce peers topic; FORWARDER/BOTH (or auto-elect when connectable) announce relays; discovery prefers relays first (no `<10 peers` gate — MentisDB #832).
- [ ] **Placeholder policy** — SHA-256(host:port) allowed only as temporary directory keys; never for trust scoring or search targets.
- [ ] **Identity file path** — desktop: `CommonUtils.getUserSettingsDir()/libtorrent/identity.dat` (not settings dir alone). Import/export/restore must use the same path as `Initializer`.

### Checklist — IceBridge transport

- [ ] **Control auth** — non-`/health` endpoints require bearer token; multi-token file; no token in logs; `--generate-token` prints once. Multiple administrator tokens are not tenant isolation; see deployment boundaries below.
- [ ] **Client HTTP** — OkHttp in `common/` (not `java.net.http`). Defensive copies on `InboundMessage`; response size caps enforced while reading, before full materialization; `close()`.
- [ ] **Poller model** — single shared `IceBridgeSearchTransport` poller fans out to permanent `IncomingSearchRequestHandler` + transient performer listeners (no dual-poll race).
- [ ] **Listener-before-send** — performer registers listener + latch before any `transport.send` (MentisDB #809).
- [ ] **rUDP fragmentation** — payloads > `MAX_FRAGMENT_PAYLOAD` (historically 1024) split into DATA_FRAG/DATA_END; reassembly byte-equal tests; max assembled size / concurrent groups / aggregate bytes bounded (MentisDB #810). Verify current constants.
- [ ] **Hole punch / connectivity** — historical unsolicited HELLO/connect-back logic parsed host:port and called `connect()` to establish connectivity. That alone is not verified connectivity: require fresh authenticated/correlated introductions and destination policy before DNS/dial or forwarder election; verify endpoint possession.
- [ ] **PeerRegistrySync** — uses peer `rudpPort` from directory, not hardcoded 6889.
- [ ] **Process launcher** — no `inheritIO()`; redirect stdout/stderr to temp files; pass `--relay-port` and configurable rUDP; health wait with process-alive check. Do not put tokens/secrets in argv.
- [ ] **Local vs remote** — settings: ENABLE, USE_REMOTE, URL, token, bind host, ports, role. Structured config dump at startup without secrets (MentisDB #837).
- [ ] **Endpoint ownership** — for each advertised `host:rudpPort`, name the process that binds it in every local/remote mode. A remote HTTP client alone cannot receive rUDP; reject configurations that advertise an endpoint with no listener.
- [ ] **Control/data-plane alignment** — a peer registered through one control API is routable only by the rUDP server behind that same registry. Verify Android, desktop, and forwarder use the intended registry rather than independent local registries.
- [ ] **Relay frame reality** — `sendRelay` must serialize `RudpPacket.Type.RELAY`, and the forwarder must receive that type before emitting `RELAY_RESPONSE`. A relay-shaped payload inside `DATA` is not relay fallback.
- [ ] **Bidirectional route** — test request and response independently through the exact topology. Direct delivery to a known endpoint and fallback relay delivery for an unknown/NATed endpoint are separate cases.
- [ ] **Delivery semantics** — `/send` returning HTTP success must mean documented queue acceptance only, unless there is an authenticated acknowledgement from the destination. Callers must not count it as a delivered request without an application response.
- [ ] **CLI System.out** — allowed only in `IceBridgeServer.main` / help / generate-token; library paths use `Logger`.

### Checklist — rUDP session auth & multi-hop RELAY (BLOCK-class if violated)

MentisDB frostwire **#873–#876** records the original failures. Review every change to `RudpSessionManager` / `RelayFrame` / HELLO path against these invariants and current stronger authentication contracts; historical fixes alone are not a security proof.

- [ ] **HELLO_ACK proves responder** — historical HELLO-shaped ACK (pub + ts + sig over connectionId||ts) plus `handleHelloAck` calling `setRemotePub` repaired the empty-ACK/null-identity bug. That same-shape legacy transcript is not sufficient endpoint proof: require fresh domain/peer/role/version-bound challenge transcripts and key possession. Never re-enable legacy acceptance merely to make mesh tests pass.
- [ ] **No dual-session overwrite** — `connect(addr)` reuses/reconciles `sessionsByAddress` ownership. Bidirectional warm must not overwrite authenticated `remotePub`; test simultaneous open, all CID/address aliases, and conditional removal.
- [ ] **RELAY sourcePub = hop peer** — `frame.sourcePub` must equal the authenticated sender's `session.remotePub()`. A `sessionsByRemoteId.get(connectionId)`/address lookup locates state but does not authenticate a packet. Authenticate type, sequence, ACK, payload, and migration fields before mutation; sendRelay attributes each hop to **this** node, separate from the origin signature.
- [ ] **RELAY_RESPONSE authenticated** — session and packet integrity required; rate-limited; attribute to `session.remotePub()`, **never** spoofable header bytes. Unauthenticated `write(RELAY_RESPONSE)` fire-and-forget is **BLOCK** (amp + queue injection).
- [ ] **Local registry /poll delivery** — client registered on this node's own rUDP host:port → `notifyListener` / local inbound queue, not self-UDP RELAY_RESPONSE loop.
- [ ] **Amplification bounds: payload** — `RelayFrame.MAX_APP_PAYLOAD` enforced on encode **and** decode.
- [ ] **Amplification bounds: routing** — explicit hop TTL/fan-out budgets. Historical defaults ≤3 hops/≤3 peers are not current topology limits; inspect configured values and total work/byte budgets.
- [ ] **Amplification bounds: rate** — per-peer RELAY / RELAY_RESPONSE rate limit, in addition to the global ingress/admission bounds below.
- [ ] **No RELAY fragmentation claim** — if app payload can exceed one fragment, either reject under the current policy or implement frag for RELAY; do not silently fail multi-hop for large search frames. Verify current implementation before claiming support.
- [ ] **Auth change ⇒ re-run multi-hop E2E** — after removing unauthenticated paths, `MultiRelayMeshSearchTest` (or equivalent explicit multi-forwarder topology) must still pass with authenticated session warm + topology assert, without insecure downgrade.

### Checklist — Search protocol correctness

- [ ] **Request verify** — Ed25519 over canonical bytes; overflow-safe timestamp skew; bounded requester/nonce replay state; rate limit by authenticated **requesterPub**; fail-closed `Optional.empty()` before index access or forwarding.
- [ ] **Response verify** — client checks expected responder pub, nonce, skew, signature (`SearchResponseVerifier`).
- [ ] **TTL / multi-hop policy (app layer)** — historical dual-envelope (`RemoteSearchRequest` v2: signature over query-only; hop fields mutable) solved origin/hop signer mismatch. **BLOCK** re-sign-with-forwarder-key while verifying `requesterPub`. Preserve origin signature separately from authenticated hop attribution; the maximum routing budget must also be authenticated under the current version.
- [ ] **TTL / multi-hop policy (transport layer)** — mesh `Type.RELAY` is separate from app ttl. Transport hop auth rules above still apply even when app dual-envelope is correct.
- [ ] **TTL boundary contract** — #902's ttl=1→0 forward was an old sender/test contract, not authority to retain a discarded hop. Check both sender and receiver: permit final-hop work only if the protocol accepts/processes it, stop exhausted forwarding, and test 0/1/max/over-max with loops/replays. Do not preserve a TTL-zero send the receiver drops.
- [ ] **Catalog browse** — signature + skew/replay policy verified; desktop wiring must pass `LocalIndex` if feature is claimed; Android wiring must also be checked rather than assuming historical index injection still holds.
- [ ] **FTS5 fixtures** — whole-word match; no kebab-case-only names. Historical sanitizer stripped non-alnum; test Unicode token quoting and punctuation behavior against the actual implementation.
- [ ] **Trust check on requester** — spam/trust floor evaluated in *target's* directory for the *requester*, not the target (MentisDB #798).
- [ ] **Canonical path bytes** — `pathLengthBytes()` includes count + each length-prefixed hop; non-empty path signatures must verify.

### Checklist — Karma

- [ ] Load chain from store on writer construct (no genesis reset every launch).
- [ ] Epoch commitment before endorsements; energy budget enforced in verify.
- [ ] WoT trust is **BFS with hop decay**, not recursive double-count (MentisDB #795).
- [ ] BTEngineListener heavy work offloaded via `ThreadExecutor` / chain with dedup (`BTEngineListenerChain`).

### Checklist — Cross-platform module placement

- [ ] MCP Streamable-HTTP implementation using `com.sun.net.httpserver` / virtual threads → **desktop only** (`com.frostwire.mcp.desktop.transport`); verify configured JDK support. BouncyCastle itself is not desktop-only: check actual artifact/API availability on each target.
- [ ] IceBridge process launcher → desktop only.
- [ ] Android in-process `IceBridgeServer` + OkHttp client in common; no subprocess.
- [ ] JDBC (`LocalIndexTable`, `KarmaChainTable`) → desktop only; Android has `AndroidLocalIndex` / `AndroidKarmaChainStore`.
- [ ] Abstract `Files` usage behind injected `File` / streams in common by repository policy; do not claim `java.nio.file` is missing from Android API 26.

### Checklist — Tests required for IceBridge/relay changes

| Area | Minimum tests |
|------|----------------|
| Peer discovery | self-skip by pub, local endpoint skip, verified upsert, unauth drop, custom relay port; snapshot DHT endpoint lists before iterate (no CME) |
| Transport | listener-before-send race, multi-listener fanout, poll failure isolation |
| rUDP | fragment reassembly byte equality (pattern `i % 256`), oversize reject, bounded stale incomplete-group cleanup without silently discarding accepted completion |
| rUDP session | HELLO_ACK establishes authenticated initiator `remotePub`; fresh transcript/endpoint proof; connect reuse does not drop auth; RELAY rejected without session / wrong sourcePub / bad packet integrity |
| RELAY_RESPONSE | unauthenticated sender dropped; authenticated path delivers; no spoofed header attribution |
| RelayFrame | encode/decode round-trip; reject app payload > `MAX_APP_PAYLOAD`; current hop TTL bounds |
| Multi-hop mesh E2E | Historical fixture: ≥3 FORWARDER + seeder/searcher on different homes; warm authenticated handshake; assert seeder **absent** from searcher home registry; signed search hit arrives (`MultiRelayMeshSearchTest`). Test current topology budgets, not a mandatory production node count |
| Three-node topology | Android requester to desktop index node via cloud forwarder; request and signed response both arrive; assert packet type at forwarder and no LocalIndex use by FORWARDER; local fixtures do not establish WAN capacity |
| Search | signed request/response round-trip, bad sig/nonce/stale/replay reject; dual-envelope hop preserves requester sig and authenticated routing budget |
| Wire versions | Preserve intended safe IdentityRecord v1/v2 decoding when writing v3; reject insecure protocol versions explicitly; RemoteSearchResponse version covers chunk/final domain |
| Multi-instance | publish → find → unpublish → not-find (TCP or IceBridge fake transport) |
| Process launcher | redirect IO (not inheritIO), health check, `--relay-port` parse |
| Identity tests | always `IdentityKeys.generate(0)` |
| MCP (desktop) | SSE GET body empty + session header; `initialized` → JSON `"accepted"` |

### Post-review #1048 — Trust & admission extensions

These focused checks extend, rather than replace, the detailed relay checklist. #1048 (2026-09-05, source baseline `ce69af1ef`) is qualified review history, not proof of current defects, exploitability, remediation, or EC2 capacity. Track each finding's open/fixed/verified/blocked state and revalidate current source.

- [ ] **Separate trust layers** — identity-record validity, endpoint key possession, handshake authentication, packet integrity, and application signatures are distinct. Prefer established secure transports; a signed record or located CID cannot substitute for the missing layer.
- [ ] **Admission order and bypasses** — cheap ingress/global budgets precede crypto/native work; charge requester quotas only after authentication. Bound keys/expiry and reject identity replacement, impossible ACKs, unsolicited/uncorrelated introductions, and disallowed destinations. Known peers, reconnects, observed registration, and outbound introductions must not bypass admission.
- [ ] **Whole-pipeline bounds** — count/byte/cardinality/lifetime limits must precede allocation, parsing, crypto, native work, and scheduling. Include decoded expansion, aggregate chunks, accepted sockets, caches, pending retries, and cleanup, not just the outer frame.

### Post-review #1048 — Delivery & stream ownership

- [ ] **Acceptance ledger** — trace accepted, queued, delivered, ACKed, processed, rejected, expired, and retryable states end to end. Reserve capacity/ownership before acknowledging at the claimed layer; HTTP 200 and transport ACK are not application delivery.
- [ ] **Duplicate ACK and completion retention** — re-ACK previously accepted duplicates without repeating delivery/fanout. Distinguish rejection, incomplete assembly, retained completion, and accepted completion. Never silently evict accepted work as an optimization; retries/cancellation require explicit outcomes.
- [ ] **Authoritative consumer** — name the consumer/queue for each message. An unused shared/identity mirror must not block an active consumer. Bound total targets/bytes, clean empty subscriptions, and specify lease/ACK semantics for reliable polling rather than assuming destructive polling is reliable.
- [ ] **Transport lifetime** — retransmit through the current authenticated endpoint. Bound send windows, retry bytes/lifetime, and pending pre-handshake work; keep pollers/event loops free of blocking providers.
- [ ] **Per-holder stream state** — track each request/holder independently with bounded indices, rows, bytes, duplicates/conflicts, and contiguous completion. One final chunk cannot close other holders or missing chunks. Cancel owned sends/waits/listeners promptly and prevent late download starts or stale UI publication.
- [ ] **Metadata identity and conversion** — verify bytes against the originally requested v1/v2/hybrid infohash independently of holder signature/digest. Validate error shape/digest and request binding. Preserve signed fields, including seeder endpoints, across chunk conversion and URL search collection.

### Post-review #1048 — Privacy, persistence & lifecycle

- [ ] **Live public-share policy** — trace visibility through ordinary search, catalog browse, announcement, metadata provider, cache hit, and queued index work. Test private, inactive, metadata-only, removed, and cached-then-withdrawn torrents without restarting.
- [ ] **Index consistency** — primary rows, file rows, and FTS postings must agree through replacement, deletion, rowid reuse, and migration. Execute Android trigger definitions individually; inspect FTS rows, not only ordinary tables. LIMIT applies to distinct torrents, not a pre-dedup joined window.
- [ ] **Stored data bounds and ownership** — bound row bytes before parsing/writing and avoid huge JSON in summary reads. Verify Android CursorWindow/FTS behavior. Bind karma rows to owner and test actual publisher output through the reader; upsert must not erase local reputation.
- [ ] **Participation and teardown** — gate networking before startup. Distinguish service recreation from explicit stop, opt-out, and network restrictions; explicit stop closes owned listeners/sockets/jobs and prevents respawn. Check partial-start rollback, identity replacement, listener removal, database ownership, and child-process teardown.
- [ ] **UI/worker lifecycle** — generation/revision checks protect asynchronous sort, refresh, and query results. Cancellation must not queue behind the operation it needs to stop. Check executor rejection, serial-worker stalls, ExoPlayer looper, StrictMode/StrictEdtMode evidence, and resource plateaus across repeated start/stop/navigation cycles.

### Post-review #1048 — Deployment boundaries

- [ ] **Control API privacy** — keep APIs private/authenticated and inspect authorization, not only authentication. Multiple administrator tokens do not isolate tenants. Require TLS or an authenticated tunnel for non-loopback credential transport; no secrets in argv/logs.
- [ ] **Privilege separation** — root-owned program/configuration and separate service-writable state. Never source service-writable environment files during privileged install/upgrade. Parse allowlisted data, build without root, and verify process ownership before cleanup; foreign port listeners are not safe kill targets.
- [ ] **Effective configuration** — check CLI/env/default precedence, generated unit configuration, artifact identity, and local-only opt-in debugging. Measure memory/task ceilings including heap, direct buffers, and native allocations. More EC2 instances, sessions, or FDs do not repair admission defects.

### Post-review #1048 — Additional defensive verification matrix

| Area | Required behavior to check |
|---|---|
| Auth/admission | Invalid, stale, replayed, wrong-peer/version, and over-budget inputs rejected without routing/state side effects; bounded key churn |
| Delivery | Accepted-work accounting under slow consumers, real multi-fragment completion retry, duplicate ACK recovery, identity-only polling beyond queue lifetime capacity |
| Streams | Multiple holders, final-before-prefix, duplicates/conflicts, gaps, row/byte caps, endpoint preservation, cancellation during sends/waits |
| Metadata/privacy | Real matching/nonmatching torrent fixtures; policy enforced across every path and cache after withdrawal |
| Storage | Replace/delete/rowid reuse, persisted repair, Unicode, distinct limits, owner isolation, actual publisher-reader round trip |
| Lifecycle/UI | Partial start, repeated stop/start, explicit opt-out, identity change, navigation/cancel/sort races, constant resource counts |

Use local defensive fixtures and the production-role integration matrix above. The 762-test relay pass recorded at #1048 did not establish full-source current integration, Android/device behavior, or EC2 throughput; its multi-hop stress was paced below the limiter. Do not turn this historical result into a current readiness claim.

### MentisDB operating rule for reviewers / implementers

1. Chain: `frostwire`. Use existing agent_id **`gubatron`** for durable project memory; do not invent new agent IDs. Review related memory and file ownership before acting.
2. `ranked_search` before append; link `refs` to prior lessons (#809 async, #810 frag, #831 self-skip, #832 ports, #795 karma, #769 architecture, **#875 dual-session/HELLO_ACK**, **#876 RELAY bounds**, **#1048 qualified review**, **#1108 knowledge preservation**).
3. On any non-obvious fix: `LessonLearned` / `Constraint` immediately after checking for duplicates; fold durable rules into this skill + `frostwire-engineer` when authorized and coordinate companion-file owners rather than editing across ownership.
4. One owner per file. Publish API signatures, units, ownership, failure/cancellation, and wire contracts before changing them; report cross-owner dependencies, never overlap edits or revert unrelated work.
5. Only the coordinator runs Gradle in the shared tree. Workers provide requested gates and exact evidence levels, including cached-dependency/isolated-test limitations and missing full-source gates.
6. Search before milestone `Summary` checkpoints; include refs, owned files, blockers, verification gaps, and ownership release/handoff state before compaction or context loss.
7. Skill updates preserve accumulated hierarchy, examples, triggers, historical context, and useful specific guidance (#1108). Correct inaccurate lines inline and add focused lessons; do not apply code line-minimization goals to skill knowledge or republish an aggressively condensed replacement.
8. After authorized skill updates, prefer granular `[all]` commits; do not mix skill edits with product code unless requested. No commits, pushes, history changes, or registry publication without explicit authorization and any required coordinator review. Security verification is defensive local regression testing only, not autonomous offensive workflows or live attacks.

---

## Companion Skills

- **`frostwire-engineer`** — defines the code style rules this skill enforces
- **`frostwire-performance-reviewer`** — profiling, delivered-work measurements, and evidence-led performance review
- **`systematic-debugging`** — for diagnosing bugs found during review
- **`verification-before-completion`** — evidence before assertions, always
- **`mentisdb`** — record lessons learned from review findings as `LessonLearned` thoughts on the `frostwire` chain

---

*When a review finds a new class of bug not covered here, add it to the checklist. This skill is a living document — it improves with every review. Historical expansion: TTL boundary contract, constant-change audit, test extraction blind spots, singleton test isolation, CRN benchmarks, deploy artifact verification (MentisDB #896–#903, 2026-07-20). Detailed baseline restored with surgical corrections and focused post-review #1048/shared-brain additions under user correction #1108 (2026-09-05); no historical example substitutes for current-source verification.*
