# VCMI Builds autonomous development guide

This repository contains only the standalone Android VCMI build downloader / Thor candidate validation companion. It is not the VCMI engine and does not contain native C++ or the Thor Android game app.

## Product boundaries

- Keep application ID `dev.vcmi.builds`, signing alias `vcmi-builds`, and persistent app data formats compatible unless a separately reviewed migration explicitly requires otherwise.
- Default target is `CapnChaosDK/vcmi_thor` with candidate branch `ci/thor-candidate-validation`; allow users to configure repository and branch as existing app supports.
- Thor owns its human-authored plan, Python validation, receipt hashes and compiled candidate artifacts. Consume and verify exactly the packaged artifact contract without guessing plans or asserting CI/device successes.
- Preserve the GitHub token's existing Android Keystore-backed encryption behavior; never log, commit, export or send tokens or signing secrets.
- Android confirmation is required for APK installs; no silent installs or bypass of certificate checks.
- Keep every candidate validation result tied to exact repository, commit, APK checksum and plan. Never infer Pass from missing/blocked tests.

## Engineering workflow

1. Inspect current source, tests, README and docs/CANDIDATE_CONTRACT.md before choosing scope.
2. Implement coherent end-to-end user-facing improvements, not chains of micro-slices. Avoid gratuitous architecture or dependency changes.
3. Cover parsing, integrity boundaries, persistence, cancellation, lifecycle, errors and compatibility where relevant.
4. Run `gradle :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest` when Android tools are available; use independent PR checks otherwise. Only test APK signing with the preserved key on trusted branches.
5. Maintain a brief focused manual AYN Thor validation checklist for changed workflows; do not mark device tests passed without a user's actual test.
6. Never modify the VCMI Thor engine, its candidate workflow or shared plan from this repository. Propose cross-repository changes separately.

## CI and releases

`main` is the stable development branch. PRs compile/lint without signing credentials. Trusted `main` pushes produce signed `vcmi-builds-installer` artifacts with the existing signing secrets. Preserve compatibility with installed app data across updates. Never rotate the release signing key as part of routine upgrades.
