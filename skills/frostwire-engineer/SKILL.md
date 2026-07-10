---
name: frostwire-engineer
description: FrostWire Desktop + Android + JLibTorrent engineering practices. Use when writing, reviewing, or refactoring Java/Kotlin code in the FrostWire repo — enforces gubatron + aldenml house style: KISS, DRY, minimum-scope, composition-over-inheritance, granular commits with [android]/[desktop]/[common]/[all] prefixes, off-EDT/main-thread, JUnit 5 tests, com.frostwire.util.Logger, try-with-resources, i18n, Apache/GPL headers, defensive byte[] copies.
triggers:
  - frostwire
  - commit message
  - [android]
  - [desktop]
  - [common]
  - [all]
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
---

# FrostWire Engineer

> Combined house style of **gubatron** (project owner, lead developer) and **aldenml** (co-founder, jlibtorrent architect), distilled from the FrostWire Desktop, Android, and JLibTorrent codebases. Replaces the legacy `AGENTS.md` at the repo root.

If you only read one section, read **§1 Mantras** and **§10 Maintainer's Checklist**.

---

## 1. Quick-Reference Mantras

When in doubt, apply the closest mantra. They are not slogans — each one maps to a real review comment we have shipped.

| Mantra | When to apply |
|--------|---------------|
| **"Fail closed, not crashed"** | Native code, deserialization, network calls — wrap and fall back |
| **"Off the EDT / off the main thread"** | JNI, I/O, parsing, heavy computation, network, DB writes |
| **"jlibtorrent already handles this"** | Before building custom persistence (e.g. `ip_filter`, session state) |
| **"Delete code, don't hoard it"** | Refactors, cleanup, removing dead layers — net-negative LoC is good |
| **"One change, one commit"** | Git history hygiene — split even tightly-coupled work |
| **"If you can't explain it, simplify it"** | Design review, PR description — explanation friction = design friction |
| **"Scope to the minimum"** | Variable lifetime, visibility, mutability |
| **"Real fixtures, real tests"** | Test data — use actual `iblocklist.com` files, real Bitsearch responses |
| **"DRY first, abstract later"** | Two call sites? Tolerable. Three? Extract. Don't preemptively abstract. |
| **"Compose, don't extend"** | Default to wrapping/composition; only `extends` for abstract types |
| **"One util to rule them all"** | Before writing `toHex`/`fromBase64`/etc., grep `com.frostwire.util.*` |
| **"Check the primitive before designing the abstraction"** | DHT, JNI, SQLite, Android APIs — verify real API semantics first |
| **"Headless first when the feature is network-native"** | Relay/search protocols must run without Swing/Android UI |

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

---

## 3. Concurrency & Threading Rules

### Never Block the UI Thread

- **Desktop**: All heavy ops (JNI, I/O, network, parsing, DB writes) on a background executor. Use `GUIMediator.safeInvokeLater()` for UI updates.
- **Android**: All heavy ops on a background thread. Never network or disk I/O on the main thread. Use `SystemUtils.postToHandler(HandlerThreadName.MISC, runnable)` for non-urgent work, `HandlerThreadName.HIGH_PRIORITY` for user-tap actions (play button, open file) that need <100 ms response.
- **JNI calls to jlibtorrent must never happen on the EDT.** Always offload to a background executor.
- **Strict EDT is enabled on desktop**: `StrictEdtMode` installs a timing `EventQueue` at startup and reports any dispatch event exceeding 2 seconds. Treat every report as a defect; never wait on a latch, `Future`, DHT/JNI call, network request, disk operation, or lock with unbounded contention from the EDT.

### Desktop Executor Selection

