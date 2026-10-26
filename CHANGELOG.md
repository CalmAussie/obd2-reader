# Changelog

Every change to OBD2 Reader, newest first. Each GitHub release shows the entry for its version.

## 1.15.0 (26 October 2026)
- **Battery readings with the ignition off.** When the car is switched off but the adapter is still connected, the app now shows the battery's voltage and roughly how charged it is (for example "12.5 V, about 80% charged"), on the Dashboard and in the battery reading. It comes from the adapter's own reading at the OBD2 port, so it doesn't need to wake the car.
- **Overnight battery watch.** Turn on **Diagnostics → Battery check → Watch the battery while the car is off**, leave the phone connected overnight (on charge, in range of the adapter), and the app notes the battery's voltage every 5 minutes. In the morning it says whether the battery held its charge or dropped faster than it should, which points to something draining it while the car is off. With it on, the app waits up to 24 hours for the car instead of 30 minutes.
- **Lets the car sleep.** While the ignition is off, the app now asks the car whether it's awake every 5 seconds instead of every 1.5, so it doesn't keep the car's computers from going to sleep. It still notices straight away when you turn the key, because the battery voltage changes as the car switches on.

## 1.14.0 (26 October 2026)
- **Drive history.** Every drive is now kept, not just the last one. **Drive history** (on the Dashboard, under the driving score) lists each drive with its date, distance, time and drive score, shows how your drive score has gone over your last 20 drives, and adds up the distance and time. Tap a drive for its summary and to share its card; tap and hold to delete it. Only the summary is kept (times, distances and scores), never your location.
- **Freeze frame.** When a trouble code is stored, the car keeps a snapshot of the engine at that moment. The app now reads it and shows it on that code's card, under **When it was set (freeze frame)**: engine speed, road speed, coolant, load, fuel trims, manifold pressure, intake air, throttle, airflow and how the fuel system was running, as far as your car records them. It's in the code report too, for your mechanic.
- **Battery check at start-up.** Connect with the ignition on and the engine off, then start the engine: the app measures the battery's resting voltage and how far it dips while the starter turns the engine, and rates it (a healthy battery stays above about 10 V). **Diagnostics → Battery check** shows the latest result and the ones before, so a battery that's getting weaker shows up before it lets you down. The demo car now starts the same way: a few seconds with the engine off, then a start (its checks are shown but not kept).
- **Route mini-map on the video overlay.** Overlay videos of drives recorded with their route now show the route's outline above the 30-second trace, shaded by speed like the drive card, with a dot that moves along it as the video plays.

## 1.13.9 (26 October 2026)
- **Put the graphs in your order.** On **Graphs**, tap and hold a graph and drag it up or down, or use **Change graph order** to move each one with ▲ and ▼. The order is kept, and a reading you turn on goes to the bottom.
- **Speed on the route outline.** The outline on drive cards is now shaded by how fast you drove each part: brighter where you were faster, dimmer where you were slower, all in the card's own colour and never fading to black.

## 1.13.8 (23 October 2026)
- Fixed: a drive that ended by switching the car off, without tapping **Disconnect**, never ended in the app. Many cars keep the OBD2 port powered with the ignition off, so the app just waited for the car to start again: the trip summary didn't come up, and **Last drive summary** still showed the drive before, so its drive card had no route outline from the drive you'd just recorded. A drive now ends once the ignition has been off for 3 minutes (a shorter stop carries on as the same drive), and its summary comes up.
- **Fewer gaps in the route.** On Android 12 and newer, recordings use the phone's fused location, which adds the phone's motion sensors to GPS, so short dropouts are bridged. Positions still come only from your phone's own location service and are only saved in the drive you record.
- **Route outline at the start and end of a drive without GPS.** When a drive starts or ends with no GPS fix (an underground car park, or a tunnel at the very end), the outline on the drive card is carried on by how far the car's speed readings say it went, in the direction it was heading. In the middle of a drive, a tunnel is still drawn as a straight line between where GPS was lost and found again: without maps, the app can't know the road's curves. The drive's CSV file is unchanged: rows without a fix stay blank.
- Fixed: a drive that ended while the app was in the background or closed could be lost. It's now kept on the phone the moment it ends, and its summary comes up the next time you open the app.

