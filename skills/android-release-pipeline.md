---
name: android-release-pipeline
description: "FrostWire for Android release pipeline - dated, importance-sorted changelog, tag frostwire-android-<version>-build-<build>, GitHub release with a plain-language post and the signed APK, then the frostwire.com config.php update and pull on virginia1. Includes the artifact-verification gates that stop a stale or contaminated APK from shipping. Use when asked to tag, release, or publish a FrostWire Android build."
triggers:
  - android release
  - release android
  - tag android
  - publish apk
  - frostwire-android build
---

# Android release pipeline

First proven on 3.2.1 build 777 (Oct/06/2026). The order below includes the
corrections made during that release.

## Hard rules

- The coordinator (you) never has the signing credentials. The user runs
  `cd android && ./gradlew assembleRelease`. Never ask for or look for keystores.
- The shipped APK must be built from a clean working tree at exactly the commit
  being tagged. A signed APK built from a dirty tree ships code that is not in git.
- Never publish an APK you have not verified (see "Verify the APK").
- Never ship a changed default, security setting, or privacy behavior that the
  changelog/strings contradict. Stop and ask the user.
- Commit prefixes: `[android]` for app changes, `[www]` for the website. The commit
  body's last line is the model id. Push frostwire to both `origin` and `gubatron`.
- Leave personal untracked files alone: `.opencode/`, `desktop/2,000`,
  `desktop/GROK_RESUME_SESSION`, `telluride_macos.arm64.zip`.

## 0. Preconditions

1. `git status --short` shows nothing tracked as modified. If a tracked file is
   modified and is not yours, find out whether it is intended. Save it as a patch
   in the temp dir before reverting; never silently commit or discard it.
2. Version surfaces: `android/AndroidManifest.xml` (`versionName`, `versionCode`),
   the top `android/changelog.txt` header, and `$FROSTWIRE_VERSIONS['apk']` /
   `$ANDROID_BUILD` on the site must all agree. The build number is the manifest
   `versionCode` (777); the version is `versionName` (3.2.1).
3. Android gate passes (coordinator only runs Gradle):
   ```bash
   cd android
   ./gradlew spotlessApply compilePlus1DebugJavaWithJavac testPlus1DebugUnitTest \
     spotlessCheck minifyPlus1ReleaseWithR8 verifyReleaseNettyReflection --offline
   ```
   Check `gh run list --repo frostwire/frostwire --commit <sha>` is green (android
   unit tests, Build, desktop tests) for the code commit.
4. New Android strings need base + 37 locale translations
   (`AndroidStringResourceParityTest`).

## 1. Changelog

File: `android/changelog.txt`. First line format:
`FrostWire <versionName> build <versionCode> MMM/DD/YYYY` with the month in
capitals, e.g. `FrostWire 3.2.1 build 777 OCT/06/2026`. Replace `UNRELEASED`.

- Sort entries by importance, most important first: headline user-facing `new:`
  features, then fixes that block core functionality (e.g. a startup failure), then
  security/privacy/behavior changes, then performance, then minor UI fixes, and
  build-internal items last.
- Move lines with a script by original line number so the text stays byte-exact.
  Assert that the set of lines is unchanged apart from deliberate removals.
- Remove true duplicates (and say so to the user). Do not rewrite entries.
- Use plain apostrophes. Android XML needs `\'`, the changelog does not.
- Make sure wording matches the actual behavior (e.g. "opt-in" vs "opt-out").

Commit: `[android] Release FrostWire <ver> build <build> changelog`, push to both
remotes.

## 2. Build, verify, then tag

Recommended order (the 3.2.1 release tagged first and had to move the tag twice):

1. Changelog commit pushed to both remotes.
2. Tell the user the exact commit and ask them to run
   `cd android && ./gradlew assembleRelease` from the clean tree.
   Output: `android/build/frostwire-android-release-<ver>-b<build>-plus.apk`
   (Plus1 variant is the shipped one).
3. Verify the APK (below). Only then tag.
4. Tag name: `frostwire-android-<versionName>-build-<versionCode>`
   (e.g. `frostwire-android-3.2.1-build-777`).
   ```bash
   git tag -a frostwire-android-3.2.1-build-777 -m 'FrostWire for Android 3.2.1 build 777'
   git push origin frostwire-android-3.2.1-build-777
   git push gubatron frostwire-android-3.2.1-build-777
   ```
   If the tag must move, only do it with the user's explicit approval, before
   publishing the release, using `git tag -fa` and `git push -f` to both remotes.
   Confirm `git rev-parse HEAD <tag>^{commit} origin/master gubatron/master` all
   match.

### Verify the APK

Run from the temp dir (`/var/folders/.../T/opencode/apkchk`):

- mtime is newer than the tagged commit (`git log -1 --format=%ci`) and
  `git status` was clean when it was built. An APK older than the last code commit
  is stale. This was caught on 3.2.1: an earlier APK lacked a method added by the
  last review change.
