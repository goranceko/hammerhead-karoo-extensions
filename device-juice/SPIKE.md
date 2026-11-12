# Device Juice: milestone 0 device spike

The debug build logs raw Karoo data so we can answer the open questions in
[PLAN.md](PLAN.md) section 6.5 on a real Karoo 3 with the real devices. It draws no conclusions;
read the log.

## Install

1. Build: `./gradlew :device-juice:assembleDebug` (needs `gpr.user` / `gpr.key` in `local.properties`,
   see the root README), or take `device-juice.apk` from a GitHub release / the CI artifact.
2. Sideload to the Karoo: open the release's APK link in the Hammerhead Companion app and share it
   to the Karoo, or `adb install -r device-juice/app/build/outputs/apk/debug/device-juice-debug.apk`.
3. Karoo binds the extension service on install. Open **Device Juice** from the Karoo app menu
   once: the Karoo % shows at the top, the "Device spike (debug)" panel shows the latest
   `SavedDevices` and the last 40 log lines. Check the log has `[KAROO] connected=true`.
4. Wake every device first (spin the cranks, shift, switch on Varia, wear the HR strap) so each has
   a recent reading.

## On the ride (an indoor ride of ~20 min is enough)

| Step | Why |
|---|---|
| Restart the Karoo before the ride, then wait 1 min | `BOOT` / `trigger=boot` lines: does the service rebind after a reboot? |
| On the launcher, **select the ride profile and open the ride screen, but don't press Start yet**; swipe through 2 pages; wait 30 s | Q4: does `ACTIVE_PAGE` fire while Idle? |
| Start recording; ride ≥ 10 min (at least two `periodic` checkpoints); shift a few times | Q1, Q7 |
| Pause once for ~1 min, resume | `pause` / `resume` checkpoints |
| End and save the ride; leave the Karoo on for **at least 6 min** | `ride_end` and `ride_end+5m` checkpoints |
| Open Device Juice → **Share log** or **Save to Downloads** | Collect `device-juice-spike-<time>.log` |

Getting the log off the Karoo without adb: **Save to Downloads** writes
`Download/DeviceJuice/device-juice-spike-<time>.log`; connect the Karoo over USB (file transfer)
and copy it. With adb: `adb logcat -s DeviceJuice` or
`adb shell run-as io.github.goranceko.devicejuice cat files/spike/spike.log`.

## Log format

`<UTC time> [TAG] message`, one event per line, in `files/spike/spike.log` (rotated at 512 KB, 4
files kept) and logcat tag `DeviceJuice` (long lines are split into `(i/n)` parts).

| Tag | Content |
|---|---|
| `BOOT` | recorder started / stopped (service created / destroyed) |
| `KAROO` | `connected=…`, lib version, hardware, serial |
| `RIDE_STATE` | raw `state=` and `transition=` (`none`, `RIDE_START`, `PAUSE`, `RESUME`, `RIDE_END`). The first state after binding is never a transition |
| `SAVED_DEVICES` | raw `SavedDevices` JSON. `trigger=update n=<k>` = an event from the long-lived consumer. `trigger=<boot\|ride_start\|pause\|resume\|ride_end\|ride_end+5m\|periodic> source=cached ageMs=…` = the last event we had; `source=fresh sameAsCached=…` = a brand-new subscription made at that moment |
| `ACTIVE_PAGE` / `ACTIVE_PROFILE` | event JSON with `phase=` (ride phase at the time) |
| `STREAM` | `KAROO_BATTERY` (`BATTERY_PERCENT`), `SHIFTING_BATTERY`, `KI2_SHIFTING_BATTERY_PCT` (`ki2::DATATYPE_SHIFTING_BATTERY_PERCENTAGE`), `KI2_SWITCH_BATTERY` (`ki2::DATATYPE_SWITCH_BATTERY`). Logged on subscribe, on every change of state/value, on error, on completion |
| `MANUAL` | Share / Save pressed on the screen |

## Which lines answer which question (PLAN.md 6.5)

**1. Does `SavedDevices` refresh mid-ride / at ride end?**
- Count `[SAVED_DEVICES] trigger=update` lines and note their `phase=`. Updates with
  `phase=RECORDING` mean it refreshes mid-ride; updates right after `[RIDE_STATE] … transition=RIDE_END`
  mean it refreshes at ride end. Only `n=1` at boot means it is sent once per subscription.
- Compare `lastBatteryUpdate` for the same device across `trigger=ride_start`, `periodic`,
  `ride_end`, `ride_end+5m`. Changing timestamps = fresh readings.
- `source=fresh sameAsCached=false` while the long-lived consumer saw no new `update` means
  re-subscribing returns newer values (fallback (a) in PLAN.md 6.3 works). `sameAsCached=true`
  everywhere plus unchanged `lastBatteryUpdate` = no refresh at all; fallbacks (b)/(c) apply.

**2. Di2 component keys; are shifters listed?**
- In any `SAVED_DEVICES` JSON, find the Di2 / Ki2 device and read its `components` map: the keys
  are the answer. Check whether entries for the wireless shifters (switches) appear.