- `DesktopParallelExecutor.execute(...)` is a four-worker bounded executor for independent, reorder-safe work such as file I/O, network fetches, media resolution, and parallel searches. Its bounded queue may reject bursts; callers that cannot drop work must handle `RejectedExecutionException`. Catch and log failures inside submitted `Runnable`s because `execute` exposes no result.
- `BackgroundQueuedExecutorService.schedule(...)` is a single-worker FIFO queue. Use it only when serialization is required for correctness: shared torrent/UI model state, ordered restore/mutation, or operations that must not overlap. Keep every task short; one slow task delays every queued GUI-background operation.
- Neither executor may mutate Swing. Snapshot EDT state before dispatching, perform blocking work in the executor, then use `GUIMediator.safeInvokeLater(...)` for the smallest possible UI update. Do not use `safeInvokeAndWait` from background work unless the call chain is proven not to depend on the waiting executor.

### Thread-Safe Singletons

- If you use singletons, make them thread-safe (double-checked locking or static-holder pattern).
- Clear singleton data models before reloading to prevent stale state.
- Singletons that hold native handles (BTEngine, SessionManager) must release those handles deterministically in `LifecycleManager.onShutdown()`.

### Defensive Programming in Concurrent Contexts

- Null-check everything that crosses thread boundaries.
- Wrap potentially crash-prone native code in `try/catch` so the app **fails closed, not crashed**. Example: if Python ELF init fails on Android, log and fall back to `"<unavailable>"` — never crash on every startup.
- Synchronous data cleanup (store removals, flag flips) MUST happen before any async post. Otherwise the callback fires on stale data. (Real bug: `DeleteDialog` race that left deleted songs in Recent/Favorites.)

---

## 4. Java Code Style (FrostWire Conventions)

### File Headers

- Java files use the standard FrostWire Apache/GPL header:
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
- Package-private statics only when the test in the same package needs them — otherwise `private`.
- Don't expose package-private statics just for testing convenience; make them `private` and let the test live in the same package.

### Logging

- **Always `com.frostwire.util.Logger`** — never `org.apache.commons.logging`, `java.util.logging`, `org.slf4j`, or `System.out`/`System.err`.
- Declare as `private static final Logger LOG = Logger.getLogger(MyClass.class);`
- Pass throwables as a second argument: `LOG.warn("op failed for " + id, e)`. Never concatenate the stack trace into the message.
- Don't `@SuppressWarnings` on `printStackTrace` or unused log statements — delete the dead code instead.

### Null Safety

- Null-check deserialized objects, especially at API boundaries (intents, bundles, JSON, network responses).
- Use `Optional<T>` for return values that may legitimately be absent.
- Guard Android lifecycle callbacks against null / destroyed state.
- Check for null `listData`, `srList`, and similar deserialized collections before use.

### Resource Cleanup

- **Always try-with-resources** for `InputStream`, `OutputStream`, `Reader`, `Writer`, `Connection`, `PreparedStatement`, `ResultSet`.
- Clean up temp files after use, including the failure path. See `OkHttpClientWrapper.save()` lesson: 0-byte temp files leak when `SocketException` happens before the response is written.

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

- **minSdk = 26** (Android 8.0). All `Build.VERSION.SDK_INT < 24` and `SDK_INT < 26` guards are **dead code**. Don't add them "for safety". Grep `build.gradle` for `minSdkVersion` before writing a version guard.

### Threading

- `SystemUtils.postToHandler(HandlerThreadName.MISC, () -> { ... })` for background work.
- `HandlerThreadName.HIGH_PRIORITY` for user-tap actions (play button, open file) — these need <100 ms response and must not queue behind slow background tasks.
- ExoPlayer: `.setLooper(handlerThread.getLooper())` is mandatory.
- OnPreferenceChangeListener callbacks run on the main thread. Any call to BTEngine, TransferManager, or ConfigurationManager inside such a listener is a StrictMode violation. Dispatch with `postToHandler(MISC, ...)` and `return true` immediately — do not do a synchronous read-back to verify the write.

### EditTextPreference

- EditTextPreference ALWAYS reads/writes its value as `String` via `SharedPreferences.putString()`/`getString()`. If a corresponding key is stored as `Integer` anywhere (`ConfigurationDefaults`, etc.), the preference will crash with `ClassCastException` at inflation time.
- **Rule**: numeric defaults for EditTextPreference keys are stored as `String` (e.g. `"7656"` not `7656`). Read with `Integer.parseInt(cm.getString(key))` + safe fallback.

