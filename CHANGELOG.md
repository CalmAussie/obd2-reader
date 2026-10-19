# Changelog

Every change to OBD2 Reader, newest first. Each GitHub release shows the entry for its version.

## 1.1.0 (19 October 2026)

### New look
- A full redesign in a track-day livery style: livery stripes, a race-number roundel showing live RPM inside a segmented rev ring, angled decals for speed, coolant and fuel, and a timing-tower list for every other reading.
- Five start lights come on one by one while the app finds your car's protocol, then go out when it connects, and the rev ring sweeps like a car's self-test.
- Six liveries to choose from: Asphalt, Endurance, Kerb, Pit lane (a light style for sunlight), Drift and Apex.
- Two more layouts:
  - **Race telemetry:** shift lights, big numbers and a live 30-second trace for every reading.
  - **Digital cockpit:** one big speed in a glowing ring, on layered glass.
- Diagnostics: a check-engine banner that turns amber when codes are stored, trouble codes on bold plates, and the VIN on a decal.
- A "Check engine" tag appears on the dashboard roundel when codes are stored.
- New typeface throughout: Archivo.

### Android Auto
- The car screen picks up your style's colour for its buttons and icons.
- Engine speed shows a small rev roundel, and trouble codes show on the same plates as the phone.

### Changed
- Styles from 1.0 move to the closest new one: Midnight and Racing become Asphalt, Daylight becomes Pit lane, and Retro LCD becomes Drift.
- Releases are now named by app version, for example "OBD2 Reader 1.1.0 (build 7)".

## 1.0.5 (19 October 2026)
- Releases are signed with a permanent key, so every later version installs over the one before and keeps your settings. If you installed 1.0.4, uninstall it once first.

## 1.0.4 (19 October 2026)
First release.
- Live data and trouble codes from ELM327 adapters over Bluetooth Classic, Bluetooth LE (including the Veepeak OBDCheck BLE+) and Wi-Fi.
- Automatic protocol search, with a protocol picker and a "Choose your car" search by make, year, model and engine for cars that won't connect on Automatic.
- 69 makes from 2000 on, with VIN decoding.
- Android Auto screens for live data and trouble codes.
- In-app help, including Android Auto setup.
