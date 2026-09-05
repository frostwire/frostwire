---
name: frostwire-engineer
description: 'FrostWire Desktop + Android + JLibTorrent engineering practices. Use when writing, reviewing, or refactoring Java/Kotlin code in the FrostWire repo — enforces gubatron + aldenml house style: KISS, DRY, minimum-scope, composition-over-inheritance, granular commits with [android]/[desktop]/[common]/[all] prefixes when authorized, off-EDT/main-thread, JUnit 5 or existing Android runner tests, com.frostwire.util.Logger, try-with-resources, i18n, Apache/GPL headers, defensive byte[] copies.'
triggers:
  - frostwire
  - commit message
  - '[android]'
  - '[desktop]'
  - '[common]'
  - '[all]'
  - jlibtorrent
  - EDT
  - main thread
  - i18n
  - I18n.tr
  - BTEngine
  - jni
  - KISS
  - DRY
  - code style
  - coding guidelines
  - code review
  - changelog
  - DHT
  - BEP 5
  - BEP 44
  - BEP 46
  - relayd
  - headless
  - Java
  - Kotlin
  - JNI
  - IceBridge
---

# FrostWire Engineer

> Combined house style of **gubatron** (project owner, lead developer) and **aldenml** (co-founder, jlibtorrent architect), distilled from the FrostWire Desktop, Android, and JLibTorrent codebases. Replaces the legacy `AGENTS.md` at the repo root.

If you only read one section, read **§1 Mantras** and **§10 Maintainer's Checklist**.

Read the affected code and callers first; use `frostwire-code-reviewer` for review and `frostwire-performance-reviewer` for measured optimization. These are rules, not claims that current production code satisfies them. Historical examples are evidence to recheck, not mandatory obsolete wire formats. Section 15 retains newer review #1048 defenses and shared-memory contracts separately from the restored house style.

---

## 1. Quick-Reference Mantras

When in doubt, apply the closest mantra. They are not slogans — each one maps to a real review comment we have shipped.

| Mantra | When to apply |
|--------|---------------|
| **"Fail closed, not crashed"** | Native code, deserialization, network calls — guard recoverable failures and fall back; Java catches cannot stop a native process crash |
| **"Off the EDT / off the main thread"** | JNI, I/O, parsing, heavy computation, network, DB writes |
| **"jlibtorrent already handles this"** | Before building custom persistence (e.g. `ip_filter`, session state) |
| **"Delete code, don't hoard it"** | Refactors, cleanup, removing dead layers — net-negative LoC is good |
| **"One change, one commit"** | Git history hygiene when authorized — keep tightly-coupled changes together when splitting would break builds or contracts |
| **"If you can't explain it, simplify it"** | Design review, PR description — explanation friction = design friction |
| **"Scope to the minimum"** | Variable lifetime, visibility, mutability |
| **"Real fixtures, real tests"** | Test data — use actual `iblocklist.com` files, real Bitsearch responses |
| **"DRY first, abstract later"** | Two call sites? Tolerable. Three? Extract. Don't preemptively abstract. |
| **"Compose, don't extend"** | Default to wrapping/composition; only `extends` for abstract types |
| **"One util to rule them all"** | Before writing `toHex`/`fromBase64`/etc., grep `com.frostwire.util.*` |
| **"Check the primitive before designing the abstraction"** | DHT, JNI, SQLite, Android APIs — verify real API semantics first |
| **"Headless first when the feature is network-native"** | Relay/search protocols must run without Swing/Android UI |
| **"Authenticate both ends of the handshake"** | HELLO alone proves only the initiator; require responder key possession bound to this peer/session transcript, not merely a populated `remotePub` |
| **"Reuse the session, don't dual-connect"** | `connect()` to an address that already has a valid session should reuse it — bidirectional mesh warm must not wipe authenticated `remotePub`; an address match alone is not authentication |
| **"No unauthenticated wire type"** | If a packet type can inject into the app queue, authenticate the packet and its session (or equivalent). Fire-and-forget "bootstrap" paths become amp vectors |
| **"Green tests before every push"** | When the user authorizes commit/push and requires tests-to-commit: run the affected suite, then one logical commit, then push — never push red |
| **"Boundary values are the contract"** | Clamps, decrements, TTLs, page sizes — test ttl=1/0/MAX against the intended sender/receiver contract. A ttl-clamp change broke historical forwarding (2026-07), but useless TTL-zero forwarding is not sacred; correct unsafe semantics and their assertions together |
| **"Change the constant, audit the assertions"** | Public constant or user-visible label changed → grep every test asserting the old value/label BEFORE push (`MAX_FORWARD_TARGETS` 3→30, `"Local"`→`"Local (test)"` both broke CI) |
| **"Suite width matches change width"** | Narrow changes get narrow suites; `common/` constants or shared helpers get the FULL module suite. The ttl-clamp regression passed the relay package suite and failed CI deterministically elsewhere |
| **"Same seed, same network"** | Comparative benchmarks/simulations: common random numbers for every candidate, or the ranking is seed luck. Rare-hit swung ±30pp across seeds on identical configs (2026-07) |

---

## 2. Core Philosophies (Non-Negotiable)

### KISS — Keep It Simple, Stupid

- If you can't explain what you're doing in one sentence, you're over-engineering.
- Prefer well-named methods and code reusability over comments. **Code should be self-explanatory.**
- Net-negative LoC after a refactor is the goal, not a side effect.

### DRY — Don't Repeat Yourself

- Reuse your code and ours. Search `com.frostwire.util.*` for any helper (Hex, Base64, URL, hashing, time) before writing a new one.
- DRY code behaves like an equation — it writes itself after the first occurrence.
- The threshold for extracting: **2 call sites tolerable, 3+ extract**. Don't preemptively abstract.
- Watch for *coincidental* duplication (the same value in 3 places that must stay in sync — trackers, SoundCloud credentials, default ports). These get a single source of truth and a test asserting the others match.

### Minimum Scope Principle

- Variables: as local as possible. Method scope > field scope > static.
- Class visibility, in order of preference:
  1. **Local variable** — first choice
  2. **Private member** — only if it must outlive the method
  3. **Protected** — only for inheritance
  4. **Public** — only when you are certain no consumer can break internal state
