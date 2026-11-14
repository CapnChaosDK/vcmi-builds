# VCMI Builds

Independent Android companion for downloading, installing, and validating VCMI Thor candidate builds.

- Android package: `dev.vcmi.builds` (preserved for updates)
- Default game repository: [`CapnChaosDK/vcmi_thor`](https://github.com/CapnChaosDK/vcmi_thor)
- Default game candidate branch: `ci/thor-candidate-validation`
- Android SDK 35, Java 17, Gradle 8.9 (installed by CI)
- Candidate verification: archive digest, signed receipt identity, package, ABI, APK checksum and packaged validation plan
- Local candidate and per-build result persistence; encrypted GitHub token, installer handoff, report copy/share

## Build

In this repository's root, with Java 17, Android SDK 35, and Gradle 8.9:

```sh
gradle :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest
```

Android CI compiles the debug app and instrumentation tests on pull requests and `main` pushes. Trusted `main` builds also require the existing signing secrets, then publish the verified APK and checksum to GitHub Releases. Increase `versionCode` for each new APK release. An existing release for a different commit cannot be overwritten by reusing its version code.

## Update on Thor

Install an updater-enabled APK (0.4.0 or newer) from this repository's [GitHub Releases](https://github.com/CapnChaosDK/vcmi-builds/releases) over your existing installation. Open **Settings → Update VCMI Builds** (on the home screen in 0.4.0), check for an update, download it and confirm installation in Android. The updater uses this repository's public APK releases without a GitHub token. Android may prompt you to allow installs from VCMI Builds.

Keep the update screen open during download. If interrupted, check again and retry. A completed, verified download remains available across restarts and installer cancellation. The updater checks the release checksum, package ID, signing certificate and version before making the APK available. The normal Thor candidate controls still use the game repository and its saved token.

For the on-device check, start with a same-key-signed updater-enabled build and a newer GitHub release. Check for the update, download it, decline install permission once, then grant it and cancel the Android installer. Reopen the updater and install the same download. Confirm the new version reports that it is up to date and that your token, saved candidates and results survived. Verify that checking for game builds still works.

Run the update metadata instrumentation with `adb shell am instrument -w -e phase update dev.vcmi.builds.test/dev.vcmi.builds.TokenStoreInstrumentation` on a compatible test installation.

## Find and resume Thor builds

Use **Check for new builds** to refresh the GitHub list. After verification, open **Downloaded builds** to install a candidate or view its results. The last-opened build appears first, and the latest download is marked in the same list.

On Thor, check the empty list and then download two candidates. Open the older candidate, return with Android Back, and verify it appears first while the newer one still says **Latest download**. Reopen the app and verify the same ordering and saved results. Check that **Cancel download** appears during a transfer and that the updater is reachable from Settings.

## Release signing — required one-time setup

Configure the **existing** signing credentials in this new repository under exactly these names. GitHub does not reveal previously saved secret values, so use your original keystore/password backup (not the old GitHub Secrets screen):

- `VCMI_BUILDS_KEYSTORE_BASE64`
- `VCMI_BUILDS_KEYSTORE_PASSWORD`

Do **not** create a new key, change package ID, or upload secrets to git. The Gradle signing alias remains `vcmi-builds`. If the previous key is not recoverable, a new signature cannot update the existing installed app in place: pause and recover the existing key first.

After secrets are set, rerun **Android CI** on `main` using **Run workflow**. Download the `vcmi-builds-installer` artifact, verify checksum and certificate, install using `adb install -r vcmi-builds.apk`, and check that existing settings, token, saved candidates and results persist. `assembleDebugAndroidTest` compiles instrumentation tests but does not run them without an Android device.

To run device test instrumentation, install the same-key-signed debug and test APKs and use:

```sh
adb shell am instrument -w -e phase candidate dev.vcmi.builds.test/dev.vcmi.builds.TokenStoreInstrumentation
```

Only perform that command using test APKs signed compatibly with the existing app; do not overwrite a real installation with a differently signed debug APK.

## Contract ownership

The **VCMI Thor repository** owns `CI/thor-validation-plan.json`, the plan validator, receipt binding and packaged artifacts. This downloader only consumes and verifies those immutable packaged contracts; do not copy or independently author the game's validation plan. See [candidate contract](docs/CANDIDATE_CONTRACT.md).

## Migration / history

The source was extracted from [`vcmi_thor/tools/vcmi-builds`](https://github.com/CapnChaosDK/vcmi_thor/tree/ayn-thor-dual-screen/tools/vcmi-builds) using `git-filter-repo`; Git commits affecting that subdirectory are preserved as standalone history. The former root-level helper workflow was migrated into this repository. The VCMI Thor source and workflow should be removed **only after** signing, CI, device update and candidate download verification succeed.

This downloader is a separate product and should not trigger native VCMI builds when its own code changes.
