# Releasing OBD2 Reader

How a new version reaches phones, and what to do when one goes wrong.

## How a release goes out

1. **Raise the version.** The pull request raises `obdVersion` in `gradle.properties` and adds a `## x.y.z` entry at the top of `CHANGELOG.md`. That entry becomes the release notes.
2. **Merge it to `main`.** The **Build APK** run checks that this is a new version (the `check` job). The version must be higher than the newest release, live or not, and so must the build number. A push that doesn't raise the version isn't released and asks for nothing.
3. **Approve it.** The run stops at **Waiting** for the maintainer's approval on the run's page (the `release` environment). After approval it checks the version again, signs the APK and publishes it as a **pre-release**.
   - Phones aren't offered a pre-release, and the "Download the latest APK" link still gives the previous version.
4. **Install test.** About 90 minutes on Android 10, 13, 15 and 16. The **Install test** run starts by itself and installs that exact pre-release on fresh emulators.
5. **Live.** If every phone's smoke checks pass, the test's `promote` job makes it the **latest release**. Every installed copy is then offered the update, and the download link gives it.
   - If any phone's smoke checks fail, the release stays a pre-release and the run shows "Not made live".

The smoke checks are: installs, opens, connects to the demo car, tabs, codes, report, updates, keeps running, and VIN privacy. Other tour checks that fail show as warnings and don't stop a release. Results and screenshots are on the [`install-test-reports`](https://github.com/CalmAussie/obd2-reader/tree/install-test-reports) branch.

## The install test failed

Open the failed **Install test** run, then the failed phone's job. `result.txt` in the reports lists each check. `smoke-failed.txt` lists what stopped the release.

- **It looks like a test hiccup.** For example, a tap that missed or a slow emulator, with nothing wrong in the app's own log, and it passed on the other phones. Press **Re-run failed jobs** on that Install test run. If it passes this time, `promote` runs again and the release goes live.
- **It's a real bug.** Fix it in a pull request, then release again:
  - **Usually:** give the fix a new version (raise `obdVersion` and add a CHANGELOG entry). The failed pre-release can stay; phones never see it.
  - **To reuse the same version number instead:** delete the failed pre-release and its tag first: on its release page, **Delete**, then delete the tag under **Tags**. Otherwise the build sees the version as already published and doesn't release it.

## A live release turns out to be bad

Android won't install a lower version over a higher one. Phones that already updated can only be fixed by a **newer** version, so a fix release is always the real remedy.

1. **Stop more phones getting it.** This takes a minute.
   - Open the previous good release on the Releases page, press **Edit**, tick **Set as the latest release**, and save.
   - Open the bad release, press **Edit**, tick **Set as a pre-release**, and save.
   - New downloads and update notices now point at the good version again.
2. **Ship the fix as a new version.** Raise `obdVersion` above the bad one, add a CHANGELOG entry that says what was fixed, and merge. Then approve the build as usual. It goes out after its install test like any other release, and phones that got the bad version are offered it.
3. **Don't delete the bad release** while phones may still have it. Its page is where people read what they installed. Mark it as a pre-release instead (step 1).

## Build numbers

Each release's build number is its Build APK run number. That's the number phones compare to decide whether an update is newer. The check job, and the release job again just before signing, refuse a build number that isn't higher than the newest release's, live or pre-release. A re-run of an old run, or a late approval after a newer release, therefore can't publish an older build.

## Signing

The signing key lives only in the `release` environment's secrets. Every release must be signed with the certificate whose SHA-256 is in the README, and the release job checks this before publishing. If the key is ever lost, phones can't update without uninstalling first (this happened once, at 2.5.0), so keep its backup safe.
