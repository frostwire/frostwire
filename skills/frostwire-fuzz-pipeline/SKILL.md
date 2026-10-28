---
schema_version: 1
name: frostwire-fuzz-pipeline
description: The bug pipeline that finds FrostWire bugs with coverage-guided fuzzing (Jazzer) instead of user reports, then runs every finding through the Icebase pipeline (GitHub ticket, one-at-a-time root-cause fix with a regression test, changelog, CI gate, close or ask). Covers choosing targets (common/ parsers and decoders of untrusted input first, then desktop non-UI logic, then what of android runs on the JVM), writing @FuzzTest harnesses, replaying the committed corpus (fuzzTest) versus fuzzing one target for a fixed time (fuzz), deduplicating and minimizing findings, and turning a crash input into a deterministic regression test. Use when asked to "run the fuzz pipeline", "fuzz common/", "find bugs with Jazzer", write a fuzz harness, or triage a fuzzing finding.
tags: [frostwire, fuzzing, jazzer, libfuzzer, common, desktop, pipeline, github-issues, security]
triggers: [fuzz pipeline, run the fuzz pipeline, fuzz common, fuzz testing, find bugs with jazzer, jazzer, fuzz harness, fuzzing finding]
---

# frostwire-fuzz-pipeline

How FrostWire finds bugs by fuzzing and turns them into fixed, tracked tech debt. It is the Icebase pipeline with a different bug source:
- **Icebase pipeline:** a user's machine reports a crash or freeze → ticket → fix.
- **Fuzz pipeline:** Jazzer feeds generated input to a parser until it throws, hangs or blows the heap → ticket → fix.

Everything after "we have a finding" is the Icebase pipeline, `skills/frostwire-icebase-pipeline/SKILL.md`. This skill only adds the steps before it (§1–§4) and the deltas for fuzz findings (§5–§7). Read that skill first; it is not repeated here.

**Pipeline:** pick targets → write or update harnesses → replay the corpus (`fuzzTest`) → fuzz each target for a fixed time (`fuzz`) → dedupe and minimize the findings → one GitHub ticket per root cause (label `fuzzing`) → then the Icebase pipeline from its §3: one ticket at a time, root-cause fix everywhere, regression test that fails without the fix, changelog, CI green, close or ask.

Copies, kept identical: the frostwire repo's `skills/frostwire-fuzz-pipeline/` (public), frostwire-cloud (private) `icebase.frostwire.com/skills/frostwire-fuzz-pipeline/` and the MentisDB skill registry. Keep server hosts, logins, tokens and private paths out of it.

Load `frostwire-engineer` and `frostwire-code-reviewer` before editing code, as the Icebase pipeline says.

## 0. Ground rules

Everything in the Icebase pipeline's §0 applies. For fuzzing that means:
- **Never weaken the detector.** A harness catches only the exceptions the target's API documents (its `throws` clause or javadoc). Never add `catch (Exception e)`, `catch (Throwable t)`, a broader `instanceof`, a smaller input cap or a `jazzer.ignore` entry to make a finding go away. If a target's contract really allows an exception, document it in the target's javadoc first, in the fix commit, and then catch exactly that type.
- **Root cause, not the symptom.** The fix validates the input where the parser reads it: a bounds check, a length cap, a rejection with the documented exception. Wrapping the call site in `try/catch` is not a fix. Neither is catching `Throwable` inside the parser (several `common/` decoders already do this, which also swallows `OutOfMemoryError` and `StackOverflowError`: treat those as findings to fix, not as a pattern to copy).
- **Fix it everywhere.** Copied parsers repeat the same mistake: all ID3 frame classes share `BufferTools.copyBuffer`, all MP4 boxes share `IO.read`. Search for the pattern and fix every site, one commit each, or one shared helper.
- **Fuzz input is not secret, but tickets are public.** A crash input can be posted as hex or base64. Check it holds nothing from your machine (paths, user names) before posting.
- **Harnesses never touch the network, the real file system outside a temp dir, native code (jlibtorrent), Swing or the EDT.** A native crash kills the fuzzer with no usable report, and network or UI state makes runs non-deterministic.
- **CI never runs fuzzing.** `fuzzTest` and `fuzz` are not part of `test`, `check` or `build`. A finding never turns CI red until its fix lands with a normal regression test (§6).

## 1. Choose targets

Rank by how directly untrusted bytes reach the code, then by how much custom parsing it does. Fuzz the highest-ranked targets that have no harness yet, and keep the existing ones running.