- Since Di2 is paired through Ki2, note whether the Di2 shows up as a native device, as the Ki2
  extension device, or both, and its `connectionType`.
- Cross-check against `[STREAM] SHIFTING_BATTERY … values={…}` (field ids `FIELD_SHIFTING_BATTERY_STATUS…`;
  values are `BatteryStatus` ordinals: 0 NEW, 1 GOOD, 2 OK, 3 LOW, 4 CRITICAL, 5 INVALID).

**3. Are Assioma L and R separate entries?**
- In the `SAVED_DEVICES` JSON: two Assioma devices, or one device with a `components` map with
  left/right keys, or one entry with a single `lastBattery`.

**4. Does `ActiveRidePage` fire while Idle?**
- Any `[ACTIVE_PAGE] phase=IDLE …` line logged while you were on the ride screen before Start =
  yes (and the pre-ride trigger in PLAN.md 3.2 works). Only `phase=RECORDING`/`PAUSED` lines = no.
  Same check for `[ACTIVE_PROFILE] phase=IDLE` when you change profile on the launcher.

**7. Can one extension stream another extension's data type (Ki2)?**
- `[STREAM] KI2_SHIFTING_BATTERY_PCT phase=… Streaming values={…}` with a number = yes, and the
  value is the real Di2 %; `Ki2Source` moves to v1.
- `… error=…`, only `NotAvailable`/`Searching`/`Idle`, or nothing after `subscribe` = no (note which).
  Ki2 must be installed, the Di2 connected through it, and the Ki2 device added as a sensor.
- `KI2_SWITCH_BATTERY` shows the same for the switch (shifter) battery level.

Also note from the same log: whether the Karoo % (`KAROO_BATTERY`) streams while Idle, and whether
`[BOOT] spike recorder started` appears after a reboot without opening the app.

## Sending results back

Attach the exported log (or the relevant lines) to the milestone-2 work: `SavedDevices` JSON from it
becomes the unit-test fixtures (PLAN.md 6.4).

## Results

### Run 1: 12.11.2026, Karoo 3 (Android 12, API 32), indoor, HR strap only

| # | Question | Answer | Evidence |
|---|---|---|---|
| 1 | Does `SavedDevices` refresh mid-ride / at ride end? | **No.** Over 40 min (two rides, pauses, ride end, +5 min) no device's `lastBattery` or `lastBatteryUpdate` changed, and every fresh subscription matched the cache (`sameAsCached=true`). Re-subscribing (fallback (a) in PLAN.md 6.3) does not help. | `SAVED_DEVICES trigger=periodic/pause/ride_end/ride_end+5m … sameAsCached=true` |
| 1b | When does a reading change? | **When a sensor (re)connects.** Turning the HR strap off and on in Karoo Sensors pushed a new event to the existing consumer (`trigger=update n=4`) with a new `lastBatteryUpdate` for that device only. All other timestamps were from when those sensors last connected. | `10:43:39 SAVED_DEVICES trigger=update n=4` |
| 1c | Other `SavedDevices` events | Changing the ride profile flips `enabled` for profile-specific sensors (`trigger=update n=2/3`). `enabled` = used by the active profile. | `10:10:56 ACTIVE_PROFILE` followed by `update n=2` |
| 4 | Does `ActiveRidePage` fire while Idle? | **Yes**, on every page swipe on the ride screen before Start. `ActiveRideProfile` fires on profile change while Idle. The pre-ride trigger in PLAN.md 3.2 works. | `10:11:00–10:11:38 ACTIVE_PAGE phase=IDLE` |
| 2 | Di2 component keys / shifters | Partly. Di2 (paired via Ki2) is one `EXTENSION` device (`ki2::…`) with **no** `components`. SRAM AXS (native ANT+) lists `FRONT_DERAILLEUR`, `REAR_DERAILLEUR`, `LEFT_SHIFTER`, `RIGHT_SHIFTER`, each with its own level. | boot `SAVED_DEVICES` |
| 3 | Assioma L/R separate? | Partly. One device whose `components` has only `RIGHT_SIDE_POWER` (pedals were asleep; re-check with both pedals awake). | boot `SAVED_DEVICES` |
| 7 | Can we stream Ki2's data type? | **Open.** The subscription is accepted (no error) and goes `Searching`/`NotAvailable`, as expected with the Di2 absent. Needs a run with the bike. | `STREAM KI2_SHIFTING_BATTERY_PCT` |
| – | Varia | Radar and light are two separate devices. | boot `SAVED_DEVICES` |
| – | Karoo % | `BATTERY_PERCENT` streams in every ride state. | `STREAM KAROO_BATTERY` |
| – | Service lifetime | The service was bound right after install and ran the whole session. Rebind after reboot not tested yet. | `BOOT` |

Still to run, with the bike: Q2 (Di2 parts via Ki2 stream), Q3 (both pedals awake), Q7 (Ki2 %), and a reboot to check the service rebinds.

Known spike-app issues: **Share** does nothing on the Karoo (no share targets; use Save to Downloads or adb), and the saved file gets an extra `.txt` extension.