### Native Init

- Wrap `try/catch` around native initialization (Python, ffmpeg, custom `.so`) because corrupted native binaries on user devices are a real-world occurrence. Catch, log, fall back — never crash on every startup.

### Configuration

- New code prefers **Jetpack DataStore** over SharedPreferences. ConfigurationManager migration is the standing tech-debt task.
- One source of truth for any config that lives in multiple places. SoundCloud credentials were the most recent offender (4 places drifted).

---

## 6. JNI / JLibTorrent (Native Code)

### Memory Management

- SWIG-generated objects hold native memory. Be explicit about ownership.
- Close / dispose / finalize native resources deterministically when possible. Do not rely solely on finalizers for native cleanup.
- Pair every `dht_put_item` with a release path on shutdown.

### Prefer Library-Native Persistence

- If the underlying library (libtorrent / jlibtorrent) already persists state (e.g. `save_state_flags_t.all()`, `ip_filter`, DHT items, session state), **do not build a parallel persistence layer**. Trust the library.
- Real example: removed `ip_filter.db` (149 lines) because jlibtorrent persists `ip_filter` via session state. Net-negative LoC, fewer bugs.

### DHT Primitives (Do Not Invent Semantics)

- BEP 5 `dhtAnnounce(infohash, port)` + `dhtGetPeers(infohash)` is the right tool for multi-writer rendezvous: many peers can announce themselves under the same 20-byte infohash target.
- BEP 44 immutable put (`dhtPutItem(Entry)`) is content-addressed. You cannot choose its key; production discovery needs another channel.
- BEP 44/46 mutable put (`dhtPutItem(pubkey, privkey, entry, salt)`) is single-writer. The address is derived from the publisher key and salt; it is perfect for "my latest manifest", not for "everyone writes into one registry".
- DHT targets are 20-byte SHA-1 hashes. If a design says `SHA-256(... )[:32]` for a DHT lookup key, stop and correct it before coding.

### Transport Semantics Must Be Observable

- API success means accepted delivery only when the API can prove it. If `/send` is asynchronous, return an explicit queued status and expose a failure or expiry signal; do not let callers treat HTTP 200 as peer delivery.
- Test opaque transport payloads with real source and target identities. The application layer must receive the logical requester identity, not accidentally attribute every forwarded packet to the relay.
- Bound every untrusted transport queue and reassembly buffer by count, bytes, and lifetime. Test eviction under loss, duplicate packets, and a sender that never completes a fragmented message.

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
- **Real-world fixtures** are valuable. Use actual `iblocklist.com` downloads, real Bitsearch responses, real magnet URIs. Synthetic data misses the bugs that ship.

### JUnit 5

- Project standard: **JUnit 5 (Jupiter)**. No JUnit 4 vintage engine for new tests.
- `@Test` methods are `void`, no exceptions declared — use `assertThrows` instead of `@Test(expected=...)`.
- `@TempDir` and `Files.createTempDirectory()` for filesystem tests; clean up in `@AfterEach`.
- `@ExtendWith(LocalDhtCluster.class)` for distributed DHT integration tests.

### Bug Fix Pattern

1. Reproduce and understand the **root cause**. Don't fix symptoms.
2. Write the **smallest possible fix**. One line is better than ten.
3. Add a regression test that would have caught the original bug.
4. Update `changelog.txt` for user-facing fixes (Desktop and Android have separate changelogs).
5. Verify the build passes on **all targets** the change touches.

### Compile Before Commit

- Desktop: `./gradlew compileJava` (compile only) and `./gradlew test` (full unit suite)
- Android: `./gradlew compilePlus1DebugJavaWithJavac` and `./gradlew testPlus1DebugUnitTest`
- Fix compiler warnings; don't suppress them.

---

## 9. Git & Commit Hygiene

### Granular, Focused Commits

