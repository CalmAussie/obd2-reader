# OBD2 Reader (Android)

A native Kotlin Android app for ELM327-compatible OBD2 adapters. It works with **Bluetooth Classic**, **Bluetooth LE** and **Wi-Fi** adapters.

## Features
- **Live dashboard:** RPM, speed, throttle, engine load, coolant, intake air, manifold pressure, MAF, timing advance, fuel level, ambient and oil temperature, and battery voltage.
- **Smart polling:** asks the car which PIDs it supports and skips the rest. Fast values (RPM, speed) refresh every cycle; slow ones (temperatures) every 5th cycle.
- **Trouble codes:** reads stored (03) and pending (07) codes with plain-English descriptions.
- **Clear codes:** sends mode 04, with a confirmation dialog first.
- **VIN:** reads the VIN (mode 09 PID 02) on CAN and older protocols.
- **Demo mode:** simulates a car so you can try the app without an adapter.
- **Adapter log:** shows the raw traffic for debugging flaky clones.
- Works with Android 8.0+ (API 26), using Android 12+ Bluetooth permissions.

## Get the APK (no Android Studio needed)
1. Create a new GitHub repository and upload this folder to it.
2. Add the signing key secrets (see below) so every build is signed with the same key.
3. GitHub Actions builds the app on every change. Each build on `main` is published as a **Release** with `OBD2Reader.apk` attached, and it's also under **Actions → Build APK → Artifacts**.

### Sharing the APK
The APK is a normal, standalone Android app. Anyone with Android 8 or newer can install it from the file alone, with no GitHub account and nothing else to install.

- **Direct link:** `https://github.com/<you>/<repo>/releases/latest/download/OBD2Reader.apk` always gives the newest build. This needs a **public** repository; for a private one, download the APK yourself and send the file.
- **Installing:** people open the file and allow their browser or file app to "install unknown apps". If Play Protect warns about an unknown developer, they tap **More details → Install anyway**.
- **Updates:** a newer APK installs over the old one and keeps settings, because every build has a higher version number and is signed with the same key.
- **Help:** setup instructions, including Android Auto, are inside the app under **Diagnostics → Help**.

### Release signing key
Phones only accept an update if it's signed with the same key as the installed app. Store the key in four repository secrets (**Settings → Secrets and variables → Actions → New repository secret**):

| Secret | Value |
|---|---|
| `OBD_KEYSTORE_BASE64` | The keystore file, base64-encoded (`base64 -w0 release.jks`) |
| `OBD_KEYSTORE_PASSWORD` | Keystore password |
| `OBD_KEY_ALIAS` | Key alias |
| `OBD_KEY_PASSWORD` | Key password |

