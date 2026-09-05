---
name: frostwire-code-reviewer
description: Evidence-led review of FrostWire common, desktop, and Android code for correctness, security, lifecycle, cross-platform compatibility, house style, and defensive regression coverage.
triggers:
  - code review
  - PR review
  - security review
  - audit
  - regression test
  - before commit
---

# FrostWire Code Reviewer

Use `frostwire-engineer` for canonical engineering rules and `frostwire-performance-reviewer` for profiling. Review current source and real call paths, not a historical bug list. A review request is read-only unless fixes are authorized.

## 1. Scope And Coordination

1. Establish revision/diff, requested scope, modules, production entry points, trust boundaries, and supported roles. Include callers, serializers, persistence, teardown, and tests, not only changed methods.
2. Search MentisDB `frostwire` using existing identity `gubatron` for related findings, constraints, API contracts, and file ownership before edits or new findings. Revalidate old claims; distinguish resolved, latent, conditional, and current defects.
3. Assign one owner per file. Publish API signatures, units, ownership, failure/cancellation, and wire contracts before changing them; report cross-owner dependencies instead of making overlapping edits.
4. Only the coordinator runs Gradle in the shared tree. Workers provide requested gates and evidence. Search before milestone `Summary` checkpoints; include refs, files, blockers, verification gaps, and handoff state before context loss.
5. Preserve unrelated work. No commits, pushes, or history changes without explicit permission. Security verification is defensive local regression testing only, not exploit reproduction, autonomous offensive workflows, or live attacks.

## 2. Severity And Evidence

| Severity | Meaning | Disposition |
|---|---|---|
| BLOCK | Demonstrated contract violation with release-blocking security, data-loss, privacy, or core correctness impact | Resolve before approval |
| HIGH | Credible production failure, availability risk, or serious regression | Fix before release or explicitly resolve risk |
| MEDIUM | Bounded correctness or maintainability issue | Schedule a concrete fix |
| LOW | Minor style or clarity issue | Nonblocking |
| INFO | Observation, accepted cost, or qualified follow-up | No defect claim |

- Give each finding `path:line`, trigger/preconditions, violated invariant, user impact, minimal remedy, and defensive validation. Severity follows impact and reachability, not dramatic wording.
- Label evidence: source-confirmed, locally observed, measured, estimated, or conditional. Operation counts are not measured latency. A source-confirmed defect need not be exercised against a live service.
- Inspect tests' assertions and production wiring. A test name, mocked codec, regex/source-string assertion, or green suite cannot establish behavior it never executes. Do not report old issues as new or label known failures flaky without fresh evidence.

## 3. Platform, UI, And Native Review

- Inspect current Gradle source/target, minSdk, desugaring, actual dependencies, and R8 rules. Java 17 supports sealed classes; language support and Android API/runtime availability are separate checks. BouncyCastle availability must come from actual dependency declarations/resolution, not an old desktop-only claim.
- Shared `common/` cannot depend on Swing/AWT, desktop HTTP APIs, JDBC implementations, desktop paths, or JVM subprocesses. `java.nio.file.Files` is available on API 26; avoiding `java.nio.file` in shared code is repository policy. Verify both target builds rather than conflating policy with missing APIs.
- Trace JNI, SQL/disk, network, parsing, ranking, and large UI allocations to their actual thread. `safeInvokeLater` schedules ON Swing EDT, never off it. Android preference callbacks run on main; use workers and generation-checked UI updates. Check executor rejection, serial-worker stalls, ExoPlayer looper, and StrictMode/StrictEdtMode evidence.
- Validate native initialization failure handling, SWIG ownership, deterministic cleanup, and restart behavior. Catching Java exceptions does not contain native crashes. Inspect actual dependency timeout units; one monotonic absolute deadline must cover queueing, serial sends, retries, and reads, not start after them.
- Check house style: minimal scope, composition, defensive copies, existing utilities, `Logger`, resource cleanup, appropriate headers, imports, formatting, and concise invariant/API documentation. Require desktop `I18n.tr`, Android locale-key parity, placeholder/plural integrity, themes, and affected changelogs.

