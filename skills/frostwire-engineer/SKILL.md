---
name: frostwire-engineer
description: Practical FrostWire Desktop, Android, common, and JLibTorrent engineering rules. Preserves gubatron and aldenml house style, UI responsiveness, cross-platform safety, defensive protocol invariants, and evidence-based delivery.
triggers:
  - frostwire
  - Java
  - Kotlin
  - jlibtorrent
  - JNI
  - IceBridge
  - i18n
  - code style
---

# FrostWire Engineer

Use when implementing or refactoring FrostWire. Read the affected code and callers first; use `frostwire-code-reviewer` for review and `frostwire-performance-reviewer` for measured optimization. These are rules, not claims that current production code satisfies them.

## 1. Shared-Brain Workflow

- Use MentisDB chain `frostwire` and existing identity `gubatron`. Search related findings, current constraints, API decisions, and ownership before editing; verify historical claims against current source.
- One owner per file in a shared tree. Publish API contracts before changing them: signatures, units, ownership, threading, wire versions, failure and cancellation semantics. Coordinate callers instead of guessing another agent's API.
- Search before appending; publish milestone `Summary` checkpoints with evidence, changed files, blockers, remaining work, and references. Checkpoint before compaction or handoff; record non-obvious corrections rather than duplicate old lessons.
- Only the coordinator runs Gradle in a shared tree, including compile, tests, formatting, and dependency tasks. Workers report requested gates; concurrent Gradle runs can corrupt shared results.
- Preserve unrelated changes. No commits, pushes, tags, rebases, or history rewriting without explicit permission. When authorized, use one coherent change per commit and `[android]`, `[desktop]`, `[common]`, or `[all]` imperative subjects; inspect staged content for secrets.
- Security work is defensive: local regression fixtures and bounded behavior checks only. Do not create autonomous offensive workflows, exploit reproductions, or live attacks.

## 2. House Style

- KISS, DRY, minimum scope, composition over inheritance. Prefer the smallest correct change; delete dead code, avoid speculative abstractions, and search `com.frostwire.util.*` before adding helpers.
- Prefer private/final state, constructor injection, immutable values, and defensive copies of mutable arrays/collections at public boundaries. Document ownership instead of sharing buffers implicitly.
- Follow neighboring FrostWire GPL/Apache license headers and authorship; no wildcard/unused imports. Use existing formatting. Keep formatting noise separate from behavioral edits.
- Use `com.frostwire.util.Logger` with throwables as arguments, not `printStackTrace` or library `System.out`. Intentional headless CLI help/output is an exception, not permission to log secrets.
- Name constants for real shared contracts and meaningful bounds. Keep methods cohesive; concise comments explain non-obvious invariants, not assignments. Document public API threading, units, errors, and lifecycle. Do not widen access solely for tests.
- Use try-with-resources for owned streams, cursors, JDBC objects, and HTTP responses. Remove unique temporary files on success, failure, and cancellation.
- All UI strings use desktop `I18n.tr()` or Android resources. Propagate new Android base keys to every locale using the project placeholder policy; preserve placeholders/plurals. Use themed components and check light/dark themes. Update affected user-facing changelogs.

## 3. Platform And Native Boundaries

- Read current Gradle source/target, minSdk, desugaring, and dependency declarations. Java 17 includes sealed types; source-language support does not prove Android runtime/API availability. Check the actual toolchain and minimum-device API separately.
- `common/` compiles for both platforms: no Swing/AWT, desktop HTTP server/client APIs, JDBC implementation, desktop paths, or JVM subprocess launchers. Inject storage interfaces and `File`/streams. `java.nio.file.Files` exists from Android API 26, but this repository avoids `java.nio.file` in shared code by policy, not because API 26 lacks it.
- Verify actual BouncyCastle and other dependencies on both targets; do not label them desktop-only from stale memory. Check API/version compatibility, license, R8/reflection/JNI rules, and artifact size before adding dependencies.
- Verify minSdk before adding SDK guards (currently documented as 26). Android `EditTextPreference` values/defaults are strings; keyed `View.setTag` requires an application resource id. Follow existing DataStore/configuration contracts without synchronous UI read-back.
- Treat jlibtorrent/SWIG calls as potentially blocking and native handles as owned resources. Guard load/initialization failures with a safe disabled state, log actionable errors, and release resources deterministically. Java catch blocks cannot recover from a native process crash or make use-after-free safe.
- Reuse libtorrent persistence when its real API supports the state; do not invent a parallel store. BEP 5 provides multi-writer rendezvous; BEP 44 mutable items are single-writer by publisher/salt, immutable items are content-addressed. Snapshot native-mutated peer lists before iteration.
- Check timeout units in the installed dependency's source or bytecode. For example, reviewed jlibtorrent 2.0.12.9 mutable-item waits used seconds, not milliseconds. Use explicit conversion and one monotonic absolute deadline covering queueing, discovery, sends, reads, retries, and completion.

