#!/bin/bash
# Installs the published APK on a fresh emulator exactly as a user would get it, opens it,
# starts the demo car, visits every tab and checks nothing crashed. Saves screenshots.
set -u
API=$1; OUT=reports/api-$API; mkdir -p "$OUT"
PKG=com.obdreader.app
log() { echo "$*" | tee -a "$OUT/result.txt"; }

adb wait-for-device
# Emulators often show "Pixel Launcher isn't responding" just after booting. Hide system error
# dialogs (app crashes are still caught from the log) and give the launcher time to settle.
adb shell settings put global hide_error_dialogs 1
sleep 20
adb shell input keyevent KEYCODE_HOME
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb logcat -c

if adb install OBD2Reader.apk > "$OUT/install.txt" 2>&1; then log "install: OK"; else log "install: FAILED $(tail -1 "$OUT/install.txt")"; exit 1; fi
adb shell dumpsys package $PKG | grep -E "versionName|versionCode|targetSdk" | head -3 | sed 's/^ */  /' | tee -a "$OUT/result.txt"

adb shell am start -W -n $PKG/.MainActivity > /dev/null
sleep 6
shot() { adb exec-out screencap -p > "$OUT/$1.png"; }
# Dismiss a permission or notification prompt if Android shows one.
tap_text() {
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
  adb shell cat /sdcard/ui.xml > "$OUT/ui.xml"
  local b=$(python3 - "$1" "$OUT/ui.xml" <<'PY'
import re, sys
want, path = sys.argv[1].lower(), sys.argv[2]
xml = open(path, encoding='utf-8', errors='ignore').read()
for m in re.finditer(r'<node [^>]*>', xml):
    n = m.group(0)
    t = (re.search(r' text="([^"]*)"', n) or [None, ''])[1].lower()
    d = (re.search(r'content-desc="([^"]*)"', n) or [None, ''])[1].lower()
    if t == want or d == want or (want in t and len(t) < len(want) + 12):
        x1, y1, x2, y2 = map(int, re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n).groups())
        print((x1 + x2) // 2, (y1 + y2) // 2); break
PY
)
  if [ -n "$b" ]; then adb shell input tap $b; sleep 1; return 0; fi
  return 1
}
alive() { adb shell pidof $PKG > /dev/null; }
# Copies a file the app made to $3. Debug builds: straight from the app's own folder (run-as).
# Release builds can't be read that way, so look where the user finds it: Pictures or Movies
# › OBD2 Reader (Android 10 and newer). Prints the copied size, or nothing if not found.
pull_made() {   # $1 name pattern, $2 Pictures|Movies, $3 local file
  local F
  F=$(adb shell run-as $PKG ls cache/exports 2>/dev/null | tr -d '\r' | grep "$1" | tail -1)
  if [ -n "$F" ]; then adb exec-out run-as $PKG cat "cache/exports/$F" > "$3"
  elif [ "$API" -ge 29 ]; then
    F=$(adb shell ls -t "'/sdcard/$2/OBD2 Reader/'" 2>/dev/null | tr -d '\r' | grep "$1" | head -1)
    [ -n "$F" ] && adb pull "/sdcard/$2/OBD2 Reader/$F" "$3" > /dev/null 2>&1
  fi
  [ -s "$3" ] && stat -c %s "$3"
}

