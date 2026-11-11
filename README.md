# Hammerhead Karoo extensions

My extensions for the Hammerhead Karoo 3, built on the official
[karoo-ext](https://github.com/hammerheadnav/karoo-ext) SDK (lib 1.1.9, Kotlin, minSdk 23).

## Layout

One Gradle project, one Android module per extension:

| Folder | What | Status |
|---|---|---|
| [`sensor-battery/`](sensor-battery/) | **Sensor Juice**: battery overview of every paired sensor, before and after the ride. See [PLAN.md](sensor-battery/PLAN.md) and [mockup.html](sensor-battery/mockup.html) | Plan done |
| `power-zone-bar/` | Apple Watch–style power zone bar | Not started |
| `common/` | Shared UI and karoo-ext helpers | Not started |
| `.github/workflows/` | Builds APKs into GitHub releases | Not started |

The Android project is not scaffolded yet.

## Building

karoo-ext is published on GitHub Packages, so Gradle needs a GitHub user and a token with
`read:packages` in `local.properties` (not committed):

```properties
gpr.user=<github-user>
gpr.key=<token>
```

## Installing

The Karoo 3 installs APKs from a share link: open the APK link from a GitHub release in the
Hammerhead Companion app and share it to the Karoo.