## 4. Threading And Lifecycle

- Never run JNI, disk/DB, network, large parsing/ranking, or unbounded work on Swing EDT or Android main. `GUIMediator.safeInvokeLater()` posts TO the EDT; it does not offload work. Snapshot UI state, compute on a worker, then post a small generation-checked update.
- Desktop: use `DesktopParallelExecutor` for independent reorder-safe work and handle rejection; use `BackgroundQueuedExecutorService` only for short ordered operations. Do not wait on an executor that needs the waiting thread. StrictEdtMode reports are defects, not an acceptable latency budget.
- Android: use existing `SystemUtils` handlers for bounded work and verify whether the selected helper queues or creates a thread. Reserve high priority for short user actions; coalesce refreshes and cancel them on navigation. Use an explicit ExoPlayer looper and lifecycle-appropriate WorkManager/foreground-service ownership, not a new worker every timer tick.
- Keep transport/event-loop/poller callbacks light. Hand off admitted work to bounded executors; never perform DNS, SQL, JNI, signing bursts, or synchronous sends on the sole response poller.
- Keep cross-component locks away from I/O/JNI/callbacks; isolate required DB transactions on workers. Publish state atomically, invalidate stale generations before asynchronous cleanup, and recheck cancellation before starting a download or applying a sorted snapshot.
- Own every listener, timer, socket, database, child process, and session alias. Close idempotently, including partial-start failures; remove mappings only if still owned. Repeated start/stop and identity replacement must not grow retained resources or reuse old identity-bound callbacks.
- Separate incidental service recreation from explicit stop, opt-out, and network restrictions. Explicit stop must stop owned public participation and prevent supervisor respawn. Gate sockets/advertising before startup; do not unconditionally keep mesh work alive after service destruction.

## 5. Defensive Protocol Contracts

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

## 6. Persistence, Privacy, And Deployment

- Apply one public-share policy to search, browse, announcements, metadata, cache hits, and queued indexing. Private, removed, inactive, or metadata-only entries must not become public by an alternate path; withdraw promptly without restart and prevent stale jobs from resurrecting entries.
- Keep primary rows, file rows, and FTS postings transactionally consistent across replace/delete/rowid reuse. Install each Android trigger separately, repair persisted stale postings through migration, and test real FTS state. Limit distinct torrents before joined file rows hide results; quote Unicode FTS tokens and escape LIKE wildcards without ASCII-only stripping.
- Bound stored row bytes before parsing/storage, avoid huge `files_json` in summary reads, and test device CursorWindow limits. Bind karma/history rows to their owner; feed actual publisher output through the reader and preserve local reputation across registry refresh.
- Keep control private/admin-scoped unless principal isolation is explicitly implemented. Require TLS or an authenticated tunnel for non-loopback credentials. Never put tokens, private keys, or mnemonics in argv, logs, or repository files; use a restricted non-argv handoff.
- Root owns executable artifacts and configuration; the service owns only separate data/state. Never source service-writable environment files, especially during privileged upgrades; parse allowlisted configuration as data. Build unprivileged, verify service ownership before process cleanup, and leave unrelated listeners alone.
- Check effective CLI/env/default precedence and artifact identity, not only Gradle's up-to-date label. Derive service memory/task limits from heap plus direct/native overhead; increasing session or FD caps is not a resource-safety fix.

## 7. Verification And Delivery

- Add focused JUnit 5 regression tests with representative local fixtures, edge values, failures, and concurrency. Follow the existing Android test runner where required. Reset singleton state and close fixtures. Source-string tests and mocks do not prove native, FTS, scheduling, or device behavior.
- Defensive protocol tests assert rejection without side effects, bounded resource retention, accepted-work accounting, real multi-fragment retry, multi-holder/reordered completion, and cancellation. Test exact production roles/routes and listener-before-send ordering without live attacks.
- The coordinator selects gates by change width. From `desktop/`: `./gradlew compileJava`, `./gradlew test`, `./gradlew spotlessCheck`; targeted relay gate: `./gradlew test --tests 'com.frostwire.search.relay.*'`. From `android/`: `./gradlew compilePlus1DebugJavaWithJavac`, `./gradlew testPlus1DebugUnitTest`; verify task names against current build files.
- Shared changes need both targets and wider suites for shared constants/wire helpers. Native changes need affected macOS, Linux, Windows, and Android architectures. Record unavailable gates rather than claiming cross-platform success. Do not dismiss a current failure merely because its test was historically flaky.
- Review actual assertions, not test names. Record exact commands, environment, counts, failures/skips, and untested paths. A review or green loopback suite is not EC2 capacity proof: count delivered work and resource plateaus using the performance skill. Never claim all findings fixed from edits or a partial suite.

Maintained from FrostWire house style and MentisDB review #1048 (2026-09-05). Historical lessons are evidence to recheck, not contracts to preserve known defects.