## 1.13.7 (23 October 2026)
- **Enter your VIN from the plate.** Some cars send a VIN that isn't quite theirs: the VIN stored in the engine computer can have a typo from when it was programmed, or belong to another car if the computer was replaced. **Diagnostics → Not your VIN? Enter it** lets you type the VIN from your VIN plate. It's kept on this phone only and used for the make and year and in reports instead. When the car's VIN is different, Diagnostics shows what the engine computer reports underneath, and reports list both, so a mechanic sees both too. Choosing a different car stops using the VIN you entered.
- Fixed: VINs from cars built outside North America, including many built in China for other markets, were wrongly called "may be misread" because their check digit didn't add up. Only North American VINs must use a check digit, so only those are checked now.

## 1.13.6 (23 October 2026)
- Fixed: after disconnecting and closing the app from Recents, the connection notification could stay behind, and tapping its **Disconnect** button didn't take it away. The notification now goes as soon as there's nothing connected (and no route being recorded), and **Disconnect** on a leftover notification clears it.

## 1.13.5 (23 October 2026)
- **Swipe between tabs.** Swipe sideways across the screen to move to the next tab (right to left) or back to the previous one (left to right): Dashboard, Graphs, Diagnostics. Taps, scrolling up and down, and short sideways nudges work as before.

## 1.13.4 (22 October 2026)
- **The phone doesn't lock while the app is open,** whether or not you're connected or recording. Minimise it and your phone locks as normal.
- **Minimised, it keeps going.** With the app in the background (Home, or Back from the Dashboard), the connection stays up and a drive you're recording keeps recording, as before. Back from the Dashboard now minimises the app on every Android version, instead of closing it on Android 10 and 11.
- **Record drive is on the Dashboard now,** under the Connect button, and only appears once you're connected to the car (it stays while a drive is recording, so you can stop it). Recorded drives are still under **Graphs → Saved drives**, where you can share them or make a video overlay even when you're not connected.
- **Closed, it stops.** Swipe the app away from Recents and it finishes and saves any recording, disconnects from the car and stops running in the background. If Android Auto is showing the app on the car screen, the connection carries on for the car screen.
- **The clock matches the app name.** It's now in the same font, size and colour as "OBD2 Reader" and sits on the same line, so it changes with your style.
- **Save or share the code report and adapter log.** **Save or share code report** and the adapter log's **Save or share** now ask whether to save the file to your phone (Downloads › OBD2 Reader) or share it as before. On Android 9 and older you pick the folder.
- Fixed: the VIN could come out wrong. When two of the car's modules answered at once, or an older car sent the VIN's parts out of order, the parts were joined together and showed as a different car's VIN. The app now keeps each module's answer separate, puts the parts back in order, and checks the VIN's check digit. If a VIN still fails the check, Diagnostics says it may be misread and the make and year aren't taken from it.

## 1.13.3 (22 October 2026)
- **A clock on the Dashboard,** just left of the style button. It follows your phone's 12 or 24-hour setting.
- Fixed: the protocol was worded differently on the Dashboard ("CAN 11-bit, 500 kbaud") and the Diagnostics tab ("ISO 15765-4 CAN (11-bit, 500 kbaud)"), so the same protocol looked like two different ones. Both now use the full standard name. The **Protocol** setting on Diagnostics also says which protocol Automatic found while you're connected, for example "Automatic · ISO 15765-4 CAN (11-bit, 500 kbaud)", instead of just "Automatic".

