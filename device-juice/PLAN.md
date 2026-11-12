# Device Juice: Karoo 3 device battery extension (investigation + design plan)

Name: **Device Juice**. A karoo-ext extension that gives a clear overview of the battery state of every paired device, **mainly before and after a ride**. Covered devices: Shimano Di2 (per component), Garmin Varia, Favero Assioma (L + R), HR strap, and the Karoo itself. A compact in-ride data field is a secondary feature.

Mockup: [`mockup.html`](./mockup.html). It shows the pre-ride and post-ride overview screens at 480×800 in dark and light ride modes, plus notifications, in-ride fields and the colour scale.

Status labels used throughout:
- **Confirmed**: read in source, with `file:line`. karoo-ext paths are relative to `hammerheadnav/karoo-ext` at commit `f79f103` (lib version 1.1.9, `lib/build.gradle.kts:15`, `minSdk = 23` at `:28`). `KE/` = `lib/src/main/kotlin/io/hammerhead/karooext/`. Other repos are named in the reference.
- **Hypothesis**: inferred from protocol knowledge, manufacturer specs from memory, or naming. Not verified on a device.

Decisions (11.11.2026):
- **Name:** Device Juice. User-facing text says "devices", not "sensors" (the list includes the Karoo).
- **Green band** starts at 50 % (section 4).
- **Di2 is paired through Ki2.** Real Di2 % depends on open question 7. If streaming the Ki2 data type works in milestone 0, `Ki2Source` moves from v2 to v1; if not, Di2 shows the level Ki2 passes to Karoo.

---

## TLDR

1. **The SDK does not give sensor percentages.** Every external sensor is reported as one of 5 `BatteryStatus` levels (NEW / GOOD / OK / LOW / CRITICAL, plus INVALID). Only the Karoo's own battery is a real percentage. **Confirmed**: `KE/models/BatteryStatus.kt:28-48`, `KE/models/KarooEvent.kt:556-568`, `KE/models/DeviceEvent.kt:43-44`. The Saftladen author found the same (`hamann/karoo-saftladen` README.md:97-99).
2. **One SDK event lists all sensors.** `SavedDevices` (since 1.1.5) gives every paired device's name, connection type, enabled flag, last battery level, the time of that reading, and a per-component map for multi-part sensors such as Di2. **Confirmed**: `KE/models/KarooEvent.kt:493-611`.
3. **Pre-ride and post-ride flows are possible with SDK hooks.** The extension service sees `RideState` transitions (Idle / Recording / Paused). It can post a Control Center `SystemNotification` whose `actionIntent` opens our overview screen. It can show an `InRideAlert`. **Confirmed** (section 3.2). There is no event for "ride about to start", so pre-ride uses the app itself, a ride page, and recording start.
4. **Recommended approach:** v1 = full-screen Battery overview app (pre/post ride) + ride-end notification + recording-start alert + compact in-ride field, all fed by `SavedDevices` + the Karoo `BATTERY_PERCENT` stream. Colours use 4 bands, each paired with an icon and a word. Coarse levels map onto the bands and are drawn as levels, never as made-up percentages. Charging advice is rule-based. "Rides left" is learned locally and labelled as an estimate. v2 adds real percentages from Ki2 (Di2) and from direct BLE reads.
5. **Biggest open question:** does `SavedDevices` update during or right after a ride, or only when the device list changes? Milestone 0 tests this on a real Karoo.

---

## 1. Data availability

### 1.1 What the SDK exposes

