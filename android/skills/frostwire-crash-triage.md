schema_version: 1
name: frostwire-crash-triage
description: Triage and fix FrostWire Android crashes and ANRs using Firebase Crashlytics BigQuery plus MentisDB memory. Use when asked to inspect the latest FrostWire Android release health, pick the next unclosed Crashlytics issue, study prior fixes and regressions on the frostwire chain as agent gubatron, patch the Android app, add regression tests, update changelog.txt, commit or push the fix, and track issue closure so fixed issues are not selected again.

# frostwire-crash-triage

Triage and fix FrostWire Android crashes and ANRs using Firebase Crashlytics BigQuery plus MentisDB memory. Use when asked to inspect the latest FrostWire Android release health, pick the next unclosed Crashlytics issue, study prior fixes and regressions on the frostwire chain as agent gubatron, patch the Android app, add regression tests, update changelog.txt, commit or push the fix, and track issue closure so fixed issues are not selected again.

# FrostWire Crash Triage


## Preconditions

- Work in the FrostWire Android repository.
- Use BigQuery project `frostwire-android-233c6`.
- Use MentisDB chain `frostwire` as agent `gubatron`.
- Treat Crashlytics BigQuery as the machine-readable source of truth for new events.
- Treat Firebase Crashlytics console issue state as the human issue tracker when console access is available.
- Prioritize the latest app build first unless the user explicitly asks for another version.

## Memory First

Before reading much code, search MentisDB for:

- the `issue_id`
- exception class names
- blamed file and symbol
- suspected regression commits
- prior close state for the issue

Load recent context. Reuse existing lessons and decisions. Write back any corrected assumption, non-obvious root cause, or reusable Android framework trap.

## Identify the Latest Build

Run:

```sql
SELECT
  application.build_version AS build_version,
  application.display_version AS display_version,
  COUNT(*) AS events,
  COUNTIF(is_fatal) AS fatal_events,
  COUNTIF(error_type = "ANR") AS anr_events,
  MAX(event_timestamp) AS last_seen
FROM `frostwire-android-233c6.firebase_crashlytics.com_frostwire_android_ANDROID`
WHERE event_timestamp >= TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 90 DAY)
GROUP BY 1, 2
ORDER BY last_seen DESC, fatal_events DESC
LIMIT 20
```

Ignore stale noise from old builds unless the user asks for historical cleanup.

## Pick the Next Unclosed Issue

If a local or external open/closed queue exists, respect it. If none exists yet:

1. scope to the latest `application.build_version`
2. group by `issue_id`
3. rank by `events DESC, last_seen DESC`
4. prefer `FATAL` before `ANR` when counts are comparable
5. skip issues already marked fixed in MentisDB for the current branch or newer unreleased code
6. skip issues already known closed in the Crashlytics console for the relevant release line

Use:

```sql
SELECT
  issue_id,
  error_type,
  issue_title,
  issue_subtitle,
  blame_frame.file AS blame_file,
  blame_frame.symbol AS blame_symbol,
  COUNT(*) AS events,
  COUNT(DISTINCT installation_uuid) AS installations,
  MIN(event_timestamp) AS first_seen,
  MAX(event_timestamp) AS last_seen
FROM `frostwire-android-233c6.firebase_crashlytics.com_frostwire_android_ANDROID`
WHERE application.build_version = "9090773"
GROUP BY 1, 2, 3, 4, 5, 6
ORDER BY events DESC, last_seen DESC
LIMIT 25
```

Replace the build number with the latest result from the previous query.

## Pull Issue Detail

For the chosen `issue_id`, inspect recent examples:

```sql
SELECT
  event_timestamp,
  application.build_version AS build_version,
  application.display_version AS display_version,
  device.manufacturer AS manufacturer,
  device.model AS model,
  device.architecture AS architecture,
  operating_system.display_version AS os_display_version,
  process_state,
  issue_title,
  issue_subtitle,
  blame_frame,
  exceptions,
  errors,
  threads
FROM `frostwire-android-233c6.firebase_crashlytics.com_frostwire_android_ANDROID`
WHERE application.build_version = "9090773"
  AND issue_id = "REPLACE_ISSUE_ID"
ORDER BY event_timestamp DESC
LIMIT 5
```

Focus on the repeated frames and the triggering thread, not just the headline exception.

## Research Before Editing

- Search MentisDB for the issue fingerprint and adjacent symptoms.
- Check `git log` for previous attempts touching the blamed files or same exception family.
- Read the current code path from the top crash frame outward.
- If it is a regression, identify what changed in behavior, not only what changed in lines.

## Fix Standard

- Prefer the narrowest fix that removes the failure mode without inventing a new control path.
- Reproduce locally when feasible.
- Add or adjust a regression test whenever the failure can be expressed in unit, integration, or instrumentation form.
- If no meaningful automated test is possible, state the limiting factor in the commit summary or final report.
- Update the current `UNRELEASED` section of `changelog.txt`.
- Commit granularly.
- Push only when the user asked for it or the workflow explicitly requires it.

## Close State

After a fix is verified and committed:

1. close the issue in the Firebase Crashlytics console when direct console access is available
2. record the closure in MentisDB with the `issue_id`, affected build, commit SHA, verification command, and whether console closure is confirmed or pending
3. if console closure cannot be performed in the current environment, treat MentisDB as the local queue state so the issue is not selected again until console closure is confirmed
4. if Crashlytics later reopens a closed issue as a regression, treat that as a new work item and update the prior closure memory rather than creating a duplicate trail

Do not rely on BigQuery alone for close state; BigQuery is event data, not the issue workflow.

Use MentisDB as the fallback ledger with:

- `entity_type: CrashlyticsIssue`
- tags:
  - `issue:<issue_id>`
  - `build:<build_version>`
  - `status:open|fixed|closed-local|closed-console|regressed`
  - `error_type:FATAL|ANR|NON_FATAL`

Search the latest thought for `issue:<issue_id>` before selecting work. Treat the newest matching thought as the local source of truth when console closure is unavailable.

## Memory Writes

Write to MentisDB during the work:

- `Summary` with `role: Checkpoint` before a substantial implementation wave
- `LessonLearned` for Android, Media3, libtorrent, storage-model, or Crashlytics workflow traps
- `Decision` when choosing a ranking rule, queue rule, or architectural fix
- `TaskComplete` when the issue is fixed durably, including close state

Always record why a prior assumption failed if the issue was a regression or a failed earlier fix.