Without them, the build still works, but it's signed with a temporary key, so each update needs an uninstall first. The workflow shows a warning when that happens. Back up the keystore: if it's lost, everyone has to uninstall to move to a new key. Never commit the keystore to the repository (it's in `.gitignore`).

For local release builds, put the same values in a `keystore.properties` file in the project folder (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`); it's ignored by git.

## Build locally
Open the folder in Android Studio (Koala or newer, JDK 17) and press Run. Or, with Gradle 8.7 and the Android SDK installed:

```
gradle assembleRelease testDebugUnitTest
```

## Using it
Plug the adapter into the OBD2 port (under the dash, driver side), turn the ignition on, then tap **Connect adapter** and choose the type:

| Type | Typical adapters | Setup |
|---|---|---|
| Bluetooth Classic | Most cheap ELM327 clones, OBDLink LX, Veepeak Mini | Pair in Android Bluetooth settings first. The PIN is usually `1234` or `0000`. |
| Bluetooth LE | "iPhone compatible" adapters such as Vgate iCar Pro BLE, Veepeak BLE+ and OBDLink CX | No pairing needed. The app scans for 12 seconds. |
| Wi-Fi | ELM327 Wi-Fi and Vgate iCar Wi-Fi | Join the adapter's hotspot (e.g. `WiFi_OBDII`), then connect. The default address is `192.168.0.10:35000`. |

### Veepeak OBDCheck BLE+
This adapter supports both Bluetooth LE and Bluetooth Classic, and appears as **VEEPEAK**. Use **Bluetooth LE** in the app. It connects without pairing and is the faster link. If you'd rather pair it in Android settings, the PIN is `1234` and **Bluetooth Classic** works too. After the first connection, the connect panel offers a one-tap **Reconnect to VEEPEAK**.

For Wi-Fi, if Android says the network has no internet, choose to stay connected. The app routes its connection through Wi-Fi even when mobile data is on.

## Android Auto
The app has an Android Auto screen built with Google's Car App Library and its driving-safe templates:

- **Live data grid:** RPM, speed, coolant, throttle, fuel level and battery voltage, refreshed about once a second (Android Auto limits how often apps may redraw). Readings past their limit are marked HIGH.
- **Trouble codes:** read and listed with their meanings. Clearing codes stays on the phone, to be done parked with the engine off.
- **Connect from the car:** reconnect to the last adapter you used, or start demo mode. Pair and choose adapters on the phone first.

The phone and the car share one connection, which runs in a background service with a notification (live RPM, speed and coolant, plus a Disconnect button). It keeps working with the phone locked, and changing theme no longer disconnects.

**Installing for Android Auto.** Android Auto only lists apps from approved categories (navigation, points of interest, IoT, weather, media and messaging). This app declares the IoT category, the closest fit, but Google Play is likely to reject a car-diagnostics app in Android Auto. For your own use, sideload the APK and enable unknown sources in Android Auto:

1. Open the Android Auto settings on your phone and tap **Version** at the bottom ten times, then confirm to turn on developer mode.
2. In the three-dot menu, open **Developer settings** and turn on **Unknown sources**.
3. Connect to the car. OBD2 Reader appears in the Android Auto launcher.

The car screen uses Android Auto's own colours and layout, not the app's theme.

## Themes
Tap the palette button on the Dashboard to pick a look. The choice is saved.

| Theme | Look |
|---|---|
| Midnight (default) | Charcoal and cyan, soft rounded cards, gradient dials |
| Racing | Black with orange and yellow, sharp corners, bold condensed numbers, thick solid dials |
| Apex | Carbon black with volt yellow and cyan, cut-off corners on cards, buttons, fields and sheets, slanted bold numbers and titles, square-ended dials with a thin track |
| Daylight | Light background, white cards, blue accents, dark status-bar icons, for bright sun |
| Retro LCD | Phosphor green on black, square edges, monospace digits, segmented bar-graph dials |

Each theme sets the colours, card shape (rounded or cut corners), button corners, card outlines, number font and slant, and dial style (see `res/values/themes.xml`). Every screen reads these as theme tokens (`res/values/attrs.xml`), so adding a theme means adding one style and one entry in `ui/AppThemes.kt`. Changing theme while connected redraws the app, which disconnects; you can reconnect straight away.

## Car compatibility (since 2000)

The car picker covers 69 makes:
- **Japan:** Toyota, Lexus, Scion, Honda, Acura, Mazda, Nissan, Infiniti, Subaru, Mitsubishi, Suzuki, Isuzu, Daihatsu.
- **America:** Ford, Lincoln, Mercury, the GM brands (Chevrolet, GMC, Cadillac, Buick, Pontiac, Saturn, Oldsmobile, Hummer, plus Holden, Opel/Vauxhall and Saab), Jeep, Chrysler, Dodge, Ram, Plymouth, Tesla, Rivian, Lucid.
- **Europe:** BMW, Mini, Rolls-Royce, Mercedes-Benz, Smart, the Volkswagen Group (Volkswagen, Audi, Skoda, SEAT/Cupra, Porsche, Lamborghini, Bentley, Bugatti), Fiat, Abarth, Alfa Romeo, Maserati, Lancia, Peugeot, Citroën, DS, Volvo, Polestar, Jaguar, Land Rover, Renault, Dacia, Alpine, Aston Martin, Ferrari, McLaren, Lotus, Rover, MG.

On **Automatic**, the app first tries the protocol that worked last time, then the adapter's own search, then every OBD2 protocol in turn. Cheap adapters often miss older cars in their own search. You can also pick a protocol on the **Diagnostics** tab.

| Years (approx.) | Ford | Mazda | Honda |
|---|---|---|---|
| 2000–2007 | SAE J1850 PWM, some ISO | J1850 PWM (Ford-based models) or ISO 9141-2 / KWP | ISO 9141-2 / KWP |
| 2008 on | CAN | CAN | CAN |

| Years (approx.) | Toyota | BMW | Mercedes-Benz |
|---|---|---|---|
| 2000–2004 | ISO 9141-2 / KWP | ISO 9141-2 / KWP | ISO 9141-2 / KWP |
| 2005–2007 | Moving to CAN, some KWP | KWP (fast init), some CAN | KWP (fast init), moving to CAN |
| 2008 on | CAN | CAN | CAN |

| Years (approx.) | GM North America | Holden Commodore | Other Holden, Opel, Saab | VW Group |
|---|---|---|---|---|
| 2000–2003 | SAE J1850 VPW | VT: ALDL (not OBD2). VX/VY: partial OBD2 | ISO 9141-2 / KWP | ISO 9141-2 / KWP |
| 2004–2007 | J1850 VPW, moving to CAN | VZ: full OBD2. VE (2006): CAN | KWP, moving to CAN | Moving to CAN, some KWP |
| 2008 on | CAN | CAN | CAN | CAN |

Many cheap ELM327 clones can't speak J1850 VPW, which most pre-2008 GM, Chrysler, Dodge and Jeep vehicles need (and Oldsmobile and Plymouth). Lincoln and Mercury follow Ford, Lexus and Scion follow Toyota, and Acura follows Honda. Nissan, Infiniti, Subaru, Mitsubishi, Suzuki, Isuzu and Daihatsu use ISO 9141-2 / KWP until about 2004, move to CAN from 2005 and use CAN from 2008. Volvo moved to CAN from 2005. Jaguar, Land Rover, Renault, Dacia, Aston Martin, Ferrari, McLaren, Lotus, Rover and MG follow the European pattern. Fiat, Alfa Romeo, Lancia, Maserati, Peugeot, Citroën and DS follow the European pattern: ISO 9141-2 / KWP to about 2003, moving to CAN from 2004, CAN from 2008. Badge-engineered models get their own notes, for example the Pontiac G8 and GTO (Holden-built), the Pontiac Vibe (Toyota-based), the Saab 9-2X (Subaru-based), the Chrysler Crossfire (Mercedes-based), the Fiat and Abarth 124 Spider (Mazda-built), the Fiat Fullback and Peugeot 4007 / Citroën C-Crosser (Mitsubishi-based) and the Peugeot 107 / Citroën C1 (Toyota-built), the Infiniti Q30 / QX30 (Mercedes-based), the Isuzu Ascender and i-Series and 2007–2009 Suzuki XL-7 (GM-built), the Suzuki Equator and Mercury Villager (Nissan-based), the Scion iA (Mazda2-based) and the Smart ForFour (Mitsubishi- or Renault-based, depending on year).

**Electric cars** (Tesla, Rivian, Lucid, Polestar, and EVs such as the Nissan Leaf, Jaguar I-Pace and MG4) don't have to support standard OBD2 engine readings, so expect little or no live data. Teslas have no standard OBD2 port at all; the app says so when you pick one.

On BMW, Mini, Mercedes-Benz, Smart, VW Group, Volvo, Jaguar, Land Rover, Renault and the supercar makes, generic OBD2 covers engine data and emissions codes. Their own detailed diagnostics (other modules, service resets) need the maker's tools (for VW, Audi, Skoda and SEAT, tools such as VCDS). The Mercedes X-Class is Nissan Navara-based and uses Nissan electronics.

**If Automatic doesn't connect:** on the Diagnostics tab (or from the **Choose your car** button that appears after a failed connection), pick the manufacturer and year, then search the model. The app looks up that car's most likely protocol, tries it first with a longer wait, and still falls back to every other protocol. All 69 makes are listed, with about 670 models from 2000 on, and any other make can be chosen by year.

After the model, you can pick the **engine**. The list shows the engines that model had in your chosen year (for example, a 2003 Mazda Tribute offers 2.0 I4 and 3.0 V6, and a 2005 offers 2.3 I4 and 3.0 V6). The engine doesn't change which protocol is tried, because that's set by the car's computer. It scales the RPM dial to that engine's approximate redline, and diesels get a note that they usually report fewer readings. Engine lists cover the most common models (including the HiLux, LandCruiser, Prado, Camry, Corolla, RAV4, and the BMW 1, 3 and 5 Series, X3, X5, Z4 and M3, the Mercedes A, C, E, S and GLE/M-Class and Sprinter, the Holden Commodore family, Captiva, Colorado and Cruze, the Chevrolet Silverado, Camaro and Corvette, and the VW Golf, Polo, Passat, Tiguan, Touareg, Amarok, Audi A3, A4, Q5, Q7, TT, R8, Skoda Octavia Porsche 911, Boxster, Cayenne and Macan, and the Nissan Navara, Patrol, X-Trail and GT-R, Subaru WRX, Forester, Outback and Liberty, Mitsubishi Triton, Pajero, Outlander and Lancer Evolution, Isuzu D-Max and MU-X, Suzuki Swift and Jimny, Lexus IS and RX, Volvo XC90, Land Rover Defender and Discovery, Renault Clio and Mégane, and the Mini); for the rest, type the engine yourself.

After connecting, the app reads the VIN and shows the make (all 69 makes) and, for North American VINs, the model year.

**Things the app can't fix:**
- **Your adapter must support the protocol.** Many very cheap ELM327 clones lack SAE J1850 PWM, which most 2000s Fords need. Check the adapter's listing for "J1850 PWM" if you have an older Ford.
- **Cars sold in Australia before 2006** weren't required to have full OBD2. The AU and BA Falcon only partly support it (the BA shows live data but no trouble codes), and the BF onwards is fully compliant.
- Pre-2005 cars often don't report a VIN. Live data and codes still work.

## Limitations
- BLE adapters are detected by looking for a GATT service that has both a write and a notify characteristic. Common vendor UUIDs (FFF0, FFE0, 18F0, Vgate) are tried first. An exotic adapter may need its UUID added to `BleTransport.PREFERRED_SERVICES`.
- On Android 11 and below, BLE scanning needs Location turned on (an Android rule).
- Covers generic SAE PIDs only. Manufacturer-specific data (transmission temperature, DPF) isn't included.

## Code map
| File | Purpose |
|---|---|
| `obd/BluetoothTransport.kt` | Bluetooth Classic RFCOMM socket, with a fallback for clones |
| `obd/BleTransport.kt` | Bluetooth LE GATT serial emulation |
| `obd/WifiTransport.kt` | TCP socket bound to the Wi-Fi network |
| `obd/StreamTransport.kt` | Shared request/response loop for Classic and Wi-Fi |
| `obd/DemoTransport.kt` | Simulated adapter and car |
| `obd/ElmClient.kt` | AT setup, PID/DTC/VIN requests, one command at a time |
| `obd/ElmParser.kt` | Pure response parsing, unit-tested |
| `obd/Pids.kt` | PID formulas, so you can add gauges here |
| `obd/VehicleCatalog.kt` | Makes, models and their likely protocols, so you can add cars here |
| `obd/ObdProtocols.kt` | The nine OBD2 protocols and the order they're tried in |
| `obd/VinDecoder.kt` | Make and model year from a VIN |
| `MainActivity.kt` | Dashboard and Diagnostics tabs, permissions, pickers |
| `session/ObdSession.kt` | The shared connection, live readings, VIN and codes, used by the phone and the car |
| `session/ObdConnectionService.kt` | Background service and notification that keep the connection alive |
| `car/` | Android Auto: `ObdCarAppService`, `DashboardScreen` (live grid) and `CodesScreen` |