## 1.13.2 (22 October 2026)
- Fixed: tapping **Update** downloaded the new version, but Android's **Install** prompt sometimes never appeared, so nothing was installed. The app now shows the prompt from its own screen; if you've switched away, a notification appears, and tapping it opens the prompt.
- If an update can't install, the app now says why in a message that stays on screen, with Android's own reason (for example, Google Play Protect blocking it, or a download that didn't finish) and a button to get the new version from the release page instead. Before, it only showed a brief message that was easy to miss.
- **Samsung phones:** Samsung's Auto Blocker stops apps installing their own updates, usually without any message. The update offer now says to turn Auto Blocker off first (Settings › Security and privacy › Auto Blocker), and if the phone doesn't respond after the download, the app says so and points to Auto Blocker.
- The download's progress bar now moves smoothly to the end instead of stalling partway, and the download is faster.
- After an update installs, Android closes the app to replace it. A notification now says it's updated, and tapping it opens the app again.

## 1.13.1 (22 October 2026)
- **Your location, spelled out.** A new **Help → Your location** topic, the **Save the route too?** question and the README now say plainly that your location is never sent to the developer or anyone else: it's only stored on your phone, in the CSV file of a drive you record with its route, and only used to draw that route's outline on your share card.
- **Share preview in the same window.** **Share as a picture** now shows the card straight away, with **Story (9:16)** and **Square (1:1)** buttons to switch between the two shapes, and **Share** and **Cancel** underneath. No second window.
- **What's new since your version.** **Diagnostics → App updates** now has a **Check for updates** button. Tap the rest of the row to see the changelog for just the versions released after the one you have, with a button to get the newest. If you're up to date, it shows what's in your version.
- **Tap and hold to see the card big.** Hold the preview and it grows smoothly out of its place to fill the screen; let go and it shrinks back.
- The post's text is now just your score and #OBD2Reader, for example "My drive: 67/100 drive score. #OBD2Reader".
- Fixed: checking for updates could fail on a busy shared network (work, a café or a hotspot), because GitHub limits how often one internet address can ask. The app now falls back to GitHub's release page when that happens.
- Fixed: on the Digital cockpit style, the **Connect** button stayed dark and faded after you disconnected (it looked stuck pressed) until the app was fully closed and reopened. It now goes straight back to its bright blue-to-teal look.
- Fixed: on the Digital cockpit style, the small level bars under the readouts were so faint they were nearly invisible. They now show at full strength, as does the Digital cockpit preview in the Style picker.

## 1.13.0 (22 October 2026)
- **Updates from inside the app.** Once a day the app checks GitHub for a newer release, and **Diagnostics → App updates** checks straight away. When there's a new version, it shows what's new with **Update** and **Later**. **Update** downloads it with a progress bar and hands it to Android, which asks you to tap **Install**. On Android 12 and newer, later updates may install without asking once the app has installed its own update. Your settings, drives and recordings are kept.
- The first time, Android asks you to allow OBD2 Reader to install apps; the app takes you to that switch.
- The daily check never interrupts while you're connected to the car, and **Later** stops it asking again about that version. Only the version check and the download go to GitHub.

## 1.12.0 (22 October 2026)
- **Preview before you share a drive.** After you pick Story or Square, the card is shown as it'll look, with a **Share** button and **Cancel**. Nothing is saved or sent until you tap **Share**.
- **One score on each drive card.** Shared from **Race telemetry**, the card shows only your Racing score. From any other layout, it shows your **drive score**: the average of your Everyday and Efficiency scores, with those two shown underneath on the Story card. If a drive was too short for one of them, the drive score uses the other.
- **Score and route side by side.** On both the Story and Square cards, the score and the route's outline now sit on one row at the same height. On the Square card the stats move underneath.

## 1.11.1 (22 October 2026)
- **Code support table.** [docs/CODE-SUPPORT.md](docs/CODE-SUPPORT.md) lists every make and model in the car list, whether the generic codes apply, and how many manufacturer-specific codes are explained for each model year. It's built from the app's own car list and code library, and a test fails if it falls out of date.

## 1.11.0 (22 October 2026)
- **The full standard code list.** About 2,860 generic trouble codes are now explained, up from about 370: nearly every standard P0, P2 and U0 code, the P34 cylinder-deactivation codes and the U3 codes. Each has its standard name and an explanation for its kind of part, with how urgent it is, common causes and what to check. New kinds include hybrid and electric drive systems (with a warning about orange high-voltage parts), cylinder deactivation and variable valve lift, glow plugs, exhaust temperature and pressure, coolant pumps and level, engine oil level and condition, cruise control, power steering, and transmission switches and actuators.
- Every name was checked against more than one copy of the standard list, and a code was only added when they agreed on its name, letters, numbers and bank. The few they disagree on are left out and still say which system they belong to.
- **Ford's own definitions for some Ford and Lincoln codes.** Where Ford's published OBD documents define a Ford-specific code and say which model years it covers, the app uses Ford's definition for those years: P1336, P1450, P144A, P144C, P164A, P164B, P1646 and P1647, on 2018 to 2025 petrol models depending on the code. Choose your car (or connect so the VIN can be read) for these to show. On other years and other makes, these codes still say where to look them up, because they can mean something different there.

## 1.10.0 (22 October 2026)
- **Drive cards in your layout's style.** The shareable picture now looks like the Dashboard layout you share it from: livery stripes and a score ring on the liveries; shift lights lit by your score, a data-logger readout and channel-coloured panels on Race telemetry; a soft blue glow, a fine glowing ring and glass panels on the Digital cockpit; neon haze, scanlines, a segmented neon ring and split-colour digits on Cyberpunk.
- **Your route in recorded drives.** When you tap **Record drive**, the app asks whether to save the route too. If you say yes, each row of the drive's CSV gets **Latitude**, **Longitude** and **Location accuracy (m)** columns, so mapping software (Google My Maps, QGIS, Excel's map charts and the like) can draw where you were for every reading. The route keeps recording with the screen off. Rows with no recent GPS fix are left blank rather than repeating an old position.
- **Route outline on drive cards.** A drive that was recorded with its route gets its outline on the shareable picture: a line on the card's own background, with a ring where you started and a dot where you finished. No map, street names or coordinates are drawn.
- **Location stays in the recording.** Your location is only used while a drive is recording with the route, is written only into that drive's file on your phone, and is never kept anywhere else (not in trip summaries, alerts, settings or backups). Delete the drive and the route goes with it. Shared drive cards carry only the outline's shape.