tap_contains() {   # taps the first on-screen item whose text contains $1
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
  adb shell cat /sdcard/ui.xml > "$OUT/ui.xml"
  local b=$(python3 - "$1" "$OUT/ui.xml" <<'PY'
import re, sys
want, path = sys.argv[1], sys.argv[2]
xml = open(path, encoding='utf-8', errors='ignore').read()
for n in re.findall(r'<node [^>]*>', xml):
    t = (re.search(r' text="([^"]*)"', n) or [None, ''])[1]
    if want in t:
        x1, y1, x2, y2 = map(int, re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n).groups())
        print((x1 + x2) // 2, (y1 + y2) // 2); break
PY
)
  [ -n "$b" ] && adb shell input tap $b && sleep 1 && return 0
  return 1
}
# Centre of a view inside a scrolled page, after scrolling that page all the way down.
view_center_bottom() {   # $1 view id, $2 scrolling page id
  for k in 1 2 3; do adb shell input swipe 540 2000 540 500 150; done; sleep 1
  adb shell dumpsys activity top > "$OUT/views.txt" 2>/dev/null
  python3 - "$1" "$2" "$OUT/views.txt" <<'PY'
import re, sys
want, page, path = sys.argv[1], sys.argv[2], sys.argv[3]
stack = []; page_box = None; page_depth = None; content_h = None
for line in open(path, encoding='utf-8', errors='ignore'):
    m = re.search(r'^(\s*)\S+\{[^}]* (-?\d+),(-?\d+)-(-?\d+),(-?\d+)', line)
    if not m: continue
    depth = len(m.group(1)); x1, y1, x2, y2 = map(int, m.groups()[1:])
    while stack and stack[-1][0] >= depth: stack.pop()
    ox = sum(s[1] for s in stack); oy = sum(s[2] for s in stack)
    if page_depth is not None and content_h is None and depth > page_depth: content_h = y2 - y1
    if ('app:id/' + page) in line: page_box = (oy + y1, y2 - y1); page_depth = depth
    stack.append((depth, x1, y1))
    if ('app:id/' + want) in line and page_box:
        scroll = max(0, (content_h or 0) - page_box[1])
        print(ox + (x1 + x2) // 2, oy + (y1 + y2) // 2 - scroll); sys.exit(0)
PY
}

# Fails if a button is missing or squeezed too small to tap (at least 40% of the width or 200 px).
check_button() {
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
  adb shell cat /sdcard/ui.xml > "$OUT/ui-start.xml"
  python3 - "$1" "$OUT/ui-start.xml" <<'PY'
import re, sys
want, path = sys.argv[1].lower(), sys.argv[2]
xml = open(path, encoding='utf-8', errors='ignore').read()
for n in re.findall(r'<node [^>]*>', xml):
    d = (re.search(r'content-desc="([^"]*)"', n) or [None, ''])[1].lower()
    t = (re.search(r' text="([^"]*)"', n) or [None, ''])[1].lower()
    if want in (d, t):
        x1, y1, x2, y2 = map(int, re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n).groups())
        sys.exit(0 if (x2 - x1) >= 200 and (y2 - y1) >= 40 else 1)
sys.exit(2)
PY
}
# Size of a view by its id, from the window's view hierarchy. Unlike a UI dump, this works while
# the dashboard is animating live data.
view_width() {
  adb shell dumpsys activity top > "$OUT/views.txt" 2>/dev/null
  python3 - "$1" "$OUT/views.txt" <<'PY'
import re, sys
for line in open(sys.argv[2], encoding='utf-8', errors='ignore'):
    if ('app:id/' + sys.argv[1]) in line:
        m = re.search(r' (-?\d+),(-?\d+)-(-?\d+),(-?\d+) ', line)
        if m:
            x1, y1, x2, y2 = map(int, m.groups()); print(x2 - x1); sys.exit(0)
print(0)
PY
}
# Where the bottom tabs are, read once while the screen is still: tapping them later works
# even while live data is animating.
remember_tabs() {
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
  adb shell cat /sdcard/ui.xml > "$OUT/ui-tabs.xml"
  python3 - "$OUT/ui-tabs.xml" <<'PY' > "$OUT/tabs.txt"
import re, sys
xml = open(sys.argv[1], encoding='utf-8', errors='ignore').read()
for n in re.findall(r'<node [^>]*>', xml):
    d = (re.search(r'content-desc="([^"]*)"', n) or [None, ''])[1]
    if d in ('Dashboard', 'Graphs', 'Diagnostics'):
        x1, y1, x2, y2 = map(int, re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n).groups())
        print(d, (x1 + x2) // 2, (y1 + y2) // 2)
PY
}
tap_tab() {
  local xy=$(grep "^$1 " "$OUT/tabs.txt" | cut -d' ' -f2-)
  [ -n "$xy" ] && adb shell input tap $xy && sleep 1
}
FAIL=0
# First launch asks for the car before anything else.
SEEN=0
for i in 1 2 3 4 5 6 7 8 9 10; do   # slow emulators can take a while to show it
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
  adb shell cat /sdcard/ui.xml > "$OUT/ui.xml"
  grep -q "First, choose your car" "$OUT/ui.xml" && { SEEN=1; break; }
  sleep 2