## 4. Trust And Admission Review

- Distinguish identity-record validity, endpoint key possession, handshake authentication, packet integrity, and application signatures. None substitutes for the others. Require fresh domain/peer/role/version-bound handshake transcripts; prefer established secure transports.
- Before any mutation or delivery, authenticate packet type, sequence, ACK, payload, and migration-sensitive fields. CID/address lookup is not authentication. Reject identity replacement, impossible ACKs, and unsolicited or uncorrelated introductions; apply destination policy before outbound work.
- Coordinate incompatible wire/signature changes with explicit version rejection and tests for both sides. No insecure downgrade or automatic acceptance of historical encodings to preserve connectivity.
- Freshness checks need overflow-safe arithmetic AND bounded requester/nonce replay state. Manual negation also overflows `Long.MIN_VALUE`; `a - b` can already overflow. Validate domains and use checked arithmetic or bounded comparisons, not an absolute-value trick.
- Cheap ingress/global budgets precede expensive work; per-requester quotas are charged only after authentication. Count limiter-key cardinality and expiry, not only QPS. Known peers, reconnects, observed registrations, and outbound introductions must not bypass admission.
- Enforce count, bytes, cardinality, and lifetime limits before allocation, parse, crypto, native work, or scheduling. Read streams under a cap; reading the whole body and checking afterward is not a bound. Include decoded expansion, aggregate chunks, accepted sockets, caches, pending retries, and cleanup.
- Only admitted requests may access indexes or forward. Preserve origin signatures separately from hop attribution; authenticate the maximum routing budget and reject loops/replays. Align sender/receiver TTL semantics; old tests do not justify forwarding TTL-zero requests that the next hop discards.
- Inspect SQL parameters, Unicode-safe FTS token quoting, LIKE escaping, and canonical path containment by path component, not naive string prefix. Treat DHT, imports, peer-signed content, and cached records as untrusted inputs.

## 5. Delivery And Stream Review

- Trace accepted, queued, delivered, ACKed, processed, rejected, expired, and retryable states end to end. Reserve capacity/ownership before acknowledging at the claimed layer; do not equate HTTP 200 or transport ACK with application delivery.
- Re-ACK previously accepted duplicates without repeating delivery/fanout. Distinguish rejection, incomplete assembly, retained completion, and accepted completion. Never evict accepted work silently as an optimization; retry and cancellation must have explicit outcomes.
- Identify the authoritative consumer/queue for each message. Shared/identity mirrors must not block an active consumer when an unused mirror fills. Bound total targets and bytes, clean empty subscriptions, and specify lease/ACK semantics for reliable polling.
- Check simultaneous-open reuse, all CID/address aliases, conditional map removal, and retransmission through the current authenticated endpoint. Bound send windows, retry bytes/lifetime, and pending pre-handshake work; keep pollers/event loops free of blocking providers.
- Track each request/holder stream independently with bounded indices, rows, bytes, duplicates/conflicts, and contiguous completion. One final chunk cannot close other holders or missing chunks. Cancel owned sends/waits/listeners promptly and prevent late download starts or stale UI publication.
- Verify metadata bytes against the originally requested v1/v2/hybrid infohash independently of holder signature/digest. Validate error shape/digest and request binding. Preserve signed fields such as seeder endpoints across chunk conversion and URL search collection.

## 6. Privacy, Persistence, And Lifecycle