## 1.9.2 (22 October 2026)
- **ThinkCar ThinkDiag and other maker-locked adapters.** ThinkDiag adapters are made for ThinkCar's own app. If one answers like a standard ELM327, it connects like any other adapter. If it doesn't, the app now says so plainly (it's a ThinkDiag, made for ThinkCar's app, and any ELM327-compatible adapter works), instead of "Couldn't connect" and retrying. BlueDriver adapters get the same message. A car with the ignition off is never blamed on the adapter: the message only shows when the adapter itself never answered like an ELM327.

## 1.9.1 (21 October 2026)
- **Engines for every model in the car list.** Choose your car now lists engines for all 640 petrol, diesel and hybrid models in the catalogue (up from 121), from 2000 to 2026: each with the years it was offered, its approximate redline for the rev dial, and whether it's diesel or hybrid (which changes the "may not work on your car" list). Models that already had engines gained ones that were missing, such as the 2000–2004 HiLux, the 2020-on Accord and S-Class, and the Cayenne Turbo models. Engines vary by country, so if yours isn't listed you can still type it.
- Fully electric models (Tesla, Leaf, Polestar 2 and others) say "Electric: there's no engine to choose" instead of asking you to type one.
- **Throttle on the Race telemetry trace.** The 30-second trace now shows throttle (green, 0–100%) along with engine speed and road speed, with a key under it. If a car doesn't report throttle, the line has a gap rather than dropping to zero. Standard OBD2 has no brake reading, so brake isn't shown.
- Fixed: in **Choose your car** (and **Diagnostics → Your car**), the **Engine** box worked like a plain text box. The list of engines only opened on a second tap, and only showed engines matching what was already typed. It's now a dropdown with an arrow, like Manufacturer and Year: one tap lists every engine offered for that model and year. You can still type an engine that isn't listed.

## 1.9.0 (21 October 2026)
- **Battery charging status.** Beside the battery voltage, the app now says whether the battery looks like it's charging: charging, charging weakly, not charging or overcharging with the engine running, and engine off (or engine off, low) when it isn't. It's on every style's battery readout, the live-data list and the Android Auto battery tile.
- It's an estimate, marked **(est.)**: standard OBD2 has no battery-current (amps) reading, so it's worked out from the voltage and engine speed, with the same limits as the battery alerts. It waits a few seconds before changing (20 seconds for "not charging"), so a moment's dip doesn't flip it. Cars with smart charging lower the voltage while cruising, which shows as "charging weakly". **Diagnostics → Help → Battery status** explains the limits.
- **Scan what your car reports** has a new **Battery** section: battery voltage (every car), whether the car offers the standard hybrid or electric battery data (shown raw, so it can be decoded for your car), and the hybrid battery's remaining life where reported. A 12-volt battery-current reading isn't part of standard OBD2, and the scan says so.