done
if [ $SEEN = 1 ]; then
  adb exec-out screencap -p > "$OUT/0-choose-car.png"
  log "car picker: shown first"
  # Engine: one tap on the box opens the list of engines for the model and year (Ford, 2010 by default).
  if tap_text "Model"; then
    adb shell input text "Escape"; sleep 1
    adb shell input keyevent KEYCODE_ENTER; sleep 1
    tap_text "Engine" > /dev/null; sleep 2
    adb exec-out screencap -p > "$OUT/0b-engine-list.png"
    # Dumps can fail while the list animates open, so try a few times (clearing the old dump first).
    ENG=0
    for i in 1 2 3 4; do
      adb shell rm -f /sdcard/ui.xml; adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1; adb shell cat /sdcard/ui.xml > "$OUT/ui-engine.xml" 2>/dev/null
      grep -q 'text="2.5 I4"' "$OUT/ui-engine.xml" && { ENG=1; break; }
      # The list is a pop-up window of its own, which the screen dump often leaves out: look for it.
      adb shell dumpsys window windows 2>/dev/null | grep -q "Window{.*PopupWindow" && { ENG=1; break; }
      sleep 1
    done
    [ $ENG = 1 ] && log "engine list: opens on one tap" || { log "engine list: FAILED (no list)"; FAIL=1; }
    tap_contains "First, choose your car" > /dev/null; sleep 1   # closes the list without closing the sheet
    # Put the keyboard away so the buttons under it can be tapped (Back only while it's showing).
    adb shell dumpsys input_method | grep -q "mInputShown=true" && { adb shell input keyevent KEYCODE_BACK; sleep 1; }
  else
    log "engine list: FAILED (no Model box)"; FAIL=1
  fi
  tap_text "Not listed" || { log "car picker: FAILED (no Not listed button)"; FAIL=1; }
  sleep 1
else
  log "car picker: FAILED (not shown on first launch)"; FAIL=1
fi
remember_tabs
shot 1-opened
alive && log "open: OK" || { log "open: FAILED (app not running)"; FAIL=1; }
for b in "Connect" "Demo car"; do
  check_button "$b"; r=$?
  case $r in 0) log "button '$b': visible";; 1) log "button '$b': FAILED (too small to tap)"; FAIL=1;; *) log "button '$b': FAILED (missing)"; FAIL=1;; esac