| Source | What it gives | Resolution | Status / Basis |
|---|---|---|---|
| `SavedDevices` event | `SavedDevice(id, connectionType, name, enabled, details, components, supportedDataTypes, gearInfo)` | per device | **Confirmed** `KE/models/KarooEvent.kt:498-551` |
| `SavedDevice.DeviceDetail` | `lastBattery: BatteryStatus?`, `lastBatteryUpdate: Long?` (ms epoch), `manufacturer`, `serialNumber` | 5 levels + timestamp | **Confirmed** `KE/models/KarooEvent.kt:556-576` |
| `SavedDevice.components` | `Map<String, DeviceDetail>?`, null for single devices | 5 levels per component | **Confirmed** `KE/models/KarooEvent.kt:534-539`. The real key names are a **Hypothesis**; Saftladen's docs only show an illustrative example (`docs/report-format.md:50-58`) |
| `BatteryStatus` | NEW (>95), GOOD (>80), OK (>45), LOW (>15), CRITICAL (>0), INVALID | 5 levels | **Confirmed** `KE/models/BatteryStatus.kt:28-48` |
| `DataType.Type.BATTERY_PERCENT` stream | Karoo head unit, field `BATTERY_PERCENT` | exact % | **Confirmed** `KE/models/DataType.kt:965-968`, field `:1445`; used this way in Saftladen `report/BatteryReporter.kt:102-107` |
| `DataType.Type.SHIFTING_BATTERY` stream | `SHIFTING_BATTERY_STATUS`, `..._FRONT_DERAILLEUR`, `..._REAR_DERAILLEUR` | value = `BatteryStatus.ordinal` | Fields **Confirmed** `KE/models/DataType.kt:873-876, 1457-1469`. Ordinal encoding **Confirmed** in SDK sample `app/.../IncrementalShiftingSource.kt:90-98` and Ki2 `karoo/device/ShiftingDevice.kt:277-279` |
| Extension-provided sensors | `OnBatteryStatus(status: BatteryStatus)` | 5 levels | **Confirmed** `KE/models/DeviceEvent.kt:43-44`. Even an extension that knows the exact % can only pass Karoo a level (SDK sample `app/.../DoubleHrSensor.kt:93-97` converts a BLE % with `fromPercentage`) |

Consequences:
- No SDK path carries a sensor percentage into Karoo OS. Any real % must be read by our extension directly, or come from another extension that reads it directly.
- There is no SDK event for native sensor connection state. Freshness comes from `lastBatteryUpdate`. **Confirmed** by absence in `KE/models/KarooEvent.kt` (event classes at lines 29-675).

### 1.2 Per device (rider's setup)

| Device | SDK (v1) | Real % possible? | How | Status / Basis |
|---|---|---|---|---|
| **Karoo 3** | exact % | Yes, already | `BATTERY_PERCENT` stream | **Confirmed** |
| **Di2 main battery / RD** | level | Yes, via **Ki2** | Ki2 reads Shimano's private ANT profile; battery % is byte 4 of the battery/speeds page | **Confirmed** `valterc/ki2` `ShimanoShiftingProfileHandler.java:305-336` |
| **Di2 FD** | level (`..._FRONT_DERAILLEUR`) | As RD (FD normally runs off the main battery) | | Field **Confirmed**; wiring **Hypothesis** |
| **Di2 shifters (coin cells)** | unknown; maybe a component entry | 4 levels via Ki2 | 2-bit level per wireless switch | **Confirmed** Ki2 `ShimanoShiftingProfileHandler.java:150-159`. Karoo-native listing is a **Hypothesis** |
| **Garmin Varia** | level | Probably not | ANT+ common page 82 = status (same 5 states) + coarse voltage | **Hypothesis** |
| **Assioma L / R** | level; L/R split unknown | Only over BLE | BLE Battery Service 0x180F / 0x2A19 | **Hypothesis**. L/R split is an open question |
| **HR strap** | level | Over BLE: yes | BLE Battery Service 0x180F | Read pattern **Confirmed** in SDK sample `DoubleHrSensor.kt:93-97`; strap support **Hypothesis** |

For ANT+-paired sensors the 5 levels are probably the full resolution the sensor sends (the levels match ANT+ page 82 states). **Hypothesis.**

---

## 2. Alternatives for real percentages

