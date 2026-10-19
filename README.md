# OBD2 Reader

An Android app that reads live engine data and trouble codes from a cheap ELM327 OBD2 adapter, over Bluetooth, Bluetooth LE or Wi-Fi. It also has an Android Auto screen.

**[Download the latest APK](https://github.com/CalmAussie/obd2-reader/releases/latest/download/OBD2Reader.apk)** · [All releases](https://github.com/CalmAussie/obd2-reader/releases) · [Changelog](CHANGELOG.md)

## Install
1. On your Android phone (Android 8 or newer), open the APK. If asked, allow your browser or file app to **install unknown apps**.
2. If Google Play Protect warns about an unknown developer, tap **More details → Install anyway**. Every app from outside the Play Store gets this warning.
3. To update, install a newer APK over the old one. Your settings are kept.

## Use it
1. Plug the adapter into the OBD2 port (under the dashboard, driver's side) and turn the ignition on.
2. Open the app, tap **Connect** and choose how your adapter connects:
   - **Bluetooth Classic:** most ELM327 adapters. Pair it in Android's Bluetooth settings first (PIN `1234` or `0000`).
   - **Bluetooth LE:** iPhone-compatible adapters such as the Veepeak OBDCheck BLE+. No pairing needed.
   - **Wi-Fi:** join the adapter's Wi-Fi network first. The usual address is `192.168.0.10`, port `35000`.
3. No adapter? Tap **Demo car** to try the app with a simulated car.

Help, including Android Auto setup, is also inside the app under **Diagnostics → Help**.

## What it does
- **Live data:** RPM, speed, coolant, fuel, throttle, engine load, intake air, manifold pressure, MAF, timing advance, ambient and oil temperature, and battery voltage. Readings past their limit turn red.
- **Trouble codes:** reads stored and pending codes with plain-English descriptions, and clears them after a confirmation.
- **Vehicle ID:** reads the VIN and shows the make and model year.
- **Finds the right protocol:** tries every OBD2 protocol automatically. If your car won't connect, choose it under **Diagnostics → Your car** (69 makes from 2000 on) so the app tries that car's usual protocol first.
- **Background connection:** keeps working with the screen off, with a notification showing live RPM, speed and coolant.

## Styles
Tap the palette icon on the Dashboard to choose a style. Your choice is saved.

| Style | Look |
|---|---|
| Asphalt (default) | Orange and white livery stripes on dark grey |
| Endurance | Night-race navy and yellow |
| Kerb | Red and white kerb stripes |
| Pit lane | A light style in black and red, for sunlight |
| Drift | Hot pink and teal |
| Apex | Volt yellow on carbon black, sharply raked |
| Race telemetry | A data-logger screen: shift lights, big numbers and live 30-second traces |
| Digital cockpit | One big speed in a glowing ring, on layered glass |

## Android Auto
The car screen shows live RPM, speed, coolant, throttle, fuel and battery, and lists trouble codes. It uses your style's colours, but its background, fonts and layout are always Android Auto's own. Clearing codes is only possible on the phone, parked with the engine off.

Android Auto only shows Play Store apps until you allow others. On your phone:
1. Open Android Auto's settings (search "Android Auto" in Settings).
2. Scroll to the bottom and tap **Version** about 10 times, then confirm to turn on developer mode.
3. Tap the three-dot menu, then **Developer settings**, and turn on **Unknown sources**.
4. Reconnect to your car. OBD2 Reader appears in Android Auto's app list.

## Good to know
- **Older cars:** cars sold new in Australia before 2006 weren't required to have full OBD2, so some readings or codes may be missing.
- **Cheap adapters:** many low-cost ELM327 clones can't speak SAE J1850 PWM or VPW, which most 2000s Fords and pre-2008 GM, Chrysler, Dodge and Jeep vehicles need.
- **Electric cars** don't have to support standard OBD2 engine readings. Teslas have no standard OBD2 port.
- **Generic OBD2 only:** the app reads engine data and emissions codes. Maker-specific systems (ABS, airbags, transmission data, service resets) need the maker's own tools.
- **Bluetooth LE on Android 11 and older** needs Location turned on while scanning (an Android rule).

## For developers

**Building:** every push to `main` runs [`.github/workflows/build.yml`](.github/workflows/build.yml). It runs the unit tests, builds a signed APK and publishes it as a GitHub release. To build locally, open the project in Android Studio (JDK 17) or run `gradle assembleRelease testDebugUnitTest` with Gradle 8.7 and the Android SDK.

**Releasing:** bump `obdVersion` in `gradle.properties` and add an entry at the top of [`CHANGELOG.md`](CHANGELOG.md). The release notes are taken from that entry.

**Signing:** updates only install over an older version when both are signed with the same key. The key lives in four repository secrets: `OBD_KEYSTORE_BASE64`, `OBD_KEYSTORE_PASSWORD`, `OBD_KEY_ALIAS` and `OBD_KEY_PASSWORD`. For local release builds, put the same values in an untracked `keystore.properties` file (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`). Never commit the keystore.

**Code layout** (under `app/src/main/java/com/obdreader/app/`):

| Folder | What's in it |
|---|---|
| `obd/` | Adapter connections (Bluetooth, BLE, Wi-Fi, demo), the ELM327 client and parser, PIDs, protocols, the car catalogue and VIN decoder |
| `session/` | The shared connection used by the phone and the car, and the background service |
| `ui/` | Styles and the custom views for each dashboard layout |
| `car/` | The Android Auto screens |
| `MainActivity.kt` | The Dashboard and Diagnostics tabs, permissions and pickers |

## Licences
The app uses the [Archivo](https://fonts.google.com/specimen/Archivo) typeface under the SIL Open Font License ([`licenses/Archivo-OFL.txt`](licenses/Archivo-OFL.txt)).