- Contains the latest code: unzip `classes*.dex`, run the SDK
  `build-tools/*/dexdump -l xml` and look for a method/class added in the last
  commits (e.g. `RelayStartupTracker.accept`). A missing method means stale.
- Release-only reflection targets survived R8:
  `acquireFenceFallback` is present (also enforced by `verifyReleaseNettyReflection`).
- Defaults that changed are really compiled in: disassemble with `dexdump -d`
  and check the constant next to the preference key.
- Resources reflect last string changes: `aapt2 dump strings <apk> | grep`.
- Signed with the release key:
  `build-tools/*/apksigner verify --print-certs <apk>`
  (expected signer `CN=Angel Leon, OU=Android, O=FrostWire`, SHA-256
  `01618dba36532ffcf9d5cee90a49107c4dce5e6842c179ea8365839960f82dc1`).
- Record `shasum -a 256 <apk>`; it must equal the GitHub asset digest afterward.

If any check fails, stop and ask the user to rebuild. Do not upload.

## 3. GitHub release

```bash
gh release create <tag> android/build/frostwire-android-release-<ver>-b<build>-plus.apk \
  --repo frostwire/frostwire --verify-tag \
  --title 'FrostWire for Android <ver> build <build>' \
  --notes-file <body.md> --latest
```

Body (write it to the temp dir, show or summarize it before publishing):

- `# FrostWire <ver> build <build>`
- A plain-language blog post for normal users: what is new in a few short
  paragraphs (bold lead-ins), then "Fixes you may notice". No internal class
  names, no jargon; explain each feature as what it does for the user. Mention the
  jlibtorrent version (from `android/build.gradle` `jlibtorrent_version`).
- State defaults accurately (what is on/off by default, how to change it).
- `## Full changelog` followed by the full, sorted entries for this build copied
  verbatim from `android/changelog.txt` (everything from the header line down to
  the previous release).
- Previous releases are a good style reference:
  `gh release view <previous-tag> --repo frostwire/frostwire --json body`.

After creating:
`gh release view <tag> --repo frostwire/frostwire --json url,assets,isDraft --jq ...`
- not a draft, one APK asset, asset size and `sha256` digest match the local file.
- `curl -sIL -o /dev/null -w '%{http_code}' <download url>` returns 200.

## 4. Website

Repo: `/Users/gubatron/workspace/frostwire-cloud` (remote
`git@github.com:frostwire/frostwire-cloud.git`, branch `master`). The file is
`www.frostwire.com/includes/config.php` (in git it is
`com/frostwire/www/includes/config.php`).

1. Edit only two values:
   - `$FROSTWIRE_VERSIONS['apk']` -> `'<versionName>'`
   - `$ANDROID_BUILD` -> `'<versionCode>'`
   The download link is generated as
   `https://github.com/frostwire/frostwire/releases/download/frostwire-android-<ver>-build-<build>/frostwire-android-release-<ver>-b<build>-plus.apk`.
2. `php -l includes/config.php`, `git diff` shows exactly two changed lines.
3. Commit as `[www] android <ver> build <build>`, `git push origin master`.
   The repo has an untracked `lib/Smarty-3.1.48/`; do not add it.
4. Deploy: the host alias `virginia1` in `~/.ssh/config` has no `User`, so a plain
   `ssh virginia1` tries the local user and is denied. The login user is
   `ubuntu`:
   ```bash
   ssh -o BatchMode=yes ubuntu@virginia1 \
     'cd ~/www.frostwire.com && git pull origin master && git log -1 --format="%h %s" \
      && grep -n "ANDROID_BUILD = \|apk. =>" includes/config.php'
   ```
   (The server checkout is the repo root: `~/www.frostwire.com/includes/config.php`.)
   Expect a fast-forward with only `config.php` changed. The server has untracked
   `dl/` files; leave them.
5. Verify the deployed values and the GitHub download URL return 200. The
   public pages may not expose the link in plain HTML, so confirm on the server
   and via the GitHub URL.

## 5. Wrap up

- Append a MentisDB `Summary`/`Checkpoint` (chain `frostwire`, agent `gubatron`)
  with the release URL, tag, commit, APK SHA-256, and any lessons; add an
  `append_retrospective` for anything that went wrong.
- Update this skill if a step changed.

## Lessons from 3.2.1 build 777

- The first APK was stale (built before the last code commit); the second contained
  an uncommitted local edit (a privacy-relevant default flipped to `true`).
  Both were caught only by inspecting the APK contents. Always verify content,
  not just file name and mtime.
- A default that changes behavior needs matching changes in: storage default,
  the settings XML `defaultValue`, strings in every locale, Javadoc, changelog,
  release body, and a guard test. Missing the XML `defaultValue` would have shown
  a switch OFF while the feature ran ON.
- Tagging before the APK is verified caused two tag moves. Tag after verification.
- Release-only failures (R8 removing reflectively accessed members) do not show up
  in debug builds or unit tests; keep `verifyReleaseNettyReflection` in the gate.