| Option | Gain | Cost / risk | Verdict |
|---|---|---|---|
| **A. SDK only** | All sensors, no radio use, no pairing conflicts | Levels only; refresh timing unknown | **v1 baseline** |
| **B. Ki2 interop** for Di2 | Real Di2 %, shifter levels | Rider pairs Di2 through Ki2 and adds the Ki2 device as an Extension sensor (Ki2 README). Ki2 publishes `ki2::DATATYPE_SHIFTING_BATTERY_PERCENTAGE` (`ki2/app/src/main/res/xml/karoo_extension_info.xml:3,13`). Whether another extension can stream it via `OnStreamState.StartStreaming(DataType.dataTypeId("ki2", …))` is a **Hypothesis**. `dataTypeId` **Confirmed** `KE/models/DataType.kt:1830-1832` | **v2, optional** |
| **C. Own Shimano ANT implementation** | Real Di2 % without Ki2 | Ki2 is MIT (`ki2/LICENSE`). Needs ANT lib (`ki2/app/build.gradle:114`) + `RequestAnt` (`KE/models/KarooEffect.kt:136`); a second channel alongside Karoo/Ki2 is untested. Shimano BLE protocol is proprietary | **No** |
| **D. Own BLE 0x180F reads** | Real % for BLE-paired sensors | `RequestBluetooth` (`KE/models/KarooEffect.kt:110`). Android shares one link between GATT clients (**Hypothesis**); some sensors allow only one connection. Matching a BLE device to a `SavedDevice` is a **Hypothesis** | **v2, opt-in** |
| **E. Own ANT+ page-82 listener** | Voltage | Voltage-to-% curves are guesswork | **No** |

Prior art (GitHub topic `karoo-ext`, `timklge/awesome-karoo` README):
- **Saftladen** (`hamann/karoo-saftladen`, Apache-2.0): `SavedDevices` snapshot at ride end, POSTed as JSON. Watches `RideState` for Recording/Paused → Idle (`extension/SaftladenExtension.kt:64-76`). Posts a `SystemNotification` with `actionIntent` (`:106-112`). No in-ride field and no on-device overview. This proves the post-ride data path.
- **KarooHAIntegration** (`DannyBuilth-Snoad/KarooHAIntegration`): `SavedDevices` + `BATTERY_PERCENT` (`MainActivity.kt:172-207`).
- **Ki2** (`valterc/ki2`, MIT): the only source of real Di2 % found.
- Single-device battery extensions exist (Supernova, Pinion, others). None aggregates all sensors or gives charging advice. **Confirmed** by search, not exhaustive.

---

## 3. Product shape

### 3.1 Surfaces, in priority order

| # | Surface | When | Content |
|---|---|---|---|
| 1 | **Battery overview app screen** (480×800, Compose) | Opened from the Karoo app menu, from the notification, or automatically at boot if something is red | Verdict line, "Needs attention" cards (lowest first), "Healthy" compact block, advice per device |
| 2 | **Post-ride variant** of the same screen | Ride-end notification → Open | Grouped by when to charge, with drop per device for this ride |
| 3 | **Ride-end notification** (`SystemNotification`) | Recording/Paused → Idle | "Charge Assioma R tonight · 2 more before your next ride" + Open |
| 4 | **Recording-start alert** (`InRideAlert`) | Idle → Recording, if anything is red/orange | "Assioma R · under 15 % · Charge before your next ride" |
| 5 | **In-ride field** (secondary) | Rider adds it to a page | 1×1 "weakest battery"; strip variant that is blank while all is fine |
| 6 | **Pre-ride page** (optional) | Rider adds a full-page field to the profile's first page | The overview as a data field, visible before pressing Start |

### 3.2 SDK hooks for these flows

| Need | Hook | Status / Basis |
|---|---|---|
| Know when a ride starts/ends | `RideState` consumer: `Idle`, `Recording`, `Paused(auto)`; current state delivered on subscribe | **Confirmed** `KE/models/KarooEvent.kt:38-73` |
| Count only real transitions | Ignore the first `Idle` on subscribe; treat Recording/Paused → Idle as ride end. Idle is reported both when a ride is saved and when it is discarded | **Confirmed** Saftladen `SaftladenExtension.kt:58-76` |
| Open our screen from a notification | `SystemNotification(id, message, subText, header, style, action, actionIntent)`; `actionIntent` launches an activity | **Confirmed** `KE/models/KarooEffect.kt:174-212`; used in SDK sample `SampleExtension.kt:295-300` and Saftladen `:106-112` |
| Alert during a ride | `InRideAlert(id, icon, title, detail, autoDismissMs, backgroundColor, textColor)`. It has no action, so it cannot open an app | **Confirmed** `KE/models/KarooEffect.kt:221-253` |
| Run without the app open | Karoo binds the extension service once the APK is installed; it receives events in the background | **Confirmed** for install time (Saftladen `docs/development.md:126-127`). Rebinding after reboot is a **Hypothesis** (expected) |
| "About to ride" signal | None. `ActiveRideProfile` and `ActiveRidePage` (since 1.1.5) report the selected profile and visible page | **Confirmed** `KE/models/KarooEvent.kt:650-684`. Whether `ActiveRidePage` fires while Idle, when the rider opens the ride screen before Start, is a **Hypothesis**. If it does, it is the best pre-ride trigger |
| Open the app automatically | Not offered by the SDK. Starting an activity from the background is restricted on Android 10+ (**Hypothesis** for Karoo OS) | Use the notification instead |
| App in Karoo menu | Sideloaded apps with a launcher activity appear in the Karoo app list | **Hypothesis** (expected, as for the SDK sample) |