1. **`common/`, untrusted input from the network or from downloaded files.**
   - Media and metadata parsers: ID3 tags (`com.frostwire.mp3`), MP4 boxes (`com.frostwire.mp4`), WebM (`com.frostwire.webm`).
   - Search engine response parsers: every `SearchPattern.parseResults(String)` (HTML through `com.frostwire.regex`, which runs on RE2/J and so can't backtrack catastrophically, and JSON through Gson), `SoundcloudUtils.fromJson`, `TellurideParser`. Look for the `java.util.regex` helpers around them (dates, sizes): those can.
   - Relay and IceBridge wire formats from peers: `SearchPayloadCodec` decoders, `RemoteSearchResponse.fromBencodeableMap`, `RemoteIndexFetcher.parseManifest`, `IndexDigest.fromBytes`, `RelayFrame.decode`, `MeshEnvelope.unwrap`, the rUDP codec and `FragmentReassembler` (stateful: drive it with a sequence of operations from one `FuzzedDataProvider`).
   - URL, magnet and string utilities applied to network data: `UrlUtils`, `SizeParser`, `DateParser`, `HtmlManipulator`, `StringUtils`, `Hex`.
2. **`desktop/` non-UI logic.** Models, filters, validators, settings and file-format parsers (e.g. `com.frostwire.gui.library.tags`, the magnet and torrent-URL handling in `ExternalControl` and `TorrentFetcherDownload`). Never fuzz a Swing component: extract the logic into a plain class or static method first, in its own commit, and fuzz that.
3. **`android/`, only what runs on a plain JVM.**
   - `common/` is compiled into the Android app, so the harnesses above already cover the shared parsers on Android too.
   - Android-only classes with no `android.*`/`androidx.*` dependency (directly or through what they call) can be fuzzed only after moving them into `common/`. The Android unit tests use JUnit 4 and Robolectric, and Jazzer's JUnit integration needs JUnit 5, so there is no Android fuzz source set.
   - Not fuzzable on the JVM: anything touching the Android framework (Activities, Services, ContentProviders, `MediaStore`, `SharedPreferences`), the bundled Python (Telluride), and jlibtorrent's native code. Fuzzing those needs on-device or native (libFuzzer/C++) fuzzing, out of scope here.
4. **Skip:** code whose input is fully under our control (our own settings files written by our own code), thin wrappers around JDK parsers, and code that only works with native libraries loaded.

When the entry point hides the bug (a decoder that catches `Throwable` and returns `null`), fuzz the first method under the catch that does the parsing, and treat a hang or an `OutOfMemoryError` swallowed by the catch as a finding.

## 2. Write harnesses

Where things live (the `fuzzTest` source set in `desktop/build.gradle`):
- Harnesses: `common/fuzz/src/test/java/<package of the target>/<Target>FuzzTest.java`, in the target's package so package-private parsers are reachable.
- Seed and crash inputs (committed): `common/fuzz/src/test/resources/<package path>/<Target>FuzzTestInputs/<method>/`.
- Generated corpus (local, ignored by git): `common/fuzz/.cifuzz-corpus/<FQCN>/<method>/`.

Conventions:
- **One target per harness:** one class with one `@FuzzTest` method that calls one entry point. Jazzer fuzzes one fuzz test per JVM, and one target per class keeps findings, corpus and ticket aligned.
- **Signature:** `@FuzzTest void <method>(FuzzedDataProvider data)`. Use `data.consumeRemainingAsBytes()` for binary formats and `data.consumeRemainingAsString()` for text. For stateful targets, drive a sequence of calls with `consumeInt`/`consumeBytes` and finish with `consumeRemainingAs…`.
- **The header** is the house header (`Created by Angel Leon (@gubatron)`, `Copyright (c) 2011-2026, FrostWire(R)`, `Licensed under GPL v3`), the same as every other new file. Format with google-java-format through Spotless (the source set is in the Spotless `target`). In a git worktree, where Spotless fails with "Cannot find git repository", run the google-java-format jar of the version `desktop/build.gradle` pins on the new files instead, and let `spotlessCheck` in the main checkout confirm it.
- **The class javadoc says** where the input comes from in production (who sends it), what the API documents, and what therefore counts as a finding.
- **What counts as a finding:**
  - any uncaught `RuntimeException` or `Error` that the API does not document (`ArrayIndexOutOfBounds`, `NegativeArraySize`, `BufferUnderflow`, `NullPointer`, `IllegalArgument` from a JDK call, `ClassCast`, `NumberFormat`, `StackOverflowError`);
  - a hang: one input running longer than the 10 s per-input timeout (`junit.jupiter.execution.timeout.testtemplate.method.default` in the Gradle task);
  - `OutOfMemoryError` from an allocation sized by the input (Jazzer reports `FuzzerSecurityIssueLow: Out of memory`);
  - any Jazzer sanitizer report (`FuzzerSecurityIssue*`: ReDoS, path traversal, deserialization, SSRF, command injection);
  - a broken contract you assert: the result is `null` where the API never returns `null`, a size below `-1` where `-1` means "unparseable". Assert only what the javadoc or every caller relies on.
- **Not findings:** the checked or unchecked exceptions the API documents. Catch exactly those types, nothing broader.
- **Seeds:** 2–5 small, valid inputs per harness that reach deep code (a real ID3v2.3 tag, a minimal `ftyp`+`moov`+`mdat` file, one row of each HTML layout a search pattern accepts). Generate binary seeds with a short script and commit only the output files. Seeds must pass: they run in `fuzzTest`.
- **No product code changes in a harness commit.** Harness commits only add tests. Findings are fixed in their own ticket (§6).

## 3. Run

Run Gradle from `desktop/`, never from the repo root, and `set -o pipefail` when piping its output.

- **Regression mode, fast, plain JUnit:** `./gradlew fuzzTest`. It replays every committed seed and crash input of every harness once, with no fuzzing. Run it before committing harness changes and after fixing a finding. Add `--tests '<FQCN>'` for one harness.
- **Fuzzing mode, one target, time-boxed:** `./gradlew fuzz -Pfuzz.target=<FQCN> -Pfuzz.duration=300s`.
  - Sets `JAZZER_FUZZ=1`. Jazzer fuzzes exactly one harness per run, so run targets one after another (a loop in a background shell), never several in one Gradle call.
  - `-Pfuzz.keepGoing=N` keeps fuzzing until N distinct findings (by `DEDUP_TOKEN`). With it, Jazzer reports the JUnit test as passed when it found fewer than N, so the `fuzz` task itself fails whenever the run wrote a crash file and lists them. Trust that list, not the test result.
  - `-Pfuzz.ignore=<token>,<token>` skips findings already ticketed so the run can reach deeper code before they are fixed. Never commit an ignore list.
  - `-Pfuzz.javaVersion=24` runs the fuzzer on an older toolchain JDK. Use it when the log shows `Failed to instrument java/... Unsupported class file major version`: Jazzer's bundled ASM can't read the newest JDK's classes yet, and the sanitizers that hook JDK classes (ReDoS in `java.util.regex`, command injection in `ProcessBuilder`) are then off. App-code coverage still works on the newest JDK.
  - Budget: 5 minutes per target for a pass, 30–60 minutes for a new or changed parser. Log each run to a file, then read the summary lines (`Done N runs in S second(s)`, `cov:`, `== Java Exception`, `DEDUP_TOKEN`).
  - The tasks set `java.util.logging` to `SEVERE` (`common/fuzz/logging.properties`): parsers log each rejected input, which at thousands of runs per second fills gigabytes of log and slows fuzzing. Don't turn it back on for a run.
  - The 10 s per-input limit is libFuzzer's `-timeout` in fuzzing mode and JUnit's per-invocation timeout in `fuzzTest`. The `fuzz` task disables JUnit's own enforcement, which would otherwise fail the whole fuzzing invocation after 10 s.
- **Where the output goes:**
  - Crash inputs are written to the harness's committed inputs directory as `crash-<sha1>` (also `timeout-`, `oom-`). Move them out of the tree (to a scratch directory) until their fix lands. A crash file left in the inputs directory makes `fuzzTest` fail, by design.
  - The generated corpus is written to `common/fuzz/.cifuzz-corpus/`, which is git-ignored. Keep it between runs on the same machine; it is what makes the next run start deep.
- **CI isolation:** CI runs `./gradlew test` (desktop tests), `./gradlew build` and `jar` (Build) and the android unit tests. `fuzzTest` and `fuzz` are custom `Test` tasks that `check` does not depend on, and the `test` task's class directories don't include the fuzz source set. If you change the Gradle wiring, recheck `.github/workflows/*` and confirm with `./gradlew test --dry-run` and `./gradlew build --dry-run` that neither lists `fuzzTest`, `fuzz` or `compileFuzzTestJava` (in a git worktree add `-x spotlessCheck`: Spotless's ratchet can't find the repository from a worktree).

## 4. Triage findings

1. **Dedupe by root cause, not by token.** Jazzer's `DEDUP_TOKEN` hashes the whole stack, so one bug reached through two getters gives two tokens. Group findings by the exception type plus the top app frame (`com.frostwire.*`, `com.limegroup.*`, `org.limewire.*`, our copies under `org.apache.commons.*`). One group, one root cause, one ticket. Distinct top frames in the same copied pattern (all ID3 frame classes trusting a length byte) are one ticket with every site listed.
2. **Minimize the input.** Start from the shortest crash file in the group and delete chunks and zero bytes while it still throws the same exception at the same top app frame. A scratch single-file Java program (outside the repo) that calls the target in a loop and runs with `java -cp desktop/build/classes/java/main` (plus any jar the target needs) does hundreds of tries per second; by hand, drop each candidate into the inputs directory and run `./gradlew fuzzTest --tests '<FQCN>'`. Stop when every remaining byte is needed, and explain each byte in the ticket ("`ID3` + v2.2 header + a `TT2` frame of size 0").
3. **Decide whether it is reachable and what users see.** Find the production callers of the target and whether they catch the exception. A finding is still a bug when a caller swallows it: it drops a whole search page, a file's tags, or a peer's response. Say what the user loses.
4. **Check master and the existing tickets**, as the Icebase pipeline's §1–§2 say: `gh issue list --repo frostwire/frostwire --state all --label fuzzing --search "<class or method>"`. Fold into an open ticket instead of opening a new one.

## 5. Ticket

Open tickets as the Icebase pipeline's §2 says, with these differences:
- **Labels:** `fuzzing`, plus `common`, `desktop` or `android` for where the code lives, plus `crash` for an exception or `bug` for a hang or allocation. There is no Icebase issue to close.
- **Title:** what a user would see, e.g. "A malformed ID3 tag in a downloaded MP3 throws ArrayIndexOutOfBoundsException while reading its title".
- **Body:**
  - the harness (`<FQCN>`), Jazzer version and JDK, and how long it ran;
  - the exception and the top app frames for each site in the group;
  - the minimized input as hex or base64, with the meaning of each byte;
  - the repro: put the input in `common/fuzz/src/test/resources/<pkg>/<Class>Inputs/<method>/` and run `./gradlew fuzzTest --tests '<FQCN>'` from `desktop/`;
  - the production callers and what the user loses;
  - the suspected cause with `file:line`, fix options, and a test plan.
- **Order the queue** as the Icebase pipeline's §3.2 does. Severity for fuzz findings: allocation and hang findings on network input first (a peer or web page can stall or crash the app), then exceptions on network input, then exceptions on local files.

## 6. Fix

Follow the Icebase pipeline's §3–§5: one ticket at a time on master, root cause everywhere, gates, changelog line, push, CI green, close or ask. Fuzz findings add two things to each fix:
- **A deterministic regression test in the normal test suite** (`common/src/test/java/...` or `desktop/src/test/java/...`, in a package already in the `test` task's `includeTestsMatching` list, or add it there). It feeds the minimized input to the target as a byte or string literal and asserts the documented outcome (the documented exception, an empty result, `-1`). Verify it fails with the fix reverted. This test is what CI runs; the fuzz corpus is not.
- **The crash input committed next to the harness** in the same push as the fix: move the minimized file into the harness's inputs directory with a descriptive name (`crash-empty-text-frame`), so `fuzzTest` replays it from now on. Run `./gradlew fuzzTest --tests '<FQCN>'` and then fuzz the target again for 5 minutes with no `-Pfuzz.ignore`, to see that the group is gone and that the fix didn't just move the crash one frame down.

The changelog line describes the user's view ("A malformed tag in a downloaded MP3 no longer breaks reading its title"), as in the Icebase pipeline.

## 7. Report back, hygiene and memory

As the Icebase pipeline's §6–§8 say, plus:
- one row per harness: target, time fuzzed this pass, final `cov:`/`ft:`, findings (new / folded / fixed);
- the targets from §1 that still have no harness, with the next recommended one;
- a MentisDB `LessonLearned` per fixed group (chain `frostwire`): the parser mistake, the fix commit and the minimized input, so the next pass recognizes the pattern.

## 8. Running unattended (only when the maintainer asks)

GitHub-hosted standard runners are free for public repositories, a job may run up to 6 hours, and a `schedule` or `workflow_dispatch` workflow can run `fuzz` per target and keep `common/fuzz/.cifuzz-corpus/` between runs with `actions/cache` (or upload crash files as artifacts). ClusterFuzzLite packages exactly that (Jazzer is supported, batch fuzzing on a schedule plus short fuzzing on pull requests, corpus kept in a storage repo). OSS-Fuzz needs Google's acceptance. Never add a workflow without the maintainer's OK, and never let fuzzing gate a merge: a red fuzz job is a ticket, not a broken build.
