schema_version: 1
name: frostwire-bug-fix-workflow
description: FrostWire bug-fix workflow. Use when fixing bugs in the FrostWire monorepo to ensure regression tests, granular commits with scope prefixes, and changelog updates are applied consistently.

# FrostWire Bug-Fix Workflow

## Scope

Applies to all bug fixes in the FrostWire monorepo (android/, desktop/, common/, jlibtorrent/).

## 0. Root Cause Fix Philosophy (gubatron / aldenml style)

- **Bugs are fixed by finding their root cause from first principles, not silenced or hidden.** A try/catch that swallows an exception without fixing the underlying reason is a last resort, not a solution. If you don't understand why something is failing, keep investigating until you do.
- **DRY — Do Not Repeat Yourself.** When you spot the same fragile pattern in multiple places, refactor to a single reusable helper or enforce the invariant structurally (e.g., a source-scanning regression test). Re-used code behaves like a perfect equation — it starts being written by itself.
- **Composition over inheritance.** If you're extending a non-abstract class to work around a bug, you should probably be composing it instead. Inheritance hides behavior you don't control; composition makes the fix explicit and testable.
- **Minimum scope principle.** Keep variables as close to their local scope as possible. Classes should expose as little as possible: local variable first, private member if it must outlive the method, protected only for inheritance, public only when you are certain no consumer can break internal state.

## 1. Regression Tests (Whenever Possible)

- Before committing a fix, ask whether the failure mode can be expressed as a unit, integration, instrumentation, or structural regression test.
- If the bug is an API misuse (e.g., calling a method that does not exist on older Android versions), prefer a **structural/architectural test** that forbids the problematic pattern via reflection or source scanning.
- If the bug is a lifecycle race (e.g., detached fragment), add a lifecycle-guard test that asserts the guard exists.
- If no meaningful automated test is possible, document the limiting factor in the commit summary or a MentisDB `LessonLearned`.
- Run existing tests after the fix: `./gradlew test` (desktop), `./gradlew compilePlus1DebugJavaWithJavac` (Android).

## 2. Granular Commits

- One logical change per commit. Do not bundle unrelated fixes.
- Commit message prefix must match the project subfolder:
  - `android/`    → `[android] `
  - `desktop/`    → `[desktop] `
  - `common/`     → `[common] `
  - `jlibtorrent/`→ `[jlibtorrent] `
  - Cross-cutting → `[all] `
- Use imperative mood: `Fix NPE` not `Fixed NPE`.
- Reference issue numbers when available: `[android] fix SoftwareUpdaterDialog BundleCompat crash on API < 33 (#1291)`

## 3. Changelog Update

- Open `changelog.txt` (or `desktop/changelog.txt`, etc.).
- Add the fix to the **latest UNRELEASED** section at the top.
- Follow the existing format:
  - `  - fix:<concise description>`
  - `  - new:<concise description>`
  - `  - improvement:<concise description>`
  - `  - maintenance:<concise description>`
- **Reorder by importance**: place the most user-impacting / crash-preventing fixes at the top of the release section, less critical ones below.
- If a prior changelog entry for the same bug exists in an older released section but the fix was incomplete, add a new entry in UNRELEASED that supersedes it; do not edit already-released history.

## 4. Build Verification

- Android: `./gradlew compilePlus1DebugJavaWithJavac`
- Desktop: `./gradlew compileJava`
- Fix compiler warnings; do not suppress them unless justified.

## 5. MentisDB Lesson

- If the fix reveals a non-obvious framework trap or a pattern that could regress elsewhere, append a `LessonLearned` to the appropriate MentisDB chain.

## Quick Reference

| Step | Command / Action |
|------|------------------|
| Test | `./gradlew test` or add new test class |
| Changelog | Edit `changelog.txt` top UNRELEASED section |
| Commit | `git commit -m "[android] fix ..."` (one change per commit) |
| Build | `./gradlew compilePlus1DebugJavaWithJavac` |
| Push | Only when user asks or workflow requires it |