- Tight scopes prevent concurrency bugs. The wider the surface, the more locks you need.

### Composition Over Inheritance

- If you're extending a non-abstract class, you should probably be composing it instead.
- If you don't own the source of the class you're extending, behavior may surprise you across versions.
- Reach for: `final` fields of helper types, constructor injection, `static` factory methods. Avoid deep class hierarchies.

### Immutability Where Possible

- Favor immutable objects and immutable state. Pairs naturally with minimum scope.
- Final fields, defensive `byte[].clone()` on POJO boundaries, no setters on value types.
- Reduces entire classes of concurrency bugs (no shared mutable state = no race).

### Protocol Reality Before Architecture

- Before designing a distributed protocol, verify the real primitive in the library API and specs. Do not design from a desired abstraction.
- Example: BEP 44 mutable DHT items are **single-writer** (`SHA1(pubkey + salt)`), not arbitrary multi-writer buckets. If you need multi-writer discovery, use BEP 5 peer rendezvous (`dhtAnnounce` / `dhtGetPeers`) plus an authenticated protocol on top.
- Write a small local integration test that proves the primitive before writing a large design around it. The test should validate discovery, not just put/get with an in-process key.
- Prefer one simple protocol path over three clever DHT record types. If a proposed design needs `IndexAnnouncement`, `search-hint`, `relay-record`, and `identity-record` all in the DHT, ask whether two of those are really application messages between peers.

### Headless-First Modularity

- Network-native features must run in a headless JVM without Swing, Android, or desktop settings classes.
- Split modules by role, not UI: `common/` for records, crypto, protocol messages, and interfaces; `desktop/` for Swing and JDBC implementations; `android/` for Android storage/UI; a small `relayd/` module for cloud relays.
- A relay-only node should not require SQLite, a media library, a GUI, or large disk. It needs jlibtorrent, keys, rate limits, RAM-bounded caches, and fast networking.
- Use composition for roles: `RelayRole`, `IndexRole`, `SearchRole`, `UiRole`. Avoid a monolithic `DistributedSearchManager` that owns everything.

### Network Topology Is a Contract

- A control-plane registration is not evidence of data-plane reachability. For every advertised `host:rudpPort`, identify the process that binds that UDP socket and test delivery to it from the sender's network.
- Remote control mode must not silently remove the local data-plane listener required for inbound traffic. If the remote relay owns delivery instead, define how it preserves source identity, queues inbound payloads, and routes replies.
- A fallback is real only when its wire type is emitted and handled. Do not call a method `sendRelay` or `holePunch` unless an integration test observes the expected packet type on the wire and the final payload at the target.
- Model request and response routes separately. NAT, endpoint ownership, and registry state may make one direction work while the reply path fails.
- Before claiming a multi-node feature works, exercise the exact production roles, not just same-JVM or loopback fixtures.

### rUDP Session & Multi-Hop RELAY Rules (IceBridge)

