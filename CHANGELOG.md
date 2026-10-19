# Changelog

Every change to OBD2 Reader, newest first. Each GitHub release shows the entry for its version.

## 1.3.1 (20 October 2026)

### Android Auto
- The car screen is now smooth too. It refreshes about five times a second (Android Auto limits how often a car screen may redraw), and each number glides between refreshes instead of jumping once a second.
- Live numbers are now drawn big in the livery's display font, with a bar under each reading and the engine speed inside the rev roundel.
- Fixed: every update of the car screen used to count as a brand-new screen. Android Auto allows only five of those before it closes an app with an error, so the car screen could shut down after a few seconds of live data. It now keeps one screen for every state (not connected, connecting, live and ignition off) and only changes the numbers, which Android Auto treats as a free refresh. Connection problems show as a pop-up message instead.

## 1.3.0 (20 October 2026)

### Smoother Dashboard
- Every figure on the Dashboard now glides to each new reading instead of jumping: the rev roundel and its number, the speed, coolant and fuel decals, every row in the live-data list, the race-telemetry numbers and the digital cockpit's ring and glass panels. Digits roll and bars and rings sweep, frame by frame, like the Graphs tab.
- The Dashboard's moving readings (engine speed, road speed, throttle, engine load, manifold pressure and airflow) are now asked for together in one request every cycle while the Dashboard is open, so they update as often as the graphs do. Slower readings like coolant and fuel still update every second or so.
- The 30-second traces in Race telemetry still span 30 seconds, however fast readings arrive.
- Off screen, nothing animates, so this doesn't cost battery on other tabs or with the screen off.

## 1.2.9 (20 October 2026)
- More efficiency while connected: buttons and status text no longer redraw the whole screen on every reading when nothing about them has changed, and the Graphs reading rate only updates while Graphs is open. The app now sits idle between readings on every tab, which saves battery and keeps it responsive on older phones.

## 1.2.8 (20 October 2026)
- Smoother and lighter while connected: live readings only redraw the Dashboard while it's on screen. Before, every reading also relaid out the whole app behind the Graphs and Diagnostics tabs. The Dashboard catches up the moment you go back to it.
- The install test now clears the emulator's own "isn't responding" pop-ups before it starts.

## 1.2.7 (20 October 2026)
- Less battery use while connected: the rev roundel no longer animates while you're on the Graphs or Diagnostics tab. It was keeping the phone busy redrawing a dial nobody could see, which also made the app slow to respond to accessibility tools on Android 8 to 10.
- The install test now checks the Disconnect button and taps the tabs reliably while live data is moving.

## 1.2.6 (20 October 2026)

Found by the new install test, which runs the published APK on real Android system images.

### Fixed
- **The Connect button was missing on Android 9 and newer.** The Demo car button took the whole row, so the only way to connect was through Android Auto or the notification. Connect and Disconnect now show beside Demo car on every Android version.
- Page titles, the trouble-code banner, the VIN and the big telemetry numbers showed in the plain body font instead of the style's heading font (wide italic, condensed or light).
- The Graphs chip for engine load read "load". It now reads "Engine load".

### Changed
- The install test also fails if the Connect, Demo car or Disconnect button is missing or too small to tap.

## 1.2.5 (20 October 2026)
- The APK is now signed with every Android signature scheme (v1, v2 and v3), so any browser, file manager or installer app accepts it.
- Every release is now tested automatically: the published APK is downloaded, installed on fresh Android 8, 10, 13 and 15 emulators, opened, run with the demo car and checked for crashes.
- The README has a QR code that downloads the APK straight to your phone, and clearer install steps. All you need is the APK.

## 1.2.4 (19 October 2026)

### Changed
- OBD2 Reader is now read-only. The **Clear codes** button is gone, and the app can no longer clear trouble codes, turn off the check-engine light or reset emissions readiness.
- Every command is checked before it reaches the adapter. Only the adapter's own settings and OBD2 read requests (live data, freeze frame, stored, pending and permanent codes, test results and vehicle info) get through. Anything that could change the car is blocked and noted in the adapter log.
- The Help section explains that the app is read-only, and that a workshop scan tool can clear codes after a repair.

## 1.2.3 (19 October 2026)

Fixes from a full QA pass (Android Lint, compiler warnings, unit tests and a code review).

### Fixed
- After disconnecting, the dashboard kept showing the last readings as if they were live. The gauges now clear; trouble codes and the VIN stay until the next connection.
- Tapping Connect straight after Disconnect could let the old connection's clean-up overwrite the new one, showing "Not connected" while connected.
- Disconnect now stops a Bluetooth Classic or Wi-Fi connection that's still being set up, instead of waiting for it to time out.
- A failed Bluetooth Classic attempt is closed before the fallback is tried, which some phones need before they'll open a second link.
- With the adapter log open, every reading redrew the whole log, which could make the app stutter. It now refreshes four times a second, and only while it's open.
- Two drives recorded in the same minute overwrote each other. Saved drives also have readable names now, such as "19 Oct 2026, 10:37 pm".
- A drive that's still recording can no longer be deleted.
- The Record drive button stayed enabled after disconnecting.
- On Android Auto, a status change that arrived just after a refresh (such as "Ignition off") could be left undrawn.
- The VIN could be cut off on narrow phones or with large text. It now shrinks to fit.
- If Android refuses the background connection service, the app no longer risks closing.

### Changed
- When the app reconnects by itself on opening and can't find the car, it stops after 3 minutes instead of 30, so opening it at home doesn't leave it searching. Once it has reached the car, it still keeps trying for 30 minutes after the ignition goes off.
- Graphs use less battery: smoother drawing with no per-frame memory churn, and an idle Graphs tab no longer redraws 60 times a second.
- The Graphs tab now has the livery stripes like the other tabs.
- VINs from Hyundai, Kia, Genesis, SsangYong, BYD and GWM are now recognised.
- Android 13+ themed (monochrome) app icon, and the location permission request now meets Android's rules for older phones.

## 1.2.2 (19 October 2026)
- The adapter log is now readable: every exchange with the adapter gets a plain-English line underneath. For example, "> 010C  < 410C319E" now reads "Engine RPM: 3,176 rpm", and multi-reading replies list each reading.
- Setup steps, the protocol found, which readings the car supports, the VIN and errors such as NO DATA or UNABLE TO CONNECT are all explained.
- Each request and its reply now sit on one line, and the explanations stand out from the raw traffic.

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
