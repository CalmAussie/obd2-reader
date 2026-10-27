# OBD2 Reader

An Android app that reads live engine data and trouble codes from a cheap ELM327 OBD2 adapter, over Bluetooth or Wi-Fi. It also has an Android Auto screen.

**[Download the latest APK](https://github.com/CalmAussie/obd2-reader/releases/latest/download/OBD2Reader.apk)** · [All releases](https://github.com/CalmAussie/obd2-reader/releases) · [Changelog](CHANGELOG.md)

## What it looks like

<p align="center">
  <img src="docs/demo.gif" width="300" alt="Animated demo: choosing the car first, the demo car connecting with start lights and a rev sweep, live readings gliding on the Dashboard, the driving score in its Everyday, Efficiency and Racing styles, the Race telemetry and Digital cockpit styles, the Graphs tab and trouble codes on the Diagnostics tab">
</p>

**[Try the interactive demo in your browser](https://raw.githack.com/CalmAussie/obd2-reader/main/preview/index.html)**: tap **Demo car**, switch styles with the palette button, and open **Android Auto** at the top to see the car screen. It's a web mock-up of the app, so nothing needs installing.

The real app, version 1.4.2, running on Android 15 with the demo car:

<p align="center">
  <img src="docs/screen-choose-car.png" width="180" alt="First launch: choose your car">
  <img src="docs/screen-dashboard.png" width="180" alt="Dashboard: the rev roundel, speed, coolant and fuel">
  <img src="docs/screen-graphs.png" width="180" alt="Graphs: live charts of engine speed and road speed">
  <img src="docs/screen-diagnostics.png" width="180" alt="Diagnostics: three trouble codes with a full explanation">
</p>

<p align="center">
  <img src="docs/screen-android-auto.png" width="560" alt="Android Auto screen: engine speed in the rev roundel, with speed, coolant, throttle, fuel and battery">
</p>

## Install
All you need is the APK. There's no Play Store listing, account, sign-in or other app to install, and it works offline.

<img src="docs/download-qr.png" width="140" align="right" alt="QR code linking to the APK download">

1. On your Android phone (Android 8 or newer), tap **[Download the latest APK](https://github.com/CalmAussie/obd2-reader/releases/latest/download/OBD2Reader.apk)**, or scan the QR code with the phone's camera.
2. Open the downloaded file. If asked, allow your browser or file app to **install unknown apps**, then tap **Install**.
3. If Google Play Protect warns about an unknown developer, tap **More details → Install anyway**. Every app from outside the Play Store gets this warning.
4. Open OBD2 Reader and choose your car (or **Not listed**). Then tap **Demo car** to try it straight away, or plug in an adapter and tap **Connect**.

**Updates:** the app checks GitHub for a new release once a day (and whenever you tap **Check for updates** under **Diagnostics → App updates**; tap the rest of that row to see what's changed since your version). When there is one, it shows what's new and, if you tap **Update**, downloads it and hands it to Android, which asks you to **Install**. The first time, Android asks you to allow OBD2 Reader to install apps. Your settings, drives and recordings are kept. Only the version check and the download go to GitHub. You can still install a newer APK over the old one by hand. **Samsung phones:** Auto Blocker (Settings › Security and privacy › Auto Blocker) blocks installs from anywhere but the Galaxy Store and Play Store, including the app's own updates. Turn it off to install or update, then turn it back on if you like.

Every release is checked automatically: the published APK is downloaded, installed on fresh Android 10, 13, 15 and 16 phones (emulators), plus Android 17 as an experimental check (its emulator doesn't run reliably on GitHub yet), opened, run with the demo car and checked for crashes.

## Use it
1. Plug the adapter into the OBD2 port (under the dashboard, driver's side) and turn the ignition on.
2. Open the app, tap **Connect** and choose how your adapter connects:
   - **Bluetooth:** the app lists paired and nearby adapters by name, whichever kind of Bluetooth they use (Classic or LE). If yours needs pairing, Android asks for the PIN, usually `1234` or `0000`.
   - **Wi-Fi:** join the adapter's Wi-Fi network first. The usual address is `192.168.0.10`, port `35000`.
3. No adapter? Tap **Demo car** to try the app with a simulated car.
4. After the first time, the app reconnects by itself whenever you open it or turn the ignition on.

Help, including Android Auto setup, is also inside the app under **Diagnostics → Help**.

## What it does
- **Live data:** RPM, speed, coolant, fuel, throttle, engine load, intake air, manifold pressure, MAF, timing advance, ambient and oil temperature, and battery voltage. Readings past their limit turn red.
- **Battery charging status:** beside the voltage, an estimate (marked est.) of whether the battery is charging, worked out from voltage and engine speed. Standard OBD2 has no battery-current (amps) reading.
- **Top speed:** the highest speed of the current drive shows beside the speed, on the phone and in Android Auto.
- **Swipe between tabs:** swipe sideways across the screen to move between the Dashboard, Graphs and Diagnostics.
- **Drive history:** every drive's summary (no location), with your drive score's trend over the last 20 drives; open any drive to share its card.
- **Battery with the ignition off:** the battery's voltage and rough charge while the car is off (from the adapter, without waking the car), and an optional overnight check for something draining the battery.
- **Battery check at start-up:** connect before starting the engine and the app measures how far the battery dips while starting, and keeps a history so a weakening battery shows up early.
- **Trip summary:** distance, driving and idle time, average and top speed, best 0–100 and scores when each drive ends (when you disconnect, or the ignition has been off for 3 minutes).
- **Driving alerts:** sound, vibration and a notification (on Android Auto too) for a hot engine or oil, a battery not charging, overcharging or low, and new trouble codes. Switch off under **Diagnostics → Alerts**.
- **Performance timers:** 0–100 km/h, 80–120 km/h and the quarter mile (an estimate, worked out from speed), or 0–60 and 50–70 mph, with your best times.
- **Video overlay and drive cards:** turn a recorded drive into an MP4 gauge overlay for your video editor ([how to use it](#video-overlay)), with the route's mini-map when the drive was recorded with it, or a drive into a picture card for Shorts, TikTok, Reels or Instagram, drawn in the style of your Dashboard layout, with one score (your Racing score from Race telemetry, otherwise your drive score: the average of Everyday and Efficiency) and the route's outline when the drive was recorded with it, shaded brighter where you drove faster. You see a preview of the card before it's shared.
- **Home-screen widget:** live speed, coolant and battery while connected; otherwise the last battery reading, code check and drive, with a Connect or Disconnect button. Resize it to 4 × 2, 4 × 1, 2 × 2 or 2 × 1.
- **Your app icon:** choose the icon's colour from eight colourways under **Diagnostics → App icon**.
- **Your readouts, your units:** choose which readings fill the Dashboard's main readouts, and km/h or mph and °C or °F separately.
- **N/A, never a guess:** a reading the car doesn't report, or stops answering, says N/A rather than a dash or an old number.
- **Your car first:** the app asks for your car's make, year, model and engine (engines are listed for every petrol, diesel and hybrid model in the catalogue) when you first open it, so manufacturer-specific trouble codes are explained for your brand, the rev dial uses your engine's redline, and your car's protocol is tried first. It then lists any readings that may not work on a car like yours, and why.
- **Driving score:** rates the current drive out of 100 for **Efficiency**, **Everyday** driving or **Racing** (track days), with a score for each habit and a tip for what would help most.
- **Graphs and drive logs:** live charts for up to four readings at once, in the order you choose, fast multi-reading polling, and CSV recording of whole drives (tap **Record drive** on the Dashboard once connected; recordings are under **Graphs → Saved drives**) that you can share to Excel or Google Sheets, optionally with your route (latitude and longitude on every row) for mapping software. Drives are only recorded when you tap **Record drive**, your location is only ever saved inside a drive you record with its route, and recordings are never included in backups.
- **Your location stays on your phone:** it's never sent to the developer or anyone else. It's only stored locally, in the CSV file of a drive you record with its route, and the app only uses it to draw the outline of that route on your share card. Shared drive cards carry only the outline's shape: no map, street names or coordinates. Positions come from your phone's own location service (GPS, plus the phone's motion sensors on Android 12 and newer), the same one maps apps use, so your phone's own Location settings apply as usual. Where GPS drops out at the very start or end of a drive (an underground car park, say), the outline is carried on by the distance the car's speed readings show, in the direction it was heading; it's an estimate, and the app has no maps.
- **Trouble codes, explained:** reads stored and pending codes from the engine (and, on many cars, the automatic transmission). About 2,860 standard (generic) codes are explained: what it means, how urgent it is, symptoms, common causes and what to check. Manufacturer-specific codes say where to look them up for your make, except a few Ford and Lincoln codes, which use Ford's own definitions for the model years Ford's documents cover. [Which cars get which codes](docs/CODE-SUPPORT.md) lists every model in the car list. Save the lot as a report to your phone, or share it with your mechanic. When a code is stored, the car's **freeze frame** (the engine's readings at that moment) is shown with it and included in the report.
- **Deeper checks (read-only):** under Diagnostics, **Readiness monitors** shows which of the engine computer's emission checks have finished (handy before an inspection) and its software version; **Self-test results** shows the measurements behind those checks, with their limits; **Codes from other modules** reads codes from the ABS, airbags, transmission, power steering, cluster and other modules, at the addresses your make uses, and can search every address for the rest; **Enhanced diagnostics** shows live readings your maker added beyond OBD2 where they're known (for example Ford transmission temperature and oil life; community-sourced, and only the ones your car answers) and every module's part numbers, software versions and stored VIN; **Custom readings** lets you add a reading by its request and formula. These need a CAN car (most from 2008, many earlier), except readiness and custom readings.
- **Read-only:** the app never clears codes, turns off the check-engine light or changes anything in the car. Every command is checked before it's sent, and anything that isn't a read is blocked.
- **Reconnects by itself:** picks the adapter back up when you open the app, start Android Auto or turn the ignition back on.
- **Readable adapter log:** every message to and from the adapter is explained in plain English (for example "Engine RPM: 3,176 rpm" under the raw reply), with the latest trouble codes and their definitions pinned at the top. Share it as a text file when troubleshooting.
- **Vehicle ID:** reads the VIN and shows the make and model year. If the car's VIN doesn't match your VIN plate, enter the right one (kept on your phone only).
- **Finds the right protocol:** tries every OBD2 protocol automatically. If your car won't connect, choose it under **Diagnostics → Your car** (69 makes from 2000 on) so the app tries that car's usual protocol first.
- **Background connection:** keeps working with the screen off, with a notification showing live RPM, speed and coolant.

## Compatibility

**Phones**
| | |
|---|---|
| Android | 8.0 (Oreo) or newer |
| Android Auto | Any car or head unit with Android Auto, wired or wireless, using the phone's Android Auto app |
| iPhone | Not supported (Android only) |

**Adapters**
| Type | Works with | Notes |
|---|---|---|
| Bluetooth Classic | ELM327 v1.4 and newer, and ELM327-compatible clones and STN-based adapters | Listed under Bluetooth; the app pairs it if needed |
| Bluetooth LE | "iPhone-compatible" ELM327 adapters, for example the Veepeak OBDCheck BLE+ | Listed under Bluetooth; no pairing needed |
| Wi-Fi | ELM327 Wi-Fi adapters | Default address `192.168.0.10`, port `35000`, changeable in the app |
| Maker-locked | ThinkCar ThinkDiag, BlueDriver and similar adapters made for their maker's own app | They work only if they answer like an ELM327. If not, the app tells you it's a maker-locked adapter |
| Not supported | USB adapters | |

**OBD2 protocols** (all nine an ELM327 supports, tried automatically)
| Protocol | Typical cars |
|---|---|
| ISO 15765-4 CAN (11-bit and 29-bit, 500 and 250 kbaud) | Almost every car from 2008 on |
| SAE J1850 PWM | 2000s Fords and Ford-based Mazdas |
| SAE J1850 VPW | Pre-2008 GM, Chrysler, Dodge and Jeep |
| ISO 9141-2 | 2000s Asian and European cars, including many Hondas, Mazdas and Toyotas |
| ISO 14230-4 KWP2000 (fast and 5-baud init) | 2000s Asian and European cars |

Many cheap ELM327 clones can't speak J1850 PWM or VPW. If you have a 2000s Ford or pre-2008 GM, Chrysler, Dodge or Jeep, use a genuine ELM327 or STN-based adapter.

**Cars**
| | |
|---|---|
| Australia | Petrol cars from about 2006, diesels from about 2007 (ADR 79). Many earlier imports also work |
| United States | 1996 on |
| Europe (EOBD) | Petrol from 2001, diesel from 2004 |
| Car catalogue | 69 makes from 2000 on, to help the app pick the right protocol: Japanese, American, European and Australian brands |
| Electric cars | Often limited or no standard engine readings. Teslas have no standard OBD2 port |
| Japanese-market imports | Some older ones use Japan's own standard and may not connect |

**What can be read:** standard (generic) OBD2 data: live engine readings, emissions-related trouble codes from the engine and, on many cars, the automatic transmission (about 2,860 generic P0, P2, P34, U0 and U3 codes are explained; the rest are named by system; manufacturer-specific codes are identified but, apart from a few Ford and Lincoln codes, not explained), and the VIN. When a car leaves out a reading, a close standard one is used where there is one, and labelled. **Diagnostics → Scan what your car reports** lists everything your car reports. **Diagnostics → Codes from other modules** reads codes from other modules such as ABS and airbags on CAN cars, at your make's own module addresses, and **Search every address** finds the rest; clearing them, and service resets, need the maker's own tools or a scanner made for your make.

## Video overlay
Turn a recorded drive into a video of your gauges (shift lights, rev arc, rpm, speed, top speed, throttle and a 30-second trace) to lay over dash-cam, GoPro or phone footage in a video editor.

**Record the drive**
1. Start your camera recording first.
2. In OBD2 Reader, connect to the car and tap **Record drive** on the Dashboard (it appears once you're connected). The overlay's clock starts at 0:00 the moment you tap it, so film the phone as you tap (or say "now") to make lining up easy later.
3. Drive, then tap **Stop recording**.

**Make the video**
1. On the **Graphs** tab, tap **Saved drives**, choose the drive, then **Make a video overlay**.
2. Choose a background:
   - **Green screen:** remove it in your editor with chroma key.
   - **Black:** use your editor's **Screen** blend mode to drop the black.
3. Choose a size: **1080p** (sharper) or **720p** (quicker to make). Then tap **Make video**.
4. Keep the app open while it works; it shows the progress and roughly how long is left.
5. The video is saved to **Movies › OBD2 Reader** on Android 10 and newer, where your editor can find it. On older phones, tap **Share** to send it to your editor, Drive or Files.

**In your editor**
1. Put your camera footage on the main track and the overlay on the track above it.
2. Remove the background: chroma key on green (for example CapCut's **Chroma key**, Premiere Pro's **Ultra Key** or DaVinci Resolve's **3D Keyer**), or set the overlay's blend mode to **Screen** for black.
3. Line the overlay up with your footage: slide it until its 0:00 sits on the moment you tapped **Record drive**.

The overlay is 30 frames a second, 16:9, with the gauges along the bottom, so it fits over landscape footage. It uses the colours of the style you've chosen, and your units (km/h or mph).

## Styles
Tap the palette icon on the Dashboard to choose a style. Your choice is saved. Every style also works in landscape: turn your phone sideways and the Dashboard puts the main dial on the left and the other readouts, Connect and status on the right. The first seven are colourways of the livery dashboard; the last three each have a dashboard layout of their own.

| Style | Look |
|---|---|
| Asphalt (default) | Orange and white livery stripes on dark grey |
| Endurance | Night-race navy and yellow |
| Kerb | Red and white kerb stripes |
| Pit lane | A light style in black and red, for sunlight |
| Drift | Hot pink and teal |
| Apex | Volt yellow on carbon black, sharply raked |
| British racing | Cream and gold on British racing green |
| Race telemetry | A data-logger screen: shift lights, big numbers and a live 30-second trace of engine speed, road speed and throttle |
| Digital cockpit | One big speed in a glowing ring, on layered glass |
| Cyberpunk | A neon heads-up display: a climbing rev bar, split-colour speed digits and glowing readouts |

## Android Auto
The car screen shows live RPM, speed, coolant, throttle, fuel and battery, refreshing about five times a second with each number gliding to the next reading, and lists trouble codes. It uses your style's colours, but its background, fonts and layout are always Android Auto's own.

Android Auto only shows Play Store apps until you allow others. On your phone:
1. Open Android Auto's settings (search "Android Auto" in Settings).
2. Scroll to the bottom and tap **Version** about 10 times, then confirm to turn on developer mode.
3. Tap the three-dot menu, then **Developer settings**, and turn on **Unknown sources**.
4. Reconnect to your car. OBD2 Reader appears in Android Auto's app list.

## Good to know
- **Open, minimised and closed:** while the app is open on screen, your phone doesn't lock. Minimised (Home, or Back from the Dashboard), it keeps running: the connection stays up and a drive you're recording keeps recording. Swipe it away from Recents to close it: it saves any recording, disconnects and stops, unless Android Auto is showing it on the car screen.
- **Not every reading on every car:** cars only answer the readings they support, so some readings say **N/A**. **Diagnostics → Scan what your car reports** shows what your car supports.
- **Finding adapters on Android 11 and older** needs Location turned on while searching (an Android rule).

## For developers

**Building:** every push to `main` runs [`.github/workflows/build.yml`](.github/workflows/build.yml). It runs the unit tests, builds a signed APK and publishes it as a GitHub release. Pushes that only change Markdown files don't build a new release. To build locally, open the project in Android Studio (JDK 17) or run `gradle assembleRelease testDebugUnitTest` with Gradle 8.7 and the Android SDK.

**Preview:** [`preview/index.html`](preview/index.html) is an interactive mock-up of the app, with a demo car, every style, the Graphs and Diagnostics tabs and the Android Auto screen. Open it in a browser. It mirrors the app's layouts and logic but is a separate web page, so check changes in the app itself too.

**Install test:** [`.github/workflows/install-test.yml`](.github/workflows/install-test.yml) runs after every release. It installs the published APK on emulators (Android 10, 13, 15 and 16, plus 17 as an experimental job that doesn't fail a release), starts the demo car, visits every tab and fails on any crash. Screenshots and results are pushed to the `install-test-reports` branch.

**QA:** push to the `qa` branch to run Android Lint, the unit tests and a build with compiler warnings, without publishing a release ([`.github/workflows/qa.yml`](.github/workflows/qa.yml)). The reports are committed back to `qa-reports/` on that branch.

**Releasing:** bump `obdVersion` in `gradle.properties` and add an entry at the top of [`CHANGELOG.md`](CHANGELOG.md). The release notes are taken from that entry.

**Signing:** updates only install over an older version when both are signed with the same key. The key lives in four repository secrets: `OBD_KEYSTORE_BASE64`, `OBD_KEYSTORE_PASSWORD`, `OBD_KEY_ALIAS` and `OBD_KEY_PASSWORD`. For local release builds, put the same values in an untracked `keystore.properties` file (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`). Never commit the keystore.

**Code layout** (under `app/src/main/java/com/obdreader/app/`):

| Folder | What's in it |
|---|---|
| `obd/` | Adapter connections (Bluetooth, BLE, Wi-Fi, demo), the ELM327 client and parser, PIDs, protocols, trouble-code explanations, adapter-log translations, reconnect timing, the car catalogue and VIN decoder, plus the logic for trips, alerts, timers, units and car coverage |
| `session/` | The shared connection used by the phone and the car, the background service, alerts, graph history, drive recording, the video overlay encoder and exports |
| `ui/` | Styles and the custom views for each dashboard layout |
| `car/` | The Android Auto screens |
| `MainActivity.kt` | The Dashboard, Graphs and Diagnostics tabs, permissions and pickers |

## Licences
The app uses the [Archivo](https://fonts.google.com/specimen/Archivo) typeface under the SIL Open Font License ([`licenses/Archivo-OFL.txt`](licenses/Archivo-OFL.txt)).

Generic trouble code names follow SAE J2012. They were cross-checked against the open lists in [python-OBD](https://github.com/brendan-w/python-OBD), [AndrOBD](https://github.com/fr3ts0n/AndrOBD) and the [Wal33D DTC database](https://github.com/Wal33D/dtc-database). Ford-specific definitions come from Ford's published *OBD System Operation Summary for Gasoline Engines* documents.