These are hard-won from multi-hop mesh E2E + adversarial review (MentisDB frostwire #873–#876), with authentication and bounds corrected by later review #1048.

- **HELLO / HELLO_ACK symmetry**: both roles must prove key possession using domain-separated, peer-bound transcripts including version, identities, fresh challenges, connection IDs and negotiated session parameters. The historical same-shape `pub + timestamp + sig(connectionId || timestamp)` exchange is insufficient; an empty HELLO_ACK is also a silent half-open session. Set `remotePub` only after the current authenticated handshake completes, not from a reusable identity record.
- **One session per peer address**: `connect(remote)` should reuse a valid `sessionsByAddress` entry without overwriting an authenticated responder session. Coordinate simultaneous open and identity replacement; address/CID lookup selects a candidate session, not proof of identity. Require authenticated path validation before migration.
- **RELAY hop auth**: validate packet integrity before acting on the frame; `frame.sourcePub` must equal the authenticated hop peer (`session.remotePub()`) where the field represents the hop. Keep logical origin identity separate. Lookup by `packet.connectionId()` (same as DATA), then any permitted address lookup, is not authentication.
- **RELAY_RESPONSE**: requires packet authentication within an authenticated session; attribute hop identity to the verified `session.remotePub()`, never spoofable header bytes, while preserving the logical origin separately. **No** unauthenticated `write()` fire-and-forget.
- **Local /poll clients**: peers that register with this process's own `host:rudpPort` drain the control inbound queue — deliver with `notifyListener` / `isLocalRudpEndpoint`, not self-UDP RELAY_RESPONSE.
- **Amplification bounds** (all required for multi-hop flood):
  - For the unfragmented RELAY path, enforce `RelayFrame.MAX_APP_PAYLOAD = RudpPacket.MAX_FRAGMENT_PAYLOAD - HEADER_LENGTH` at encode and decode, accounting for the current authenticated wire overhead; do not assume DATA fragmentation also supports RELAY.
  - Bound hop TTL and mesh fan-out using the current topology/configuration and test their boundaries; historical defaults of 3 are not universal contracts. Enforce per-peer RELAY/RELAY_RESPONSE limits plus bounded global ingress budgets.
- **Security hardening vs E2E**: removing an unauthenticated path will break tests that depended on it. After any RELAY/HELLO auth change, re-run `MultiRelayMeshSearchTest` (or three-forwarder equivalent): warm the current authenticated handshake, assert seeder is **not** on the searcher's home relay, assert signed result arrives. Never restore an insecure handshake to satisfy an old fixture.

### Wire Version Discipline

- Bumping a signed wire type (`RemoteSearchRequest`, `RemoteSearchResponse`, `IdentityRecord`) requires: version field bump, coordinated writers/readers, explicit read-path only for older versions whose authenticated contract remains sound, and a test that old maps still decode safely (or are cleanly rejected). No insecure downgrade or mandatory obsolete canonical-signature format.
- Historical example: IdentityRecord **wrote v3** (caps) but **read v1/v2** to preserve live self-ping and older peers. Recheck the current version/security contract; compatibility is not a reason to accept an insecure legacy record.

---

## 3. Concurrency & Threading Rules

### Never Block the UI Thread

- **Desktop**: All heavy ops (JNI, I/O, network, parsing, DB writes) on a background executor. Use `GUIMediator.safeInvokeLater()` for UI updates; it posts TO the EDT and does not offload work.
- **Android**: All heavy ops on a background thread. Never network or disk I/O on the main thread. Use `SystemUtils.postToHandler(HandlerThreadName.MISC, runnable)` for non-urgent work, `HandlerThreadName.HIGH_PRIORITY` for short user-tap actions (play button, open file) targeting <100 ms response. Inspect the selected helper: some handlers spawn threads rather than provide a bounded queue; priority is not a latency guarantee.
- **JNI calls to jlibtorrent must never happen on the EDT.** Always offload to a background executor.
- **Strict EDT is enabled on desktop**: `StrictEdtMode` installs a timing `EventQueue` at startup and reports any dispatch event exceeding 2 seconds. Treat every report as a defect; never wait on a latch, `Future`, DHT/JNI call, network request, disk operation, or lock with unbounded contention from the EDT.

### Desktop Executor Selection

- `DesktopParallelExecutor.execute(...)` is a four-worker bounded executor for independent, reorder-safe work such as file I/O, network fetches, media resolution, and parallel searches. Its bounded queue may reject bursts; callers that cannot drop work must handle `RejectedExecutionException`. Catch and log failures inside submitted `Runnable`s because `execute` exposes no result.
- `BackgroundQueuedExecutorService.schedule(...)` is a single-worker FIFO queue. Use it only when serialization is required for correctness: shared torrent/UI model state, ordered restore/mutation, or operations that must not overlap. Keep every task short; one slow task delays every queued GUI-background operation.
- Neither executor may mutate Swing. Snapshot EDT state before dispatching, perform blocking work in the executor, then use `GUIMediator.safeInvokeLater(...)` for the smallest possible UI update. Do not use `safeInvokeAndWait` from background work unless the call chain is proven not to depend on the waiting executor.

### Thread-Safe Singletons

- If you use singletons, make them thread-safe (double-checked locking with a `volatile` instance field, or static-holder pattern).
- Clear singleton data models before reloading to prevent stale state.
- Singletons that hold native handles (BTEngine, SessionManager) must release those handles deterministically in `LifecycleManager.onShutdown()`.

### Defensive Programming in Concurrent Contexts

- Null-check everything that crosses thread boundaries.
- Guard recoverable native load/initialization failures with `try/catch` and a safe disabled state. Example: if Python ELF loading fails on Android with a Java-visible error, log and fall back to `"<unavailable>"`. Java catches cannot stop SIGSEGV/native process crashes or make use-after-free safe; ownership and lifetime checks are still required.
- Invalidate stale state/generations before any async post. Keep in-memory removals and flag flips synchronous; perform blocking store cleanup on the owning worker with ordering/generation checks. Otherwise the callback fires on stale data. (Real bug: `DeleteDialog` race that left deleted songs in Recent/Favorites.)

---

## 4. Java Code Style (FrostWire Conventions)

### File Headers

- Java files use the standard FrostWire Apache/GPL header; follow the neighboring file's actual license and authorship. Example:
  ```java
  /*
   *     Created by Angel Leon (@gubatron)
   *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
   *
   *     Licensed under GPL v3. See LICENSE file.
   */
  ```
- Classes that are co-maintained by both gubatron and aldenml add `@author gubatron` and `@author aldenml` to the class javadoc.
- One file = one top-level public class. Inner classes only for tightly-scoped helpers.

### Imports

- No wildcard imports.
- Group: `java.*`, `javax.*`, third-party, `com.frostwire.*`. Standard IDE ordering.
- Remove unused imports in the same commit that introduces the change.

### Class & Member Style

- `public final class` on utilities and value types.
- Private constructors on utility classes (`private X() {}`).
- Fields `private final` when the class is immutable; explicit `this.x = x.clone()` for byte arrays.
- Getters on immutable POJOs return defensive clones of mutable fields (`return bytes.clone()`).
- Builder pattern for value types with >4 fields or any optional field.

### Methods

- Methods named for what they *do* at the caller's level: `upsert`, `needsRepublish`, `markPublished`. The body explains how.
- Boolean accessors: `isOpen()`, `isReadyToSubmitTask(name)`.
- Package-private statics only when package collaborators need that contract — otherwise `private`; do not widen access solely for tests.
- A test in the same package cannot directly access `private` methods. Test behavior through the existing API or a real package-level collaboration, not an invented visibility exception.

### Logging

- **Always `com.frostwire.util.Logger`** for application/library diagnostics — never `org.apache.commons.logging`, `java.util.logging`, `org.slf4j`, or `System.out`/`System.err`. Intentional headless CLI help/output is an exception, not permission to log secrets.
- Declare as `private static final Logger LOG = Logger.getLogger(MyClass.class);`
- Pass throwables as a second argument: `LOG.warn("op failed for " + id, e)`. Never concatenate the stack trace into the message.
- Don't `@SuppressWarnings` on `printStackTrace` or unused log statements — delete the dead code instead.

### Null Safety

- Null-check deserialized objects, especially at API boundaries (intents, bundles, JSON, network responses).
- Use `Optional<T>` for return values that may legitimately be absent.
- Guard Android lifecycle callbacks against null / destroyed state.
- Check for null `listData`, `srList`, and similar deserialized collections before use.

### Resource Cleanup

- **Always try-with-resources** for owned `InputStream`, `OutputStream`, `Reader`, `Writer`, `Connection`, `PreparedStatement`, `ResultSet`, cursors and HTTP responses; document borrowed-resource ownership.
- Clean up unique temp files after use, including failure and cancellation paths. See `OkHttpClientWrapper.save()` lesson: 0-byte temp files leak when `SocketException` happens before the response is written.

### Avoid Magic Numbers & Strings

- Named constants for regex patterns, buffer sizes, format signatures, table/column names, DHT key prefixes.
- When you find a string literal duplicated in 2+ files, hoist it.

### No `@SuppressWarnings("unused")` on Private Helpers

- If a private helper is unused, **delete it**. The annotation is a code smell that hides dead code.
- The compiler is right; remove the code, don't silence it.

### Reuse Before You Build

- Before writing a utility, grep the project for `com.frostwire.util.*` (Hex, Base64, UrlUtils, JsonUtils, HttpClientFactory, Ssl, StringUtils, ThreadPool, HistoHashMap, Ref, TaskThrottle, MimeDetector, …). There is almost certainly one already.
- Example: `com.frostwire.util.Hex.encode(byte[])` / `Hex.decode(String)` — don't write your own `toHex`/`fromHex`.

---

## 5. Android-Specific Rules

### minSdkVersion

- **minSdk = 26** (Android 8.0), as documented at this revision. `Build.VERSION.SDK_INT < 24` and `SDK_INT < 26` guards are **dead code** for targets whose actual minSdk is 26. Don't add them "for safety". Grep `build.gradle` for `minSdkVersion` before writing a version guard.

### Threading

- `SystemUtils.postToHandler(HandlerThreadName.MISC, () -> { ... })` for background work; verify whether this helper queues or spawns a thread, and bound/coalesce repeated submissions.
- `HandlerThreadName.HIGH_PRIORITY` for short user-tap actions (play button, open file) — target <100 ms response and avoid queuing them behind slow background tasks; never use priority as a substitute for bounded work.
- ExoPlayer: `.setLooper(handlerThread.getLooper())` is mandatory.
- OnPreferenceChangeListener callbacks run on the main thread. Calls to BTEngine, TransferManager, or ConfigurationManager that perform JNI, disk, or other blocking work must be dispatched with `postToHandler(MISC, ...)`; `return true` immediately where the existing preference contract allows it. Do not do a synchronous read-back to verify the write. Inspect actual method behavior rather than labeling every accessor a StrictMode violation.

### EditTextPreference

- EditTextPreference ALWAYS reads/writes its value as `String` via `SharedPreferences.putString()`/`getString()`. If a corresponding key is stored as `Integer` anywhere (`ConfigurationDefaults`, etc.), the preference will crash with `ClassCastException` at inflation time.
- **Rule**: numeric defaults for EditTextPreference keys are stored as `String` (e.g. `"7656"` not `7656`). Read with `Integer.parseInt(cm.getString(key))` + safe fallback.

### View Tags

- `View.setTag(int, Object)` keys must be application resource ids — a key from `View.generateViewId()` throws `IllegalArgumentException("The key must be an application-specific resource id")` (generated ids have top package byte 0x00). For idempotence/installed markers use a named wrapper class + `instanceof` on the owner field, never a generated-id tag key (NavigationViewSafety, 2026-07).

### String Resources

- New base `res/values/strings.xml` keys must be copied to **all** `values-*/strings.xml` in the same commit (English base text is the project placeholder practice; translators replace later). `AndroidStringResourceParityTest` enforces key parity across 36+ locales — 56 unpropagated IceBridge keys broke CI (2026-07). Preserve placeholders and plurals.

### Native Init

- Guard native loading/initialization (Python, ffmpeg, custom `.so`) for Java-visible failures because corrupted native binaries on user devices are a real-world occurrence. Catch expected failures, log, and disable/fall back safely. A catch block cannot recover from a native process crash; prevent unsafe calls and native lifetime violations.

### Configuration

- New code prefers **Jetpack DataStore** over SharedPreferences. ConfigurationManager migration is the standing tech-debt task.
- One source of truth for any config that lives in multiple places. SoundCloud credentials were the most recent offender (4 places drifted).

---

## 6. JNI / JLibTorrent (Native Code)

### Memory Management

- SWIG-generated objects hold native memory. Be explicit about ownership.
- Close / dispose owned native resources deterministically using the actual binding API. Do not invoke or rely on finalizers for native cleanup.
- Pair every owned resource/listener retained for `dht_put_item` with a release path on shutdown; verify ownership in the installed binding rather than assuming a DHT put itself returns a disposable handle.

### Prefer Library-Native Persistence

- If the underlying library (libtorrent / jlibtorrent) already persists the required state (e.g. `save_state_flags_t.all()`, `ip_filter`, DHT items, session state), **do not build a parallel persistence layer**. Verify the installed API's flags and coverage; do not assume it persists every example.
- Real example: removed `ip_filter.db` (149 lines) because jlibtorrent persists `ip_filter` via session state. Net-negative LoC, fewer bugs.

### DHT Primitives (Do Not Invent Semantics)

- BEP 5 `dhtAnnounce(infohash, port)` + `dhtGetPeers(infohash)` is the right tool for multi-writer rendezvous: many peers can announce themselves under the same 20-byte infohash target.
- BEP 44 immutable put (`dhtPutItem(Entry)`) is content-addressed. You cannot choose its key; production discovery needs another channel.
- BEP 44/46 mutable put (`dhtPutItem(pubkey, privkey, entry, salt)`) is single-writer. The address is derived from the publisher key and salt; it is perfect for "my latest manifest", not for "everyone writes into one registry".
- DHT targets are 20-byte SHA-1 hashes. If a design says `SHA-256(... )[:32]` for a DHT lookup key, stop and correct it before coding.

### Transport Semantics Must Be Observable

- API success means accepted delivery only when the API can prove it. If `/send` is asynchronous, return an explicit queued status and expose a failure or expiry signal; do not let callers treat HTTP 200 as peer delivery.
- Test opaque transport payloads with real source and target identities. The application layer must receive the logical requester identity, not accidentally attribute every forwarded packet to the relay.
- Bound every untrusted transport queue and reassembly buffer by count, bytes, and lifetime. Test backpressure, rejection and expiry under loss, duplicate packets, and a sender that never completes a fragmented message; never silently evict accepted work whose ACK promised retained ownership.

### Lazy Loading

- Defer expensive initialization until the user actually needs it. Example: the IP Filter table is loaded only when the panel opens, not at app startup.

### Performance & Concurrency

- For high-throughput Rust-backed code: `RwLock` vs `DashMap` tradeoffs, write batching, per-entity locking, WAL patterns. See `rust-concurrency-patterns` skill.

---

## 7. UI & User Experience

### Internationalization (I18n)

- **All user-facing strings must use `I18n.tr()`** (Desktop) or Android `strings.xml` resources.
- Never hardcode English in UI code. Search for `I18n.tr(` to verify coverage of new strings.
- `changelog.txt` entries are user-facing and need i18n awareness too.

### Skin / Theme Consistency

- Use themed components (`SkinPopupMenu`, `SkinButton`, etc.) instead of raw Swing/Android defaults.
- Test UI changes across light and dark themes.

### Graceful Degradation

- If a feature fails (blocklist download, file parse, optional plugin load), show a helpful error and let the user continue.
- Never let an optional feature crash the entire application.

---

## 8. Testing & Quality

### Tests Are Non-Negotiable

- Every public method gets at least one test. The bonus is when the test catches the bug.
- Coverage shape:
  - **Happy path** — the obvious use
  - **Edge cases** — empty input, malformed data, max values, nulls, very long strings
  - **I/O paths** — file open/close failure, partial read, encoding edge cases
  - **Format detection heuristics** — when parsing user-supplied formats
  - **Concurrency** — when the type crosses thread boundaries
- **Real-world fixtures** are valuable. Use actual `iblocklist.com` downloads, real Bitsearch responses, real magnet URIs captured as representative local fixtures; do not require live services for deterministic regression tests. Synthetic data misses the bugs that ship.

### JUnit 5

- Project standard where supported: **JUnit 5 (Jupiter)**. Follow the affected module's existing runner; Android tests may require JUnit 4/Robolectric (`@RunWith(RobolectricTestRunner.class)`). Do not impose a blanket JUnit 4 ban or add an unnecessary vintage engine.
- For Jupiter, `@Test` methods are `void`, no exceptions declared — use `assertThrows` instead of `@Test(expected=...)`. Follow existing Android runner conventions where required.
- `@TempDir` and `Files.createTempDirectory()` for JVM filesystem tests; clean up in `@AfterEach`. Use the module's runner lifecycle and shared-code API policy on Android.
- `@ExtendWith(LocalDhtCluster.class)` for distributed DHT integration tests.

### Bug Fix Pattern

1. Reproduce and understand the **root cause**. Don't fix symptoms.
2. Write the **smallest possible fix**. One line is better than ten.
3. Add a regression test that would have caught the original bug.
4. Update `changelog.txt` for user-facing fixes (Desktop and Android have separate changelogs).
5. Verify the build passes on **all targets** the change touches.

### Compile Before Commit

- Desktop, from `desktop/`: `./gradlew compileJava` (compile only) and `./gradlew test` (full unit suite)
- Android, from `android/`: `./gradlew compilePlus1DebugJavaWithJavac` and `./gradlew testPlus1DebugUnitTest`; verify current flavor/task names.
- Fix compiler warnings; don't suppress them.
- In a shared tree, only the coordinator runs Gradle, including tests, compile, formatting and dependency tasks. Workers request these gates; passing gates never grants permission to commit or push.

### Multi-Node / Mesh Tests

- Prefer real multi-process fixtures (with `IceBridgeServer` instances on free ports) over same-manager fakes when claiming multi-hop works. Multiple instances in one JVM are not multi-process evidence; record which fixture actually ran.
- After mesh link (`RelayMesh.linkFully`), **warm** sessions (direct `/send` or DATA through the authenticated path) so the current handshake settles before RELAY flood; HELLO_ACK arrival alone is not proof of completed mutual authentication.
- Assert **topology**, not only the end result: e.g. target peer absent from searcher's home registry so the path must multi-hop.
- DHT discovery: snapshot jlibtorrent peer lists (`toArray` / copy) before iterating — live lists mutate on alert threads (`ConcurrentModificationException`).

### Tests Green Before Each Granular Push

When the user explicitly authorizes commit/push and requires "tests must pass to commit and push":

1. Implement the smallest coherent logical slice; keep coupled API/wire/caller/test updates together when splitting would break the build or contract.
2. Have the coordinator run the **narrowest adequate** suite (e.g. `./gradlew test --tests 'com.frostwire.search.relay.*'` from `desktop/`), widened to full module/both targets for shared changes.
3. Commit that slice only if green and authorized.
4. Push that commit only if authorized (granular push), then start the next slice.

Never batch failed tests into a "fix later" commit. Never commit `desktop/GROK_RESUME_SESSION` or other agent scratch files.

### Test Isolation & Test-Code Quality

- **Process-wide singletons** (`IceBridgeTopology`, settings factories) must be reset in `@BeforeEach`/`@AfterEach` (`resetToDefaults()`), or equivalent hooks for the module's runner. JVM forks run many test classes; leaked singleton state = order-dependent failures that only appear on CI.
- **A test's extractor is code too** — a greedy regex `[^>]*` swallows the `/` of self-closing XML tags and hides every following key from parity checks (AndroidStringResourceParityTest blind spot, 2026-07). Use `[^>]*?` and test the extractor itself.
- **Spotless ratchet expectation** — verify the current ratchet (historically `origin/master`): touching a legacy file may format the WHOLE file. Expect large diffs; keep mechanical reformatting separate from product logic, not hidden inside an unrelated test/docs commit. Only the coordinator runs formatting in a shared tree.

### Benchmarks & Simulations

- **Common random numbers** — every candidate in a comparative run gets the SAME seed(s) so the parameter is the only variable. Per-candidate seeds put each candidate on a different random network and the winner becomes seed luck (TopologyAutoResearch, 2026-07).
- **Check saturation before trusting rankings** — if every candidate visits the whole network, the parameter only discriminates cost, not coverage; draw recall conclusions only from networks big enough for the parameter to gate coverage.

---

## 9. Git & Commit Hygiene

### Granular, Focused Commits

- **One logical change per commit, only when authorized.** Example: "remove `ip_filter.db` persistence" and "move JNI calls off EDT" were split into two separate commits even though they were done together. Keep tightly-coupled edits in one buildable commit rather than splitting arbitrarily.
- A branch with 35+ granular commits is normal for a major sweep. Squashing cosmetic / WIP commits requires explicit history-rewrite permission; never do it automatically before pushing.
- Typical mesh security sweep review order: protocol primitive → manager policy → wire version → tests → changelog. This is not a mandate to split dependent changes into broken commits.

### Commit Message Format

```text
[desktop] short imperative description (#issue)
[android] short imperative description (#issue)
[common]  short imperative description (#issue)
[all]     short imperative description
```

- **Prefix with scope tag in brackets**: `[desktop]`, `[android]`, `[common]`, or `[all]`.
- **Reference issue numbers** so GitHub auto-links them: `(#1291)`.
- **Imperative mood**: "Fix NPE" not "Fixed NPE" or "Fixes NPE".
- First line ≤ 72 chars. Body wraps at 72. Use a blank line between summary and body.

### Clean History

- Git write operations require explicit permission: staging, commits, pushes, tags, checkout/branch changes, pulls/fetches, rebases, squashes and other history rewriting. Read-only `git show`, `status`, `diff` and `log` are inspection, not authorization to mutate the tree or history.
- Only when rewriting a remote branch is explicitly approved, prefer `git push --force-with-lease` (not `--force`); still inspect remote tracking and coordinate other contributors first. Never automatically force-push or amend.
- Prefer rebasing a feature branch on `master` rather than merging `master` into it when the agreed workflow permits it. The historical sequence below is an example requiring approval for each operation and a safe worktree, not an instruction to run it automatically:
  ```bash
  git fetch origin master
  git checkout my-branch
  git rebase origin/master
  ```

### Build Artifacts Lie by Omission

- `./gradlew` "up-to-date" means the artifact matches THAT checkout's sources — not that the checkout is current, and not that a rebuild happened. When verifying a deploy, the artifact's own version banner/output is the witness: a stale EC2 icebridge.jar was caught by its missing software-version banner block despite "BUILD SUCCESSFUL, 3 up-to-date" (2026-07). With permission, update the checkout; have the coordinator rebuild and re-check the banner.

### Feature Branches

- All pull requests come from a feature branch on your fork.
- Name descriptively: `issue-1291`, `fix-ip-filter-edt-crash`, `media-player-update`. Lowercase, hyphen-separated.
- One branch = one logical unit. Don't mix tech-debt and feature work.

---

## 10. Maintainer's Checklist (What to Update per Change)

Before claiming a change is complete, walk this list. Most rows are per-change; some are per-release. Commit/release rows are conditional on explicit authorization, not permission to perform those operations.

### Per Commit

- [ ] Compiles clean on the target it touches
- [ ] All new public methods have tests
- [ ] All new user-facing strings are i18n'd
- [ ] Heavy work is off the UI thread
- [ ] Native resources are deterministically cleaned up
- [ ] Commit message uses scope prefix `[android]` / `[desktop]` / `[common]` / `[all]`
- [ ] Commit subject is imperative mood
- [ ] No `@SuppressWarnings("unused")` on private helpers — deleted the dead code instead
- [ ] No diagnostic `System.out` / `System.err` / `printStackTrace` — using `com.frostwire.util.Logger`; intentional CLI help/output only
- [ ] No magic numbers/strings — extracted to named constants
- [ ] No reinvented utility — `grep com.frostwire.util.*` before writing helpers
- [ ] No wildcard imports

### Per User-Facing Change

- [ ] `desktop/changelog.txt` updated (if Desktop)
- [ ] `android/changelog.txt` updated (if Android)
- [ ] Most important change listed first in the changelog block
- [ ] Visual changes tested in light + dark themes

### Per Release

- [ ] Build verified on all targets (Desktop, Android, JLibTorrent native for affected arch)
- [ ] PR description explains the issue and the **how** of the fix
- [ ] No formatting noise mixed with product fixes
- [ ] No commits that bundle tech-debt with product behavior
- [ ] Branch/base status reviewed against current `master`; any rebase/history rewrite separately authorized and coordinated

### Per Security-Sensitive Change

- [ ] No private keys, certificates (`.pem`, `.crt`, `.key`, `.p12`), API secrets committed
- [ ] `git status`, `git diff`, `git log --oneline -10`, and full staged diff plus `git diff --cached --stat` reviewed before every authorized commit; stage only intended files
- [ ] Threat model updated if a new attack surface appears
- [ ] Web-of-Trust root keys stored outside the repo if added
- [ ] New or changed rUDP packet types: packet integrity plus session auth, size/rate bounds, and a rejection test for unauthenticated senders
- [ ] Multi-hop flood paths: hop TTL, fan-out, payload cap, and rate limit all present
- [ ] MentisDB `frostwire` chain: search before recording `LessonLearned` / `Constraint` for non-obvious security fixes (agent_id `gubatron`)

---

## 11. Pull Request & Review Culture

### PR Description

- Explain **what** issue you're fixing and **how** you fixed it in detail.
- If it's too hard to explain, simplify the solution.
- Don't include formatting noise — it makes review impossible for a small team.
- Reference the issue number in the body, not just the title.

### Reviewer Checklist

A reviewer should be able to assess these from the diff and recorded verification evidence; source inspection alone cannot prove that a build passes:

- [ ] Build passes locally, with exact command/environment/result evidence
- [ ] No formatting noise in the diff
- [ ] Tests cover happy + edge cases
- [ ] UI strings are i18n'd
- [ ] Heavy work is off the main/UI thread
- [ ] Native resources are cleaned up deterministically
- [ ] `changelog.txt` updated if user-facing
- [ ] Commit messages follow `[scope] imperative (#issue)`
- [ ] No new public API is added without a test

A reviewer is encouraged to push back on **complexity**, not just correctness. "Can this be smaller?" is a valid question.

---

## 12. Dependency & Build Hygiene

- Update Gradle plugins, build tools, and third-party SDKs **one at a time**. Verify builds on all targets after each bump.
- Avoid duplicate resources in `build.gradle`.
- Do not introduce new modules or major dependencies without prior discussion in the issue tracker.
- Build verification matrix (coordinator-only Gradle in a shared tree; verify flavor/task names against current build files):
  - Desktop, from `desktop/`: `./gradlew compileJava` then `./gradlew test`, plus `./gradlew spotlessCheck`
  - Android, from `android/`: `./gradlew assembleDebug` (or the affected explicit flavor task), `./gradlew compilePlus1DebugJavaWithJavac`, then `./gradlew testPlus1DebugUnitTest`
  - JLibTorrent: native builds must pass on macOS (arm64 + x86_64), Linux (x86_64 + arm64), Windows (x86_64), Android (4 arches: armeabi-v7a, arm64-v8a, x86, x86_64; verify the current supported ABI set)
- Shared changes need both targets; native changes need the affected platform/architecture matrix. Record unavailable gates rather than claiming cross-platform success.

---

## 13. What This Skill Is Not

- **Not** a substitute for reading the existing code in the area you're changing. Read the file you're editing end-to-end before writing a line.
- **Not** exhaustive. It's a starting checklist. Real review comments from gubatron and aldenml are the source of truth — capture them in `mentisdb` so they survive.
- **Not** a substitute for design docs. Large features need a `DESIGN_*.md` first (see `DESIGN_RELAY_REGISTRY.md` for the template).
- **Not** a code-size budget for accumulated knowledge. Preserve useful skill sections, examples, triggers and checklists; correct wrong lines and add qualified lessons rather than condensing the document wholesale (user correction #1108).

---

## 14. Companion Skills & Resources

- **`mentisdb`** — durable semantic memory for cross-session learning. Use it to capture lessons learned, not for transient state.
- **`systematic-debugging`** — for any non-trivial bug, before proposing a fix.
- **`verification-before-completion`** — evidence before assertions, always.
- **`test-driven-development`** — write the test first, watch it fail, then implement.
- **`subagent-driven-development`** — for large work, split into independent tasks with two-stage review.
- **`dispatching-parallel-agents`** — when 2+ independent tasks can run in parallel.
- **`engineering-pipeline`** — MentisDB release engineering pipeline (parallel, verified, documented).
- **`frostwire-code-reviewer`** — FrostWire correctness, lifecycle, security, compatibility and house-style review.
- **`frostwire-performance-reviewer`** — measured HotSpot/ART performance, delivered-work accounting and resource-lifetime review.

---

## 15. Newer Defensive & Shared-Memory Contracts

Retained from the later consolidation and review #1048 (2026-09-05), separately from the original section hierarchy. These are implementation/review requirements, not a claim that the current production tree or a rollout has passed them.

### Shared-Brain Workflow

- Preserve accumulated skill knowledge: compare revisions against the previous full text, retain useful examples, triggers, tables, and specialized guidance, and correct inaccurate rules in place. Add new lessons without replacing detailed guidance with summaries. Code line-minimization is not a documentation goal; justify any substantive removal explicitly.
- Use MentisDB chain `frostwire` and existing identity `gubatron`. Search related findings, current constraints, API decisions, and ownership before editing; verify historical claims against current source.
- One owner per file in a shared tree. Publish API contracts before changing them: signatures, units, ownership, threading, wire versions, failure and cancellation semantics. Coordinate callers instead of guessing another agent's API.
- Search before appending; publish milestone `Summary` checkpoints with evidence, changed files, blockers, remaining work, and references. Checkpoint before compaction or handoff; record non-obvious corrections rather than duplicate old lessons.
- Only the coordinator runs Gradle in a shared tree, including compile, tests, formatting, and dependency tasks. Workers report requested gates; concurrent Gradle runs can corrupt shared results.
- Preserve unrelated changes. No commits, pushes, tags, rebases, or history rewriting without explicit permission. When authorized, use one coherent change per commit and `[android]`, `[desktop]`, `[common]`, or `[all]` imperative subjects; inspect staged content for secrets.
- Security work is defensive: local regression fixtures and bounded behavior checks only. Do not create autonomous offensive workflows, exploit reproductions, or live attacks.

### API, Platform And Native Boundaries

- Keep methods cohesive; concise comments explain non-obvious invariants, not assignments. Document public API threading, units, errors, and lifecycle. Name constants for real shared contracts and meaningful bounds, not coincidentally equal values. Make defensive copies of mutable arrays/collections at public boundaries; document buffer ownership.
- Read current Gradle source/target, minSdk, desugaring, and dependency declarations. Java 17 includes sealed types; source-language support does not prove Android runtime/API availability. Check the actual toolchain and minimum-device API separately.
- `common/` compiles for both platforms: no Swing/AWT, desktop HTTP server/client APIs, JDBC implementation, desktop paths, or JVM subprocess launchers. Inject storage interfaces and `File`/streams. `java.nio.file.Files` exists from Android API 26, but this repository avoids `java.nio.file` in shared code by policy, not because API 26 lacks it.
- Verify actual BouncyCastle and other dependencies on both targets; do not label them desktop-only from stale memory. Check API/version compatibility, license, R8/reflection/JNI rules, and artifact size before adding dependencies.
- Check timeout units in the installed dependency's source or bytecode. For example, reviewed jlibtorrent 2.0.12.9 mutable-item waits used seconds, not milliseconds. Use explicit conversion and one monotonic absolute deadline covering queueing, discovery, sends, reads, retries, and completion.

### Threading And Lifecycle

- Snapshot UI state, compute on a worker, then post a small generation-checked update. Do not wait on an executor that needs the waiting thread. StrictEdtMode reports are defects, not an acceptable latency budget.
- On Android, coalesce refreshes and cancel them on navigation. Use lifecycle-appropriate WorkManager/foreground-service ownership, not a new worker every timer tick; a `SystemUtils` helper name does not guarantee bounded thread creation.
- Keep transport/event-loop/poller callbacks light. Hand off admitted work to bounded executors; never perform DNS, SQL, JNI, signing bursts, or synchronous sends on the sole response poller.
- Keep cross-component locks away from I/O/JNI/callbacks; isolate required DB transactions on workers. Publish state atomically, invalidate stale generations before asynchronous cleanup, and recheck cancellation before starting a download or applying a sorted snapshot.
- Own every listener, timer, socket, database, child process, and session alias. Close idempotently, including partial-start failures; remove mappings only if still owned. Repeated start/stop and identity replacement must not grow retained resources or reuse old identity-bound callbacks.
- Separate incidental service recreation from explicit stop, opt-out, and network restrictions. Explicit stop must stop owned public participation and prevent supervisor respawn. Gate sockets/advertising before startup; do not unconditionally keep mesh work alive after service destruction.

### Defensive Protocol Contracts

- Prefer established secure transports. Authenticate both handshake roles with domain-separated, peer-bound transcripts including version, identities, challenges, and negotiated session parameters. A signed reusable identity record is not proof that the responding endpoint possesses the key.
- A session/CID lookup is not packet authentication. Protect packet type, sequence, ACK, payload, routing-sensitive fields, and migration before state changes; reject impossible ACKs, identity changes, and uncorrelated introductions. Preserve origin identity separately from authenticated hop identity.
- Coordinate incompatible wire/canonical-signature changes across writers and readers with explicit version rejection. No insecure downgrade to keep old peers/tests passing. Preserve compatibility only where the authenticated contract remains sound.
- Freshness is not replay protection. Use bounded authenticated requester/nonce replay state and loop rejection; preserve the origin signature and authenticate the maximum routing budget. Only successfully admitted requests may query, serve, or forward. Define TTL semantics at sender and receiver; never preserve useless TTL-zero forwarding merely because an old test expects it.
- Bound cardinality, count, bytes, and lifetime before allocation or work: sessions, aliases, limiter keys, sockets, queues, fragments, streams, caches, and retries. Apply cheap global/ingress budgets before expensive verification; attribute requester quotas only after authentication. Known-peer retries and every alternative admission path still consume budgets.
- Cap bytes while reading HTTP/import/frame streams, before whole-body materialization, decoding, parsing, or native construction. Content-Length is not sufficient. Check aggregate decoded size/count as well as individual fields; reject overflow. Neither `Math.abs(Long.MIN_VALUE)` nor manual negation is safe; validate numeric domains and use checked arithmetic or overflow-safe bounded comparisons.
- Define accepted, delivered, ACKed, processed, rejected, expired, and retryable outcomes. Reserve bounded ownership before ACK at the documented layer; ACK is not automatically application delivery. Re-ACK accepted duplicates without redelivery. Never silently evict accepted work to improve throughput.
- Give each logical message an authoritative queue owner. Optional shared/identity mirrors must neither retain undrainable copies nor gate unrelated consumers. Reliable polling needs explicit lease/ACK/retry behavior; HTTP success alone is not recipient delivery.
- Track streams by request and holder: bounded chunk indices, rows/bytes, duplicates/conflicts, contiguous completion, and cancellation. One holder's final is not whole-branch completion. Keep correlation until branch completion or overall deadline/budget; stop owned calls and wake waiters on cancellation.
- Verify bounded torrent metadata's actual v1/v2/hybrid infohash against the original request independently of the signer and payload digest. Bind error shape/digest and freshness to the request. Check sharing authorization before provider AND cache access.

### Persistence, Privacy, And Deployment

- Apply one public-share policy to search, browse, announcements, metadata, cache hits, and queued indexing. Private, removed, inactive, or metadata-only entries must not become public by an alternate path; withdraw promptly without restart and prevent stale jobs from resurrecting entries.
- Keep primary rows, file rows, and FTS postings transactionally consistent across replace/delete/rowid reuse. Install each Android trigger separately, repair persisted stale postings through migration, and test real FTS state. Limit distinct torrents before joined file rows hide results; quote Unicode FTS tokens and escape LIKE wildcards without ASCII-only stripping.
- Bound stored row bytes before parsing/storage, avoid huge `files_json` in summary reads, and test device CursorWindow limits. Bind karma/history rows to their owner; feed actual publisher output through the reader and preserve local reputation across registry refresh.
- Keep control private/admin-scoped unless principal isolation is explicitly implemented. Require TLS or an authenticated tunnel for non-loopback credentials. Never put tokens, private keys, or mnemonics in argv, logs, or repository files; use a restricted non-argv handoff.
- Root owns executable artifacts and configuration; the service owns only separate data/state. Never source service-writable environment files, especially during privileged upgrades; parse allowlisted configuration as data. Build unprivileged, verify service ownership before process cleanup, and leave unrelated listeners alone.
- Check effective CLI/env/default precedence and artifact identity, not only Gradle's up-to-date label. Derive service memory/task limits from heap plus direct/native overhead; increasing session or FD caps is not a resource-safety fix.

### Verification And Delivery Evidence

- Add focused regression tests with representative local fixtures, edge values, failures, and concurrency. Reset singleton state and close fixtures. Source-string tests and mocks do not prove native, FTS, scheduling, or device behavior.
- Defensive protocol tests assert rejection without side effects, bounded resource retention, accepted-work accounting, real multi-fragment retry, multi-holder/reordered completion, and cancellation. Test exact production roles/routes and listener-before-send ordering without live attacks.
- The coordinator selects gates by change width using sections 8 and 12. Shared constants/wire helpers require wider suites and both targets. Do not dismiss a current failure merely because its test was historically flaky.
- Review actual assertions, not test names. Record exact commands, environment, counts, failures/skips, and untested paths. A review or green loopback suite is not EC2 capacity proof: count delivered work and resource plateaus using the performance skill. Never claim all findings fixed from edits or a partial suite.

---

*Maintained by `gubatron` on the FrostWire mentisdb chain. Replaces the legacy `AGENTS.md` at the repo root. When a commit is authorized, update this file in a `[all]` commit; reference it from any new `DESIGN_*.md` so contributors find it. Original expansion retained: boundary-value contracts, constant-change audit, suite-width rule, CRN benchmarks, Android setTag/strings rules, test isolation, artifact-banner verification (MentisDB #899–#903, 2026-07). Restored from ce69af1ef with surgical safety corrections and separate review #1048/shared-memory additions following user correction #1108 (2026-09-05).*