## 1.8.1 (21 October 2026)
- **More trouble codes explained.** About 370 generic trouble codes now have their standard name and a full explanation, up from about 150: what the code means, how urgent it is, common causes, what you might notice and what to check first. The new ones cover sensor faults (circuit low, high, range and intermittent), injectors, ignition coils and misfires for up to 12 cylinders, oxygen sensors and their heaters, fuel trim and pressure, EGR, EVAP, the electronic throttle, the transmission's sensors, gears and solenoids, diesel particulate filters, and lost communication between the car's computers.
- New help topic, **What codes it reads**: the app reads codes from the engine computer (and, on many cars, the automatic transmission), not from systems such as ABS, airbags or body electrics. Codes outside its list are named by system, and manufacturer-specific codes say where to look them up for your make.
- The README no longer says every generic code is explained in full.

## 1.8.0 (21 October 2026)
- **Trip summary.** When a drive ends (you disconnect or turn the ignition off), a summary comes up: distance, time driving and idling, average and top speed, best 0–100 km/h and the score in all three styles. **Last drive summary**, under the driving score, opens it again later.
- **Driving alerts.** A sound, vibration and notification (also shown on the Android Auto screen) when the engine runs hot, the oil gets too hot, the battery isn't charging or is overcharging, the battery is low with the engine off, or a new trouble code appears. Trouble codes are now checked every two minutes while connected. Turn alerts off under **Diagnostics → Alerts**.
- **Video overlay.** Make an MP4 of a recorded drive, with the shift lights, rev arc, speed, top speed, throttle and a 30-second trace, to lay over your dash-cam or phone footage in a video editor. Choose 1080p or 720p, on a green-screen or black background your editor can remove. Saved to Movies › OBD2 Reader (Android 10 and newer) and ready to share.
- **Shareable drive cards.** Turn a drive into a picture in your style: a 9:16 card for Shorts, TikTok and Reels or a square one for Instagram, with the score, distance, time, top and average speed. Saved to Pictures and ready to share.
- **Performance timers.** 0–100 km/h, 80–120 km/h and the quarter mile (0–60 and 50–70 mph in imperial), timed from the car's speed, with a start-light countdown, trap speed and your best times. The quarter mile is worked out from speed, so it's marked as an estimate.
- **Choose your readouts.** Pick which readings fill the Dashboard's main readouts (two on the liveries and Race telemetry, three on the Digital cockpit and Cyberpunk) under **Diagnostics → Dashboard readouts**.
- **Wider car coverage.** When a car leaves out a reading, the app uses a close standard one where there is one (accelerator pedal position for throttle, or the car's other coolant sensor) and labels it for what it is. **Diagnostics → Scan what your car reports** lists every standard reading your car reports and, for Ford-built cars, tries a few known Ford readings. Share the scan to help add readings for your car. Like everything else, the scan only reads.
- **Metric or imperial.** Choose km/h or mph and °C or °F separately under **Diagnostics → Units**. The Dashboard, Graphs, Android Auto, trip summaries, drive cards, video overlays and timers all follow it.
- **N/A instead of guesses.** A reading your car doesn't report, or stops answering, now says **N/A** everywhere it's shown, on the phone and in Android Auto, instead of a dash or the last number it had. One missed reply is common, so a reading only switches to N/A after three in a row.
- Everything above works the same for every car; nothing is tied to one make or model except the Ford readings tried in the car scan.

## 1.7.7 (20 October 2026)
- **Top speed this drive.** The highest speed since you connected now shows beside the speed in every style: in the corner of the speed panel on the liveries, under km/h on Race telemetry and the Digital cockpit, and beside KM/H on Cyberpunk. It's on the Android Auto speed tile and in the driving score card too.
- A speed only counts once two readings in a row reach it, so a single glitched reading can't set a top speed you never drove. It resets when you connect again; turning the ignition off and on mid-drive doesn't reset it.

## 1.7.6 (20 October 2026)
- Fixed: in **Choose your car**, the Engine box was squashed (thinner than the other boxes, with the text pressed against its edge). It's now the same size and style as Manufacturer, Year and Model.

## 1.7.5 (20 October 2026)
- Landscape keeps the app name at the top left in every style, with the livery stripes behind it across the full width of the screen. The dashboard sits underneath in two columns.
- Landscape on **Race telemetry**: the 30-second trace now sits beside the engine-speed and speed numbers, the same height as them, and coolant, fuel and Disconnect share one line underneath.
- The tab bar at the bottom is thinner in every style, upright and sideways.

## 1.7.4 (20 October 2026)
- Fixed: **Throttle** showed about 12–20% with your foot off the pedal, for example stopped at a red light. That's the raw throttle sensor, which almost no car reads as 0% when closed. The app now learns your car's closed-throttle reading (the lowest it sees, remembered for your car between drives) and measures from there, so it reads 0% with your foot off and 100% wide open. The Dashboard, Graphs, Android Auto, recorded drives and the driving score all use the corrected value.
- The adapter log still shows what the car actually sent, now labelled **Throttle sensor**.

## 1.7.3 (20 October 2026)
- Landscape on **Race telemetry**, small phones: when there isn't room for the 30-second trace under the big numbers, it moves below both columns at full width instead of being cut off.

## 1.7.2 (20 October 2026)
- Fixed: on **Race telemetry**, the big engine-speed number could run into the speed number on smaller phones, with large text, or sideways. Both numbers now shrink just enough to fit their column, sized for the widest reading (8,888 rpm) so they don't change size as the revs change.
- Fixed: readouts meant to fit on one line were wrapping onto a second line and pushing everything below them down:
  - Telemetry's coolant and fuel boxes now stay on one line.
  - The Digital cockpit's glass panels put each number and its unit on the same line.
  - The status line under Connect is shorter ("ELM327 v1.5 · CAN 11-bit, 500 kbaud").
  - The driving-score buttons (Efficiency, Everyday, Racing) no longer get cut short.
- Landscape on Race telemetry: the 30-second trace shortens to fit, so the whole left column is on screen.

## 1.7.1 (20 October 2026)
- Landscape: the tab bar is slimmer sideways (icons only), and the main dial re-fits whenever the screen's height changes, so it's never cut off at the bottom.

## 1.7.0 (20 October 2026)
- **Landscape.** Turn your phone sideways and every Dashboard style switches to two columns:
  - **Livery styles:** the rev roundel on the left; speed, coolant and fuel on the right.
  - **Race telemetry:** shift lights, the big numbers and the 30-second trace on the left; coolant, fuel, Connect and status on the right.
  - **Digital cockpit:** the speed ring on the left; the glass panels on the right.
  - **Cyberpunk:** the neon display on the left; the coolant, fuel and battery readouts on the right.
- The main dial is sized to fit the screen's height, Connect and the status sit beside it, and the live-data list underneath goes into two columns. Livery stripes are slimmer sideways so the readouts sit higher. Turning back to portrait restores the usual layout, and live readings, recording and the connection carry on throughout.

## 1.6.3 (20 October 2026)
- After you choose your car, a popup lists what may not work with it and why, so a missing reading isn't a surprise. Each one is marked **Probably won't work**, **May not work** or **Works, but slower**. For example:
  - Fuel level on Ford-built cars from before about 2008, like the 2006 Mazda Tribute, which don't send it over OBD2.
  - Oil temperature on most cars before about 2010.
  - Manifold pressure or MAF airflow, since most engines have one sensor or the other.
  - Timing advance on diesels, and slower updates on cars that use the older pre-CAN protocols.
- It's based on what cars like yours usually report. When you connect, the app still asks your car and shows everything it does report.
- Open the list again any time with **What may not work with this car** under **Diagnostics → Your car**.

## 1.6.2 (20 October 2026)
- Better at finding which readings your car reports. When the car is first asked, several of its modules (engine, gearbox and so on) can answer, each with its own list. The app used to take only the first answer, and if that wasn't the engine it could miss readings such as fuel level. It now combines every module's answer.
- Any dashboard reading still missing from the car's list is tried once anyway, because some cars answer readings they don't list.
- If the car really doesn't report fuel level or coolant temperature, the big Dashboard readouts now say **n/a** instead of a dash that looks like it's still loading. Many older cars, especially Ford-built ones from before about 2008, don't send fuel level over OBD2 at all; the fuel gauge gets it from the instrument cluster instead.

## 1.6.1 (20 October 2026)
- Fixed: on the **Race telemetry** layout, the shift lights across the top stayed dark in normal driving. They only started at about a third of the redline (around 2,300 rpm on most petrol cars), so at idle and everyday revs nothing lit up. They now cover the whole rev range: one light at idle, climbing one light at a time as the revs rise, all 15 just below your car's redline, and flashing blue at the redline as before.

## 1.6.0 (20 October 2026)
- **Cyberpunk** is now a dashboard layout of its own, alongside Race telemetry and Digital cockpit, instead of a livery colourway. It's a neon heads-up display: a segmented rev bar that climbs from cyan to magenta and turns red past your car's redline, the speed in big digits split into magenta and cyan ghosts, a strip of bars showing the last 15 seconds of revs, and glowing cut-corner readouts for coolant, fuel and battery, all over faint scanlines on a magenta-hazed night-city black. The live-data list and panels pick up its neon edges too.
- If you already chose Cyberpunk, you'll see the new layout straight away.

## 1.5.0 (20 October 2026)
- New **Cyberpunk** style: neon magenta and cyan on a night-city black, with sharp raked edges, a wide upright display font, and a neon glow on the rev ring, its disc and the livery stripes. Android Auto picks up its magenta and cyan too. Choose it with the palette button on the Dashboard.

## 1.4.2 (20 October 2026)
- Fixed: on some phones the "First, choose your car" step could disappear if Android rebuilt the screen just after the app opened (for example after a system setting changed). It now comes back until you've chosen your car or tapped **Not listed**.

## 1.4.1 (20 October 2026)
- The demo car now reports a VIN for the car you chose, so its make and year match **Your car** on the Diagnostics tab. (It always said it was a 2012 Ford before.)
- With a real car, if the VIN's make or year doesn't match the car you chose, Diagnostics says so and offers **Set my car to** the VIN's make and year. Fault codes are explained for the car you choose, so it's worth keeping them in step.

## 1.4.0 (20 October 2026)

### Your car comes first
- The first time you open the app, it asks for your car (make, year, model and engine) before anything else. If you skip it, it asks again when you tap Connect. Choose **Not listed** if your car isn't there.
- Trouble codes are now explained for your car. Manufacturer-specific codes (P1, B1, C1, U1 and similar) mean different things on each brand, so they're named for your make, for example "Toyota-specific code", with where to look it up. If you haven't chosen a car, the make read from the VIN is used.
- Your car also sets the rev dial's redline and the protocol tried first, as before.

### Driving score
- A new **Driving score** card on the Dashboard rates the current drive out of 100, in the style you choose:
  - **Efficiency:** gentle acceleration, low revs, little idling, anticipating stops and moderate cruising speed.
  - **Everyday:** smooth acceleration, gentle braking and relaxed revs.
  - **Racing:** for track days. Using the top of the rev range at full throttle, committed acceleration, not hitting the limiter, not labouring the engine and keeping temperatures in check. Shows your best 0 to 100 km/h time for the drive.
- Each part of the score has a bar, and the card shows a tip for the part that would help most. The score appears after about half a minute of driving and starts again with each new connection.
- Scores are worked out on your phone and never saved.

### Telemetry is only saved when you choose
- Nothing about your drives is saved unless you tap **Record drive**. Live graphs and the driving score are kept in memory only, for the current drive.
- Recorded drives are now left out of phone and Google backups, and out of moving to a new phone, so they stay on the phone unless you share them.
- Reports and logs you share are temporary copies, deleted the next time the app opens.

## 1.3.3 (20 October 2026)
- One **Bluetooth** option instead of separate Bluetooth Classic and Bluetooth LE choices. It lists your paired adapters straight away, then adds nearby ones as it finds them, whichever kind of Bluetooth they use, by name only.
- Tapping an adapter connects the right way for its kind. A Classic adapter that isn't paired yet gets paired first: Android asks for the PIN (usually 1234 or 0000), then the app connects, so there's no need to visit Android's Bluetooth settings.
- The in-app Help and the README are updated to match.

## 1.3.2 (20 October 2026)
- Choosing an adapter now lists each device by name only, without its Bluetooth address. Devices with no name are left out, adapters that look like OBD2 adapters stay at the top, and two devices with the same name are numbered, for example "OBDII" and "OBDII (2)".

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