Pre-ride strategy, best first:
1. If `ActiveRidePage` fires while Idle: post a `SystemNotification` "1 device won't last the ride · Open" once per session.
2. On Idle → Recording: `InRideAlert` if anything is red or orange.
3. Rider adds the full-page overview field to the first page of their ride profile.
4. Rider opens the app from the menu.

### 3.3 Overview screen layout

See mockup, "Before the ride" and "After the ride".
- **Verdict line** answers "can I ride?": red "1 device won't last the ride", orange "Charge 3 devices", green "All set: 8 devices ready".
- **Needs attention** (red/orange/yellow) as large cards: icon, name, source line, value (level word or %), gauge, advice and estimate basis. Red cards get a tinted fill as well as the colour edge.
- **Healthy** collapsed into a two-column block of name + mini gauge. A stale reading (not updated in 7 days) shows grey with a "last seen" note.
- Post-ride groups by urgency ("Charge" / "Fine for now") and shows each device's drop during the ride (level change, or −N % for exact sources).
- Sorting: band (red → grey), then lowest value, then name. Each Di2 component, Assioma L/R and the Karoo get their own row.

---

## 4. Colour scale

| Band | Exact % | Coarse level mapped here | Icon (shape cue) | Default advice |
|---|---|---|---|---|
| Green | ≥ 50 % | NEW (>95), GOOD (81–95), OK (46–80) | filled circle with check | "OK for ~N rides", or nothing |
| Yellow | 30–49 % | none | half-filled circle | "Charge this week" |
| Orange | 15–29 % | LOW (16–45) | triangle with ! | "Charge before next ride" |
| Red | < 15 % | CRITICAL (1–15) | filled octagon with ! | pre-ride: "Charge before this ride"; post-ride: "Charge tonight" |
| Grey | no reading | INVALID, or older than 7 days | circle with ? | "Not seen since …, wake the sensor" |

Why this differs from the proposed scale (≥60 / 30–59 / 15–29 / <15):
- **Green from 50 %, not 60 %.** The OK level covers 46–80 %. With a 60 % line it would sit across green and yellow, and most healthy ANT+ sensors would show yellow most of the time. At 50 % only the 46–49 % sliver of OK is "too green", which is harmless.
- **Coarse levels take the worse band.** LOW (16–45) spans orange and yellow, so it shows orange. A level is drawn as 5 segments, never as a %.
- Red at < 15 % matches the SDK's CRITICAL boundary.

Readability (sun, colour blindness):
- Every band has a **different icon shape**, a **word or number**, and a **gauge length**. Colour is never the only cue.
- Each ride mode has its own palette: brighter on black, darker and more saturated on white. Red and orange differ in lightness as well as hue, and red is the only filled octagon.
- Typeface: **Roboto** for text and **Roboto Condensed** for names and values. Roboto is the Android system font, so the native build needs no bundled font files (Roboto Condensed is a free Google font). Large sizes (22-30 px on app screens, 44 px on the 1×1 field), 2 px minimum strokes, no hairlines.

---

## 5. Charging advice and estimates

### 5.1 Advice rules (v1, no learning needed)