- Trace public-share visibility across ordinary search, catalog browse, announcement, metadata provider, cache hit, and queued index work. Test private, inactive, metadata-only, removed, and cached-then-withdrawn torrents without restarting.
- Verify primary rows, file rows, and FTS postings stay consistent through replacement, deletion, rowid reuse, and migration. Execute Android trigger definitions individually; inspect FTS rows, not only ordinary tables. LIMIT must apply to distinct torrent results, not a pre-dedup joined window.
- Bound stored row bytes before parsing/writing and avoid huge JSON in summary reads. Verify real Android CursorWindow/FTS behavior. Bind karma rows to owner and test actual publisher output through the reader; upsert must not erase local reputation.
- Gate network participation before startup. Distinguish service recreation from explicit stop, opt-out, and network restrictions; explicit stop must close owned listeners/sockets/jobs and prevent respawn. Check partial-start rollback, identity replacement, listener removal, database ownership, and child-process teardown.
- Require generation/revision checks for asynchronous sort, refresh, and query results. Cancellation must not queue behind the very operation it needs to stop. Repeated lifecycle transitions should reach a resource plateau, not retain another listener/thread/store each cycle.

## 7. Deployment Boundaries

- Keep control APIs private and authenticated; multiple administrator tokens do not provide tenant isolation. Require TLS or an authenticated tunnel for non-loopback credential transport; never put secrets in argv/logs. Inspect authorization as well as authentication.
- Require root-owned program/configuration and separate service-writable state. Never source a service-writable environment file during privileged install/upgrade. Parse allowlisted data, build without root, and verify process ownership before cleanup; foreign port listeners are not safe kill targets.
- Check effective CLI/env/default precedence, generated unit configuration, release artifact identity, and local-only opt-in debugging. Measure memory/task ceilings including heap, direct buffers, and native allocations. More EC2 instances, sessions, or FDs do not repair admission defects.

## 8. Defensive Verification Matrix

| Area | Required behavior to check |
|---|---|
| Auth/admission | Invalid, stale, replayed, wrong-peer/version, and over-budget inputs rejected without routing/state side effects; bounded key churn |
| Delivery | Accepted-work accounting under slow consumers, real multi-fragment completion retry, duplicate ACK recovery, identity-only polling beyond queue lifetime capacity |
| Streams | Multiple holders, final-before-prefix, duplicates/conflicts, gaps, row/byte caps, endpoint preservation, cancellation during sends/waits |
| Metadata/privacy | Real matching/nonmatching torrent fixtures; policy enforced across every path and cache after withdrawal |
| Storage | Replace/delete/rowid reuse, persisted repair, Unicode, distinct limits, owner isolation, actual publisher-reader round trip |
| Lifecycle/UI | Partial start, repeated stop/start, explicit opt-out, identity change, navigation/cancel/sort races, constant resource counts |
| Integration | Real production roles and request/reply routing, listener-before-send, three-node/multi-holder topology; local fixtures only |

- Prefer focused JUnit 5 behavior tests; use the established Android runner where needed. Reset singleton state and close resources. Regression tests should distinguish broken from corrected behavior without recreating a live attack.
- Coordinator gates from `desktop/`: `./gradlew compileJava`, `./gradlew test`, `./gradlew spotlessCheck`; relay subset: `./gradlew test --tests 'com.frostwire.search.relay.*'`. From `android/`: `./gradlew compilePlus1DebugJavaWithJavac`, `./gradlew testPlus1DebugUnitTest`. Verify current task names and expand coverage for shared constants/protocols/native changes.
- Report commands, environment, failures/skips, and gaps. Local SQL is not Android runtime proof; loopback/simulation is not WAN/EC2 capacity proof. Performance readiness needs delivered-work counts, tail latency, and resource plateaus, not attempted sends or tests paced below a limiter.

## 9. Review Output

Findings first, ordered by severity. For each: `severity | path:line | trigger | evidence | impact | minimal fix | defensive validation`. Then give assumptions/non-findings, exact verification and missing gates, and `APPROVE`, `REQUEST CHANGES`, or `BLOCK` with scope.

If no findings, say so and list residual risks. Maintain per-finding open/fixed/verified/blocked status; never claim all fixed from a patch, green subset, or completed review. MentisDB #1048 (2026-09-05) is a qualified source-review baseline, not proof of current defects, exploitability, or EC2 capacity.