- **One logical change per commit.** Example: "remove `ip_filter.db` persistence" and "move JNI calls off EDT" were split into two separate commits even though they were done together.
- A branch with 35+ granular commits is normal for a major sweep. Squash only cosmetic / WIP commits before pushing.

### Commit Message Format

```
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

- Squash cosmetic / WIP commits before pushing.
- Force-push with lease: `git push --force-with-lease` (not `--force`) when rewriting history.
- **Never merge `master` into your feature branch.** Rebase instead:
  ```bash
  git fetch origin master
  git checkout my-branch
  git rebase origin/master
  ```

### Feature Branches

- All pull requests come from a feature branch on your fork.
- Name descriptively: `issue-1291`, `fix-ip-filter-edt-crash`, `media-player-update`. Lowercase, hyphen-separated.
- One branch = one logical unit. Don't mix tech-debt and feature work.

---

## 10. Maintainer's Checklist (What to Update per Change)

Before claiming a change is complete, walk this list. Most rows are per-change; some are per-release.

### Per Commit

- [ ] Compiles clean on the target it touches
- [ ] All new public methods have tests
- [ ] All new user-facing strings are i18n'd
- [ ] Heavy work is off the UI thread
- [ ] Native resources are deterministically cleaned up
- [ ] Commit message uses scope prefix `[android]` / `[desktop]` / `[common]` / `[all]`
- [ ] Commit subject is imperative mood
- [ ] No `@SuppressWarnings("unused")` on private helpers — deleted the dead code instead
- [ ] No `System.out` / `System.err` / `printStackTrace` — using `com.frostwire.util.Logger`
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
- [ ] Branch is rebased on current `master`, not merged

### Per Security-Sensitive Change

- [ ] No private keys, certificates (`.pem`, `.crt`, `.key`, `.p12`), API secrets committed
- [ ] `git diff --cached --stat` reviewed before every commit
- [ ] Threat model updated if a new attack surface appears
- [ ] Web-of-Trust root keys stored outside the repo if added

---

## 11. Pull Request & Review Culture

### PR Description

- Explain **what** issue you're fixing and **how** you fixed it in detail.
- If it's too hard to explain, simplify the solution.
- Don't include formatting noise — it makes review impossible for a small team.
- Reference the issue number in the body, not just the title.

### Reviewer Checklist

A reviewer should be able to tick these without running the code:

- [ ] Build passes locally
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
- Build verification matrix:
  - Desktop: `./gradlew compileJava` then `./gradlew test`
  - Android: `./gradlew assembleDebug` then `./gradlew testPlus1DebugUnitTest`
  - JLibTorrent: native builds must pass on macOS (arm64 + x86_64), Linux (x86_64 + arm64), Windows (x86_64), Android (4 arches)

---

## 13. What This Skill Is Not

- **Not** a substitute for reading the existing code in the area you're changing. Read the file you're editing end-to-end before writing a line.
- **Not** exhaustive. It's a starting checklist. Real review comments from gubatron and aldenml are the source of truth — capture them in `mentisdb` so they survive.
- **Not** a substitute for design docs. Large features need a `DESIGN_*.md` first (see `DESIGN_RELAY_REGISTRY.md` for the template).

---

## 14. Companion Skills & Resources

- **`mentisdb`** — durable semantic memory for cross-session learning. Use it to capture lessons learned, not for transient state.
- **`systematic-debugging`** — for any non-trivial bug, before proposing a fix.
- **`verification-before-completion`** — evidence before assertions, always.
- **`test-driven-development`** — write the test first, watch it fail, then implement.
- **`subagent-driven-development`** — for large work, split into independent tasks with two-stage review.
- **`dispatching-parallel-agents`** — when 2+ independent tasks can run in parallel.
- **`engineering-pipeline`** — MentisDB release engineering pipeline (parallel, verified, documented).

---

*Maintained by `gubatron` on the FrostWire mentisdb chain. Replaces the legacy `AGENTS.md` at the repo root. Update this file in a `[all]` commit; reference it from any new `DESIGN_*.md` so contributors find it.*