| Condition | Advice |
|---|---|
| Red, pre-ride | "Charge before this ride" |
| Red, post-ride | "Charge tonight" |
| Orange | "Charge before next ride" |
| Yellow | "Charge this week" |
| Green with estimate | "OK for ~N rides" |
| Green, no estimate | nothing (keeps the screen calm) |
| Coin-cell device (HR strap, Di2 shifters) | "Replace CR2032" / "Replace CR1632" instead of "Charge" |
| Stale / grey | "Not seen since <date>. Wake the sensor to update" |
| Exact source with estimate below the typical ride length | escalate one band ("Charge before this ride") |

### 5.2 "Rides / hours left" estimate

Feasible, with honest labels. Stored locally (Room or DataStore); nothing leaves the device.

**Learned rate (preferred):**
- On each ride end, store per device: start value, end value, ride duration, timestamp.
- **Exact sources** (Karoo, Di2 via Ki2, BLE 0x180F): drain = Δ% / ride-hours. Keep an exponentially weighted average over the last ~6 rides.
- **Level-only sources:** record when a device crosses a level boundary and the riding hours since the previous crossing. Hours per level band ≈ hours between crossings ÷ band width. This is coarse and needs 2+ crossings, so these devices usually rely on the fallback.
- hours left = current % (or band midpoint for levels) ÷ drain/h. rides left = hours left ÷ median ride duration (from the stored rides).
- Label: "~N rides left · your last 6 rides".

**Fallback (rated life):** typical runtime per device type. All values are **Hypothesis** from manufacturer claims as remembered, and must be checked before release:

| Device | Rated life (to verify) |
|---|---|
| Karoo 3 | ~12 h per charge |
| Di2 12-speed main battery | ~1,000+ km per charge, varies with shifting |
| Di2 wireless shifter coin cell (CR1632) | ~1.5 years |
| Assioma Duo / Pro | ~50-60 h per charge |
| Varia RTL515 radar + light | ~6 h solid light, ~16 h flash or radar only |
| Garmin HRM-Pro (CR2032) | ~1 year at 1 h/day |

Label: "under ~7 h left · rated life". When a learned rate exists it replaces the fallback, and the label changes.

**Never** show an estimate as a precise number: always "~", rounded to whole rides or hours, with the basis named.

---

## 6. Implementation plan

### 6.1 Module structure (follows the karoo-ext sample app layout)

```
device-juice/
  app/src/main/kotlin/…/devicejuice/
    DeviceJuiceExtension.kt     // KarooExtension; registers data types; watches RideState
    data/
      BatteryRepository.kt      // Flow<List<BatteryReading>>: merge, prefs, sort, band
      SavedDevicesSource.kt     // consumerFlow<SavedDevices>() → readings per device + component
      KarooBatterySource.kt     // BATTERY_PERCENT stream
      (v2) Ki2Source.kt         // StartStreaming(dataTypeId("ki2", …))
      (v2) BleBatterySource.kt  // GATT 0x180F, opt-in
      RideLog.kt                // Room: per-ride start/end values per device
      Preferences.kt            // DataStore: names, hidden, thresholds, notifications on/off
    model/
      BatteryReading.kt         // id, componentKey, name, kind, level(1..5)?, percent?, updatedAt, source
      Band.kt                   // GREEN/YELLOW/ORANGE/RED/GREY + mapping rules (section 4)
      DeviceKind.kt             // SHIFTING, SHIFTER_COIN, RADAR_LIGHT, POWER, HR, HEAD_UNIT, OTHER
    advice/
      AdviceEngine.kt           // section 5.1 rules, pure function
      Estimator.kt              // section 5.2 learned rate + rated-life fallback
      RatedLife.kt              // table of defaults per DeviceKind
    ride/
      RideWatcher.kt            // RideState transitions → snapshot, RideLog, notifications
      Notifier.kt               // SystemNotification (post/pre), InRideAlert (start)
    view/
      WeakestFieldDataType.kt   // 1×1 + strip, Glance, graphical
      OverviewFieldDataType.kt  // optional full-page pre-ride field
    ui/
      OverviewActivity.kt       // intent action …MAIN; extra mode=pre|post
      OverviewScreen.kt         // Compose, matches mockup
      SettingsScreen.kt         // rename/hide, thresholds, notification toggles
  app/src/main/res/xml/extension_info.xml
```

### 6.2 Data flow