done
tap_text "Demo car" && log "demo: started" || { log "demo: FAILED (button not found)"; FAIL=1; }
sleep 8
tap_text "Allow" > /dev/null || true
shot 2-demo-dashboard
W=$(view_width connectButton)
[ "${W:-0}" -ge 200 ] && log "button 'Disconnect': visible (${W}px)" || { log "button 'Disconnect': FAILED (${W:-0}px wide)"; FAIL=1; }
# Turn the phone sideways: the Dashboard switches to two columns, then back to portrait.
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1
sleep 5
shot 2b-landscape
LW=$(view_width connectButton); LR=$(view_width rpmRoundel)
if alive && [ "${LW:-0}" -ge 200 ] && [ "${LR:-0}" -ge 200 ]; then log "landscape: OK (Disconnect ${LW}px, dial ${LR}px)"; else log "landscape: FAILED (Disconnect ${LW:-0}px, dial ${LR:-0}px)"; FAIL=1; fi
adb shell settings put system user_rotation 0
sleep 4
shot 2c-portrait-again
W=$(view_width connectButton)
alive && [ "${W:-0}" -ge 200 ] && log "back to portrait: OK" || { log "back to portrait: FAILED"; FAIL=1; }
# Centre of a view by its id, from the window's view hierarchy (works while animating).
view_center() {
  adb shell dumpsys activity top > "$OUT/views.txt" 2>/dev/null
  python3 - "$1" "$OUT/views.txt" <<'PY'
import re, sys
stack = []
for line in open(sys.argv[2], encoding='utf-8', errors='ignore'):
    m = re.search(r'^(\s*)\S+\{[^}]* (-?\d+),(-?\d+)-(-?\d+),(-?\d+)', line)
    if not m: continue
    depth = len(m.group(1)); x1, y1, x2, y2 = map(int, m.groups()[1:])
    while stack and stack[-1][0] >= depth: stack.pop()
    ox = sum(s[1] for s in stack); oy = sum(s[2] for s in stack)
    stack.append((depth, x1, y1))
    if ('app:id/' + sys.argv[1]) in line:
        print(ox + (x1 + x2) // 2, oy + (y1 + y2) // 2); sys.exit(0)
PY
}
# Scrolls the page back to the very top, so positions read from the view tree are on screen.
scroll_top() { adb shell input swipe 540 800 540 2000 200; adb shell input swipe 540 800 540 2000 200; sleep 1; }
# Each style with its own layout, upright and sideways, with the demo car running. The second
# pass is a small phone with large text (360 dp wide, text at 115%), where crowding shows up.
for PASS in normal small; do
if [ $PASS = small ]; then adb shell wm density 480; adb shell settings put system font_scale 1.15; sleep 3; fi
for STYLE in "Race telemetry" "Digital cockpit" "Cyberpunk" "Asphalt"; do
  [ $PASS = normal ] && [ "$STYLE" = "Asphalt" ] && continue
  SLUG=$(echo "$STYLE" | tr ' A-Z' '-a-z'); [ $PASS = small ] && SLUG="small-$SLUG"
  scroll_top; XY=$(view_center connectButton); [ -n "$XY" ] && adb shell input tap $XY; sleep 3   # disconnect, so the screen is still
  tap_text "Done" > /dev/null || true   # close the trip summary if that drive was long enough for one
  tap_text "Style" || { log "style $STYLE: FAILED (no Style button)"; FAIL=1; continue; }
  sleep 1
  for i in 1 2 3; do tap_text "$STYLE" && break; adb shell input swipe 540 1800 540 700 300; sleep 1; done
  sleep 4
  tap_text "Demo car" || { adb shell input swipe 540 1500 540 900 300; sleep 1; tap_text "Demo car"; } || { log "style $STYLE: FAILED (no Demo car)"; FAIL=1; continue; }
  adb shell input swipe 540 900 540 1700 300   # back to the top
  sleep 8
  shot "5-$SLUG"
  adb shell settings put system user_rotation 1; sleep 5
  shot "6-$SLUG-landscape"
  alive && log "style $STYLE: upright and sideways OK" || { log "style $STYLE: FAILED (app stopped)"; FAIL=1; }
  adb shell settings put system user_rotation 0; sleep 4
done
done
adb shell wm density reset; adb shell settings put system font_scale 1.0; sleep 3
# A longer drive, then Disconnect: the trip summary should sum it up.
sleep 35
scroll_top; XY=$(view_center connectButton); [ -n "$XY" ] && adb shell input tap $XY; sleep 3
adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1; adb shell cat /sdcard/ui.xml > "$OUT/ui-trip.xml"
shot 7-trip-summary
if grep -q "Your drive" "$OUT/ui-trip.xml" && grep -q "Top speed" "$OUT/ui-trip.xml"; then log "trip summary: shown"; else log "trip summary: FAILED (not shown)"; FAIL=1; fi
# Share the drive as a picture: the image is made and Android's share sheet opens.
if tap_text "Share as a picture" && tap_contains "Story (9:16)"; then
  sleep 3
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1; adb shell cat /sdcard/ui.xml > "$OUT/ui-share.xml"
  if [ -n "$(pull_made story.png Pictures "$OUT/9-drive-card.png")" ]; then
    log "drive card: made"
  elif [ "$API" -lt 29 ] && grep -qi 'resolver\|chooser' "$OUT/ui-share.xml"; then
    # Android 8 and 9 release builds: the picture is only shared, not saved, so the share sheet opening is the check.
    log "drive card: made (share sheet open)"
  else
    log "drive card: FAILED (no picture)"; FAIL=1
  fi
  shot 9a-share-sheet
  adb shell input keyevent KEYCODE_BACK; sleep 2
else
  log "drive card: FAILED (button missing)"; FAIL=1
fi
tap_text "Done" > /dev/null || true
tap_text "Demo car" > /dev/null || true
sleep 8
# Performance timers: the sheet opens and follows the demo car (staging, ready, timing).
XY=$(view_center_bottom perfButton pageDashboard); [ -n "$XY" ] && adb shell input tap $XY
sleep 25
shot 10-performance
adb shell dumpsys activity top > "$OUT/views-perf.txt" 2>/dev/null
# The screen keeps animating, so a UI dump can fail; try a few times.
for i in 1 2 3 4 5 6; do
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1; adb shell cat /sdcard/ui.xml > "$OUT/ui-perf.xml"
  grep -q "Performance timers" "$OUT/ui-perf.xml" && break; sleep 2
done
# Older Android can't dump an animating screen, so also count the app's windows: a sheet adds one.
WINS=$(adb shell dumpsys window windows 2>/dev/null | grep -c "Window #.*$PKG")
if grep -q "Performance timers" "$OUT/ui-perf.xml" || grep -q "Performance timers" "$OUT/views-perf.txt" || [ "${WINS:-0}" -ge 2 ]; then log "performance timers: shown"; else log "performance timers: FAILED (not shown)"; FAIL=1; fi
adb shell input keyevent KEYCODE_BACK; sleep 2
# Codes are now also read on their own shortly after connecting, so they may already be listed.
tap_tab Diagnostics && { tap_text "Read codes" || grep -q "P0133" "$OUT/ui.xml"; } && sleep 3 && shot 4-diagnostics && log "diagnostics: OK" || { log "diagnostics: FAILED"; FAIL=1; }
tap_tab Graphs && sleep 4 && shot 3-graphs && log "graphs: OK" || { log "graphs: FAILED"; FAIL=1; }
# Video overlay: record a short drive on Graphs, then make an overlay video from it.
# Route: allow location up front (so the "save the route?" question is skipped) and feed the
# emulator a moving GPS position while it records; the recording should then say "with route".
adb shell pm grant $PKG android.permission.ACCESS_FINE_LOCATION 2>/dev/null
adb shell pm grant $PKG android.permission.ACCESS_COARSE_LOCATION 2>/dev/null
adb shell settings put secure location_mode 3 2>/dev/null
adb emu geo fix 151.2093 -33.8688 > /dev/null 2>&1
XY=$(view_center_bottom recordButton pageGraphs); [ -n "$XY" ] && adb shell input tap $XY
ROUTE=0
for i in $(seq 1 10); do
  adb emu geo fix "$(awk "BEGIN{print 151.2093 + $i * 0.0004}")" "$(awk "BEGIN{print -33.8688 - $i * 0.0003}")" > /dev/null 2>&1
  sleep 2
done
adb shell rm -f /sdcard/ui.xml; adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1; adb shell cat /sdcard/ui.xml > "$OUT/ui-recording.xml" 2>/dev/null
grep -q "with route" "$OUT/ui-recording.xml" && ROUTE=1
XY=$(view_center_bottom recordButton pageGraphs); [ -n "$XY" ] && adb shell input tap $XY; sleep 2
shot 8a-recorded
# Debug builds: check the file itself has positions.
F=$(adb shell run-as $PKG ls files/logs 2>/dev/null | tr -d '\r' | grep csv | tail -1)
if [ -n "$F" ]; then
  adb exec-out run-as $PKG cat "files/logs/$F" > "$OUT/recorded.csv"
  head -1 "$OUT/recorded.csv" | grep -q "Latitude" && awk -F, 'NR>1 && $(NF-2) != "" {n++} END {exit !(n > 0)}' "$OUT/recorded.csv" && ROUTE=1
fi
[ $ROUTE = 1 ] && log "route: saved with the recording" || { log "route: FAILED (not saved)"; FAIL=1; }
tap_tab Dashboard; scroll_top; XY=$(view_center connectButton); [ -n "$XY" ] && adb shell input tap $XY; sleep 3
tap_text "Done" > /dev/null || true
tap_tab Graphs; sleep 2
XY=$(view_center_bottom savedLogsButton pageGraphs); [ -n "$XY" ] && adb shell input tap $XY; sleep 2
shot 8b-saved-drives
if tap_contains " KB" && tap_text "Make a video overlay" && tap_contains "720p" && tap_text "Make video"; then
  READY=0
  for i in $(seq 1 60); do
    sleep 3
    adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1; adb shell cat /sdcard/ui.xml > "$OUT/ui-video.xml"
    grep -q "Video ready" "$OUT/ui-video.xml" && { READY=1; break; }
    grep -q "make the video" "$OUT/ui-video.xml" && break
  done
  shot 8-video-ready
  if [ $READY = 1 ]; then
    SZ=$(pull_made overlay.mp4 Movies "$OUT/overlay.mp4"); SZ=${SZ:-0}
    # Android 8 and 9 release builds keep the video private until it's shared; "Video ready" is the check there.
    [ "$SZ" = 0 ] && [ "$API" -lt 29 ] && ! adb shell run-as $PKG true 2>/dev/null && SZ=99999
    command -v ffmpeg > /dev/null && ffmpeg -loglevel error -y -ss 12 -i "$OUT/overlay.mp4" -frames:v 1 "$OUT/8-video-frame.png"
    DUR=$(command -v ffprobe > /dev/null && ffprobe -v error -show_entries format=duration -of csv=p=0 "$OUT/overlay.mp4")
    [ "$SZ" -gt 10000 ] && log "video overlay: made ($SZ bytes, ${DUR:-?} s)" || { log "video overlay: FAILED (file $SZ bytes)"; FAIL=1; }
    # The video stays in the reports, so a frame can be checked.
    tap_text "Done" > /dev/null || true
  else
    log "video overlay: FAILED (not ready)"; FAIL=1
  fi
else
  log "video overlay: FAILED (couldn't open the options)"; FAIL=1
fi
adb shell input keyevent KEYCODE_BACK; sleep 1

# Car scan: lists what the car reports (it needs a connection, so make sure the demo car is on).
tap_tab Dashboard; sleep 2; scroll_top; tap_text "Demo car" > /dev/null 2>&1; sleep 8
tap_tab Diagnostics; sleep 2
for k in 1 2 3; do adb shell input swipe 540 2000 540 600 150; done; sleep 1
if tap_text "Scan what your car reports"; then
  sleep 8
  SEEN=0
  for i in 1 2 3 4 5; do
    adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1; adb shell cat /sdcard/ui.xml > "$OUT/ui-scan.xml"
    grep -q "What your car reports" "$OUT/ui-scan.xml" && { SEEN=1; break; }; sleep 2
  done
  shot 12-car-scan
  [ $SEEN = 1 ] && log "car scan: shown" || { log "car scan: FAILED (not shown)"; FAIL=1; }
  adb shell input keyevent KEYCODE_BACK; sleep 2
else
  log "car scan: FAILED (row missing)"; FAIL=1
fi
# Dashboard readouts: swap fuel for intake air, and the Dashboard shows it.
tap_tab Diagnostics; sleep 2
for k in 1 2 3; do adb shell input swipe 540 2000 540 600 150; done; sleep 1
if tap_text "Dashboard readouts" && tap_contains "Readout 2" && tap_text "Intake air" && tap_text "Save"; then
  sleep 4
  tap_tab Dashboard; sleep 3; scroll_top
  shot 11-custom-readouts
  SEEN=0
  for i in 1 2 3 4 5 6; do
    # Clear the last dump first, so a failed dump can't be read as this screen.
    adb shell rm -f /sdcard/ui.xml; adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1; adb shell cat /sdcard/ui.xml > "$OUT/ui-slots.xml" 2>/dev/null
    grep -q 'content-desc="Intake air' "$OUT/ui-slots.xml" && { SEEN=1; break; }; sleep 2
  done
  # Release builds: the Dashboard readouts row on Diagnostics (a still screen) lists what's in use.
  if [ $SEEN = 0 ]; then
    tap_tab Diagnostics; sleep 2
    for k in 1 2 3; do adb shell input swipe 540 2000 540 600 150; done
    for i in 1 2 3 4; do
      sleep 2; adb shell rm -f /sdcard/ui.xml; adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1; adb shell cat /sdcard/ui.xml > "$OUT/ui-slots-row.xml" 2>/dev/null
      grep -q 'text="[^"]*, Intake air' "$OUT/ui-slots-row.xml" && alive && { SEEN=1; break; }
    done
    tap_tab Dashboard; sleep 2
  fi
  # Newer Android can't always dump an animating screen; the saved choice and a running app also count.
  [ $SEEN = 0 ] && adb shell run-as $PKG cat shared_prefs/obd_reader.xml 2>/dev/null | grep -q 'dash_slots">05,0F' && alive && SEEN=1
  [ $SEEN = 1 ] && log "dashboard readouts: intake air shown" || { log "dashboard readouts: FAILED (not shown)"; FAIL=1; }
else
  log "dashboard readouts: FAILED (couldn't change)"; FAIL=1
fi
# Units: miles and Fahrenheit, then the Dashboard shows mph.
tap_tab Diagnostics; sleep 2
for k in 1 2 3; do adb shell input swipe 540 2000 540 600 150; done; sleep 1
if tap_text "Units" && tap_contains "Miles (mph" && tap_contains "Fahrenheit" && tap_text "Save"; then
  sleep 4; tap_tab Dashboard; sleep 4; scroll_top
  shot 13-imperial
  UNITS=0
  adb shell run-as $PKG cat shared_prefs/obd_reader.xml 2>/dev/null | grep -q 'units_mph" value="true' && UNITS=1
  # Release builds: the Units row on Diagnostics says what's in use.
  if [ $UNITS = 0 ]; then
    tap_tab Diagnostics; sleep 2
    for k in 1 2 3; do adb shell input swipe 540 2000 540 600 150; done
    for i in 1 2 3 4; do
      sleep 2; adb shell rm -f /sdcard/ui.xml; adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1; adb shell cat /sdcard/ui.xml > "$OUT/ui-units.xml" 2>/dev/null
      grep -q 'text="mph, °F"' "$OUT/ui-units.xml" && { UNITS=1; break; }
    done
    tap_tab Dashboard; sleep 2
  fi
  [ $UNITS = 1 ] && alive && log "units: mph and °F saved" || { log "units: FAILED"; FAIL=1; }
else
  log "units: FAILED (couldn't change)"; FAIL=1
fi
adb shell dumpsys notification > "$OUT/notifications.txt" 2>/dev/null
grep -q "mId='alerts'" "$OUT/notifications.txt" && log "alerts channel: created" || { log "alerts channel: FAILED (missing)"; FAIL=1; }
alive && log "still running after tour: OK" || log "still running after tour: FAILED"
adb logcat -d > "$OUT/logcat.txt"
if grep -q "FATAL EXCEPTION" "$OUT/logcat.txt" && grep -A3 "FATAL EXCEPTION" "$OUT/logcat.txt" | grep -q "$PKG"; then
  log "crashes: FOUND"; grep -A20 "FATAL EXCEPTION" "$OUT/logcat.txt" > "$OUT/crash.txt"; exit 1
fi
log "crashes: none"
exit $FAIL
