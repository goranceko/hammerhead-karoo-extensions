# Hammerhead Karoo extensions

My extensions for the Hammerhead Karoo 3, built on the official
[karoo-ext](https://github.com/hammerheadnav/karoo-ext) SDK (lib 1.1.9, Kotlin, minSdk 23).

## Layout

One Gradle project, one Android module per extension:

| Folder | What | Status |
|---|---|---|
| [`device-juice/`](device-juice/) | **Device Juice**: battery overview of every paired device, before and after the ride. See [PLAN.md](device-juice/PLAN.md) and [mockup.html](device-juice/mockup.html) | Milestone 0/1: device spike + skeleton ([SPIKE.md](device-juice/SPIKE.md)) |
| `power-zone-bar/` | Apple Watch–style power zone bar | Not started |
| `common/` | Shared UI and karoo-ext helpers | Not started |
| `.github/workflows/` | Builds APKs into GitHub releases | `device-juice.yml`: debug APK on push to main; release on `device-juice-v*` tags |

Gradle path `:device-juice` maps to `device-juice/app` (layout in PLAN.md 6.1).

## Building

Needs JDK 17 or 21 (Gradle 8.11 does not run on JDK 25) and the Android SDK (platform 35).

```bash
./gradlew :device-juice:assembleDebug :device-juice:testDebugUnitTest
```

karoo-ext is published on GitHub Packages, so Gradle needs a GitHub user and a token with
`read:packages` in `local.properties` (not committed):

```properties
gpr.user=<github-user>
gpr.key=<token>
```

CI uses the `USERNAME` / `TOKEN` environment variables instead (the workflow passes `GITHUB_TOKEN`).

## Installing

The Karoo 3 installs APKs from a share link: open the APK link from a GitHub release in the
Hammerhead Companion app and share it to the Karoo.