```
SavedDevices ───────┐
BATTERY_PERCENT ────┼─► BatteryRepository ─► readings (banded, sorted, prefs applied)
(v2) Ki2 / BLE ─────┘          │
                                ├─► OverviewScreen (pre/post)  ◄── SystemNotification.actionIntent
                                ├─► WeakestFieldDataType (in-ride)
RideState ─► RideWatcher ───────┤   start: InRideAlert if red/orange
                                └─► end: snapshot → RideLog → Estimator → SystemNotification
```

Merging: a fresher exact % from a direct source wins over the Karoo level. The in-ride field re-renders only when the weakest reading changes, at most every 5 s (the reported ~1 Hz view limit is **Unverified**).

### 6.3 Milestones

| # | Milestone | Done when |
|---|---|---|
| 0 | **Device spike** | On the rider's Karoo 3 with their sensors, log raw `SavedDevices` at boot, at Idle→Recording, every 5 min, at ride end and +5 min. Answer: does it refresh mid-ride and at ride end; Di2 component keys; Assioma L/R split; Varia battery present? Also: does `ActiveRidePage` fire while Idle? Does streaming the Ki2 data type from a second app work? |
| 1 | Skeleton | Extension installs, launcher activity, Karoo % shown |
| 2 | Repository + bands | `SavedDevices` flattened; band mapping; unit tests on milestone-0 fixtures |
| 3 | Overview screen | Pre/post variants in Compose, dark + light, matches mockup |
| 4 | Ride hooks | Ride-end notification opens post-ride screen; recording-start alert; once per ride |
| 5 | Advice + estimates | Rules engine; RideLog; learned rate; rated-life fallback; labels |
| 6 | In-ride field | 1×1 weakest + strip (Glance) |
| 7 | Settings | Rename/hide, thresholds, toggles |
| 8 | v2 precision | Ki2 source, then opt-in BLE |
| 9 | Release | Signed APK, README, sideload instructions |

If `SavedDevices` does not refresh at ride end: (a) re-subscribe after a short delay (**Hypothesis** that this returns new values), (b) use the `SHIFTING_BATTERY` stream for Di2, (c) show "as of <time>" on every reading.

### 6.4 Testing

- **Unit:** band mapping, sorting, advice rules, estimator, ride-transition detection. All pure functions, tested on JSON fixtures from milestone 0 (`SavedDevices` is `@Serializable`).
- **Fake sensors:** a debug flavour with an extension sensor that cycles all levels and a fake exact %, based on the SDK sample (`StaticHrSource.kt:68`, `IncrementalShiftingSource.kt:90-98`).
- **karoo-ext-testing** (`nikosavola/karoo-ext-testing`): Robolectric test doubles for `KarooSystemService`, for `RideWatcher` and notification tests. API not reviewed yet (**Hypothesis** that it covers `SavedDevices` and `RideState`).
- **Compose previews / screenshot tests** for pre, post, all-green and stale states, dark and light.
- **On device:** a short indoor ride to check the start/end hooks, then an outdoor ride in sun for legibility.

### 6.5 Risks and open questions

| # | Question | Status | Needs |
|---|---|---|---|
| 1 | Does `SavedDevices` refresh mid-ride / at ride end? | **Answered (12.11.2026): no.** Readings change only when a sensor (re)connects; see SPIKE.md Results | Design: show "as of" times; post-ride drop needs another source |
| 2 | Di2 component keys; are shifters listed? | **Unverified** | Device test |
| 3 | Are Assioma L and R separate entries? | **Unverified** | Device test |
| 4 | Does `ActiveRidePage` fire while Idle (pre-ride trigger)? | **Answered (12.11.2026): yes**, on each page swipe before Start | none |
| 5 | Di2 paired natively or via Ki2? | **Decided**: via Ki2 | none |
| 6 | Assioma / HR strap on BLE or ANT+? Only BLE allows a real % | Open | Rider |
| 7 | Can one extension stream another extension's data type? | **Hypothesis** | Device test, milestone 0. Decides whether `Ki2Source` is v1 |
| 8 | Rated-life values in 5.2 | **Hypothesis** | Check manufacturer specs |
| 9 | Level-only estimates need several rides to learn and stay coarse | Known limitation | none |
| 10 | Karoo OS updates could change `SavedDevices` contents | Risk | none |
