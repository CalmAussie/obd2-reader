# Changelog

Every change to OBD2 Reader, newest first. Each GitHub release shows the entry for its version.

## 1.2.1 (19 October 2026)
- The adapter log now shows each trouble code with its definition and urgency right under the car's raw reply, for example "P0420: Catalytic converter efficiency below threshold (bank 1). Check when convenient."
- The latest code read stays pinned at the top of the log, so live data doesn't push it out of view, and it's included when you share the log.

## 1.2.0 (19 October 2026)

### Trouble codes explained
- Each trouble code now opens into a full explanation: what it means, how urgent it is (Stop soon, Check soon or Check when convenient), what you might notice, common causes and what to check, in order.
- Detailed explanations for about 200 of the most common generic codes, including misfires, oxygen sensors, fuel trim, catalytic converter, EVAP, EGR, timing, sensors, transmission and network faults. Any other code still gets a description of the system it belongs to.
- **Share code report:** sends a text report of every code with its explanation, plus the car's VIN and make, to your mechanic, email or a forum.

### Automatic reconnect
- The app reconnects to your adapter by itself when you open it, when Android Auto starts, and when you turn the ignition back on. Most cars are back on live data within a second or two.
- Turning the ignition off no longer drops the connection: the app shows "Ignition off" and picks up again as soon as the car answers.
- It keeps trying for 30 minutes, then stops so it doesn't drain your battery. Turn it off under **Diagnostics → Reconnect automatically**.

### Graphs and drive logs
- A new **Graphs** tab with live, smooth-scrolling charts for up to four readings at once, showing the last 10, 30 or 60 seconds, with minimum and maximum. Pause to look at a moment closely.
- Faster live data: on most cars from 2008 on, the app asks for up to six readings at once and polls the readings you're graphing first, for several times more readings a second.
- **Record drive:** saves every reading five times a second to a CSV file for Excel, Google Sheets or a data-logging tool. Open **Saved drives** to share or delete them.
- The adapter log can now be shared as a text file for troubleshooting.

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
