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

# Newer Android (17) can still be starting its system services after "boot completed":
# wait until the package installer answers, then install, trying again if it isn't ready.
for i in $(seq 1 60); do
  [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] && adb shell pm path android > /dev/null 2>&1 && break
  sleep 5
done
INSTALLED=0
for i in 1 2 3 4 5; do
  if adb install OBD2Reader.apk > "$OUT/install.txt" 2>&1; then INSTALLED=1; break; fi
  grep -q "Can't find service\|not enough space" "$OUT/install.txt" || break
  sleep 15
done
if [ $INSTALLED = 1 ]; then log "install: OK"; else log "install: FAILED $(tail -1 "$OUT/install.txt")"; exit 1; fi
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
  for k in 1 2 3 4 5; do adb shell input swipe 540 2000 540 500 150; done; sleep 2
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
adb shell dumpsys activity top > "$OUT/views-start.txt" 2>/dev/null
if grep "app:id/recordGroup" "$OUT/views-start.txt" | grep -q "{[0-9a-f]* G"; then log "record drive: hidden until connected"; else log "record drive: FAILED (shown before connecting)"; FAIL=1; fi
# The daily update check runs a few seconds after opening. If an older build is being tested
# and GitHub has a newer one, it offers the update: say Later so the tour can carry on.
sleep 6
adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1; adb shell cat /sdcard/ui.xml > "$OUT/ui-update.xml"
grep -q "is available" "$OUT/ui-update.xml" && tap_text "Later" > /dev/null
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
# Scrolls down from where the page is, a little at a time, until a button with this label is on
# screen, then taps it (labels come from the view's text or its spoken description).
tap_scrolled() {
  for i in 1 2 3 4 5 6 7 8; do tap_text "$1" > /dev/null && return 0; adb shell input swipe 540 1500 540 1000 300; sleep 1; done
  return 1
}
# Record drive sits just below the bottom of the screen on the Dashboard, and the Dashboard
# animates, so screen dumps of it fail. Scroll it up by a known 600px (a slow drag held still
# before letting go, so it doesn't fling) and tap where the view tree puts the button.
drag_up_600() {
  if adb shell input motionevent DOWN 540 1700 > /dev/null 2>&1; then
    for y in 1600 1500 1400 1300 1200 1100; do adb shell input motionevent MOVE 540 $y; done
    sleep 1; adb shell input motionevent UP 540 1100
  else
    adb shell input swipe 540 1700 540 1100 2500
  fi
  sleep 1
}
tap_record() {
  scroll_top
  local XY; XY=$(view_center recordButton); [ -n "$XY" ] || return 1
  set -- $XY
  if [ "$2" -gt 2000 ]; then drag_up_600; adb shell input tap $1 $(( $2 - 580 )); else adb shell input tap $1 $2; fi
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
  # Digital cockpit: after Disconnect, Connect must be drawn at full brightness again (it once
  # kept the faded look of the pressed or outline style until the app was reopened).
  if [ $PASS = normal ] && [ "$STYLE" = "Digital cockpit" ]; then
    scroll_top; XY=$(view_center connectButton); [ -n "$XY" ] && adb shell input tap $XY; sleep 3
    tap_text "Done" > /dev/null || true; sleep 2
    scroll_top; XY=$(view_center connectButton); shot "6b-$SLUG-after-disconnect"
    # Left of centre, clear of the "Connect" label.
    set -- $XY; PX=$(( ${1:-400} - 220 )); PY=${2:-0}
    LUM=$(python3 - "$OUT/6b-$SLUG-after-disconnect.png" $PX $PY <<'PY'
import sys, zlib, struct
# A small PNG reader (no extra packages): returns the brightest channel at x, y.
path, x, y = sys.argv[1], int(sys.argv[2]), int(sys.argv[3])
data = open(path, 'rb').read(); pos = 8; idat = b''; w = h = 0; ctype = 6
while pos < len(data):
    n, kind = struct.unpack('>I4s', data[pos:pos + 8]); body = data[pos + 8:pos + 8 + n]; pos += 12 + n
    if kind == b'IHDR': w, h, depth, ctype = struct.unpack('>IIBB', body[:10])
    elif kind == b'IDAT': idat += body
bpp = 4 if ctype == 6 else 3; raw = zlib.decompress(idat); stride = w * bpp; prev = bytearray(stride); row = None
for r in range(y + 1):
    f = raw[r * (stride + 1)]; line = bytearray(raw[r * (stride + 1) + 1:(r + 1) * (stride + 1)])
    for i in range(stride):
        a = line[i - bpp] if i >= bpp else 0; b = prev[i]; c = prev[i - bpp] if i >= bpp else 0
        if f == 1: line[i] = (line[i] + a) & 255
        elif f == 2: line[i] = (line[i] + b) & 255
        elif f == 3: line[i] = (line[i] + (a + b) // 2) & 255
        elif f == 4:
            p = a + b - c; pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
            line[i] = (line[i] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
    prev = line
print(max(prev[x * bpp:x * bpp + 3]))
PY
)
    if [ "${LUM:-0}" -ge 120 ]; then log "cockpit Connect after disconnect: bright ($LUM)"; else log "cockpit Connect after disconnect: FAILED (faded, $LUM)"; FAIL=1; fi
    tap_text "Demo car" > /dev/null; sleep 6   # carry on connected, as the next style expects
  fi
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
  # The preview is in the same window, with Story and Square to switch between; Share sends the card.
  sleep 3
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1; adb shell cat /sdcard/ui.xml > "$OUT/ui-preview.xml"
  shot 8b-share-preview
  if grep -q 'content-desc="Preview"' "$OUT/ui-preview.xml" && grep -qi 'text="Share"' "$OUT/ui-preview.xml"; then log "share preview: shown"; else log "share preview: FAILED (not shown)"; FAIL=1; fi
  # Tap and hold the preview: it grows into a full-screen layer, and goes when the finger lifts.
  PXY=$(python3 - "$OUT/ui-preview.xml" <<'PY'
import re, sys
xml = open(sys.argv[1], encoding='utf-8', errors='ignore').read()
m = re.search(r'content-desc="Preview"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml)
if m: x1, y1, x2, y2 = map(int, m.groups()); print((x1 + x2) // 2, (y1 + y2) // 2)
PY
)
  if [ -n "$PXY" ]; then
    set -- $PXY
    adb shell input swipe $1 $2 $1 $2 3000 &
    HOLD=$!
    sleep 2
    shot 8c-share-preview-held
    PEEK=$(adb shell dumpsys window windows 2>/dev/null | grep -c "Window{.*PopupWindow")
    wait $HOLD; sleep 1
    AFTER=$(adb shell dumpsys window windows 2>/dev/null | grep -c "Window{.*PopupWindow")
    if [ "${PEEK:-0}" -ge 1 ] && [ "${AFTER:-0}" -lt "${PEEK:-0}" ]; then log "share preview: enlarges on hold"; else log "share preview: FAILED (hold: ${PEEK:-0} layers, after: ${AFTER:-0})"; FAIL=1; fi
  else
    log "share preview: FAILED (preview not found to hold)"; FAIL=1
  fi
  tap_text "Share" > /dev/null || true
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
# Drive history: the drive summed up earlier is listed (Dashboard, under the score).
tap_tab Dashboard > /dev/null; sleep 2; scroll_top
XY=$(view_center_bottom historyButton pageDashboard); HIST=0
if [ -n "$XY" ]; then
  adb shell input tap $XY; sleep 2
  for i in 1 2 3; do
    adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1; adb shell cat /sdcard/ui.xml > "$OUT/ui-history.xml"
    grep -q 'text="Drive history"' "$OUT/ui-history.xml" && grep -q " km · " "$OUT/ui-history.xml" && { HIST=1; break; }; sleep 1
  done
  shot 4a-drive-history
  adb shell input keyevent KEYCODE_BACK; sleep 1
fi
[ $HIST = 1 ] && log "drive history: listed" || { log "drive history: FAILED (not shown)"; FAIL=1; }
scroll_top
tap_tab Diagnostics && { tap_text "Read codes" || grep -q "P0133" "$OUT/ui.xml"; } && sleep 3 && shot 4-diagnostics && log "diagnostics: OK" || { log "diagnostics: FAILED"; FAIL=1; }
# Freeze frame: the demo car stored one with P0133; it's shown on that code's card.
FF=0
for i in 1 2 3 4 5; do
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1; adb shell cat /sdcard/ui.xml > "$OUT/ui-freeze.xml"
  grep -q "freeze frame" "$OUT/ui-freeze.xml" && grep -q "Long-term fuel trim" "$OUT/ui-freeze.xml" && { FF=1; break; }
  adb shell input swipe 540 1600 540 900 300; sleep 1
done
shot 4c-freeze-frame
[ $FF = 1 ] && log "freeze frame: shown with P0133" || { log "freeze frame: FAILED (not shown)"; FAIL=1; }
# Battery check: the demo car starts with the engine off and cranks, so a check is measured.
BC=0
for i in 1 2 3 4 5 6; do
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1; adb shell cat /sdcard/ui.xml > "$OUT/ui-battery.xml"
  grep -q "Lowest while starting" "$OUT/ui-battery.xml" && { BC=1; break; }
  adb shell input swipe 540 1600 540 900 300; sleep 1
done
shot 4d-battery-check
[ $BC = 1 ] && log "battery check: $(grep -o 'Lowest while starting [0-9.]* V' "$OUT/ui-battery.xml" | head -1)" || { log "battery check: FAILED (not measured)"; FAIL=1; }
scroll_top
# Code report: saved straight to Downloads › OBD2 Reader (the other choice is Share).
SAVED=0
# Screen dumps sometimes fail on a busy emulator, so the whole step gets a second go.
for attempt in 1 2; do
  for i in 1 2 3 4 5 6 7 8; do tap_text "Save or share code report" > /dev/null && break; adb shell input swipe 540 1500 540 1000 300; sleep 1; done
  sleep 1
  for k in 1 2 3; do tap_text "Save to phone (Downloads)" > /dev/null && break; sleep 1; done
  sleep 2; adb logcat -d -s OBD2Save:I 2>/dev/null | grep -q "saved .* to Downloads" && SAVED=1
  adb shell ls "/sdcard/Download/OBD2 Reader/" 2>/dev/null | grep -q "." && SAVED=1
  [ $SAVED = 1 ] && break
  shot "4e-code-report-try$attempt"; adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1; adb shell cat /sdcard/ui.xml > "$OUT/ui-report-$attempt.xml"
  # A choice left open would hide everything after it: close it, start from the top.
  adb shell input keyevent KEYCODE_BACK; sleep 1; tap_tab Diagnostics > /dev/null; sleep 1; scroll_top
done
tap_tab Diagnostics > /dev/null; sleep 1
[ $SAVED = 1 ] && log "code report: saved to Downloads" || { log "code report: FAILED (not saved)"; FAIL=1; }
# VIN from the plate: typed in, it's shown instead of the car's, with what the car reports.
if tap_scrolled "Not your VIN? Enter it"; then
  sleep 1; adb shell input keyevent KEYCODE_MOVE_END $(for i in $(seq 1 20); do printf 'KEYCODE_DEL '; done)
  adb shell input text LFACTZ5NX62000375; sleep 1
  tap_text "Save" > /dev/null; sleep 2
  # The note sits under the VIN card, which can be at the bottom of the screen: bring it up first.
  adb shell input swipe 540 1500 540 900 400; sleep 1
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1; adb shell cat /sdcard/ui.xml > "$OUT/ui-vin.xml"
  shot 4b-vin-entered
  if grep -q "engine computer reports" "$OUT/ui-vin.xml" && grep -q "Change the VIN you entered" "$OUT/ui-vin.xml"; then
    log "VIN from the plate: shown, with the car's own"
  else
    log "VIN from the plate: FAILED (not shown)"; FAIL=1
  fi
  tap_text "Change the VIN you entered" > /dev/null && sleep 1 && tap_text "Use the car's VIN" > /dev/null; sleep 1
else
  log "VIN from the plate: FAILED (no button)"; FAIL=1
fi
scroll_top
# App updates: the row is on Diagnostics, near the bottom. Tapping it asks GitHub for the latest release.
for i in 1 2 3 4 5 6 7 8; do
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1; adb shell cat /sdcard/ui.xml > "$OUT/ui-updates.xml"
  grep -q 'text="App updates"' "$OUT/ui-updates.xml" && break
  adb shell input swipe 540 1600 540 600 300; sleep 1
done
if grep -q 'text="App updates"' "$OUT/ui-updates.xml"; then
  # The button sits under the row's text: scroll a little more until it's on screen, then tap it.
  for i in 1 2 3; do tap_text "Check for updates" > /dev/null && break; adb shell input swipe 540 1500 540 1100 300; sleep 1; done
  sleep 8
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1; adb shell cat /sdcard/ui.xml > "$OUT/ui-updates2.xml"
  shot 11-updates
  grep -q "is available" "$OUT/ui-updates2.xml" && tap_text "Later" > /dev/null
  CHECK=$(adb logcat -d -s OBD2Updater:I 2>/dev/null | grep -o "latest build [0-9]*, installed [0-9]*\|check failed.*" | tail -1)
  case "$CHECK" in
    "latest build"*) log "updates: GitHub checked ($CHECK)" ;;
    *) log "updates: FAILED (${CHECK:-no check logged})"; FAIL=1 ;;
  esac
  # The rest of the row: what's changed since this version, from the changelog on GitHub.
  tap_text "App updates" > /dev/null; sleep 6
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1; adb shell cat /sdcard/ui.xml > "$OUT/ui-changes.xml"
  shot 11b-whats-new
  if grep -q "What&apos;s new since\|What's new since" "$OUT/ui-changes.xml" && ! grep -q "Couldn&apos;t load the changelog\|Couldn't load the changelog" "$OUT/ui-changes.xml"; then log "updates: changes since this version shown"; else log "updates: FAILED (changelog not shown)"; FAIL=1; fi
  adb shell input keyevent KEYCODE_BACK; sleep 1
else
  log "updates: FAILED (row missing)"; FAIL=1
fi
tap_tab Graphs && sleep 4 && shot 3-graphs && log "graphs: OK" || { log "graphs: FAILED"; FAIL=1; }
# Graph order: move Speed above RPM (paused, so the screen can be read), then put it back.
scroll_top; XY=$(view_center graphPause); [ -n "$XY" ] && adb shell input tap $XY; sleep 1
adb logcat -c
XY=$(view_center graphOrder); [ -n "$XY" ] && adb shell input tap $XY; sleep 2
if tap_text "Move Speed up" > /dev/null; then
  sleep 1; shot 3b-graph-order; tap_text "Done" > /dev/null; sleep 1
  ORDER=$(adb logcat -d -s OBD2Graphs:I 2>/dev/null | grep -o "order: [0-9A-Z,]*" | tail -1)
  case "$ORDER" in "order: 0D,0C"*) log "graph order: changed ($ORDER)" ;; *) log "graph order: FAILED (${ORDER:-no change})"; FAIL=1 ;; esac
  XY=$(view_center graphOrder); [ -n "$XY" ] && adb shell input tap $XY; sleep 2
  tap_text "Move Speed down" > /dev/null; tap_text "Done" > /dev/null; sleep 1
else
  log "graph order: FAILED (couldn't open it)"; FAIL=1
fi
# Or tap and hold a graph and drag it: RPM dragged below Speed.
scroll_top; adb logcat -c
adb shell dumpsys activity top > "$OUT/views-graphs.txt" 2>/dev/null
STRIPS=$(python3 - "$OUT/views-graphs.txt" <<'PY'
import re, sys
stack = []; out = []
for line in open(sys.argv[1], encoding='utf-8', errors='ignore'):
    m = re.search(r'^(\s*)(\S+)\{[^}]* (-?\d+),(-?\d+)-(-?\d+),(-?\d+)', line)
    if not m: continue
    depth = len(m.group(1)); x1, y1, x2, y2 = map(int, m.groups()[2:])
    while stack and stack[-1][0] >= depth: stack.pop()
    ox = sum(s[1] for s in stack); oy = sum(s[2] for s in stack)
    stack.append((depth, x1, y1))
    if 'GraphStripView' in m.group(2): out.append(f"{ox + (x1 + x2) // 2} {oy + (y1 + y2) // 2}")
print(" ".join(out[:2]))
PY
)
set -- $STRIPS
if [ -n "$4" ] && adb shell input motionevent DOWN $1 $2 > /dev/null 2>&1; then
  sleep 1.5
  STEP=$(( ($4 - $2 + 200) / 8 ))
  for k in 1 2 3 4 5 6 7 8; do adb shell input motionevent MOVE $1 $(( $2 + k * STEP )); done
  sleep 1; adb shell input motionevent UP $1 $(( $2 + 8 * STEP )); sleep 2
  shot 3c-graph-dragged
  ORDER=$(adb logcat -d -s OBD2Graphs:I 2>/dev/null | grep -o "order: [0-9A-Z,]*" | tail -1)
  case "$ORDER" in "order: 0D,0C"*) log "graph order: dragged ($ORDER)" ;; *) log "graph order: FAILED (drag: ${ORDER:-no change})"; FAIL=1 ;; esac
  XY=$(view_center graphOrder); [ -n "$XY" ] && adb shell input tap $XY; sleep 2
  tap_text "Move Speed down" > /dev/null; tap_text "Done" > /dev/null; sleep 1
else
  log "graph order: drag not checked (no touch events on this emulator)"
fi
XY=$(view_center graphPause); [ -n "$XY" ] && adb shell input tap $XY; sleep 1
# Video overlay: record a short drive on the Dashboard (Record drive shows once connected), then make an overlay video from it.
tap_tab Dashboard; sleep 2; scroll_top
# Route: allow location up front (so the "save the route?" question is skipped) and feed the
# emulator a moving GPS position while it records; the recording should then say "with route".
adb shell pm grant $PKG android.permission.ACCESS_FINE_LOCATION 2>/dev/null
adb shell pm grant $PKG android.permission.ACCESS_COARSE_LOCATION 2>/dev/null
adb shell settings put secure location_mode 3 2>/dev/null
adb emu geo fix 151.2093 -33.8688 > /dev/null 2>&1
tap_record && log "record drive: on the Dashboard while connected" || { log "record drive: FAILED (no button on the Dashboard)"; FAIL=1; }
ROUTE=0
for i in $(seq 1 10); do
  adb emu geo fix "$(awk "BEGIN{print 151.2093 + $i * 0.0004}")" "$(awk "BEGIN{print -33.8688 - $i * 0.0003}")" > /dev/null 2>&1
  sleep 2
done
# The Graphs tab animates, so screen dumps often fail: try a few, and also ask Android whether
# the app is getting GPS updates right now (release builds can't be read with run-as afterwards).
for i in 1 2 3 4; do
  adb shell rm -f /sdcard/ui.xml; adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1; adb shell cat /sdcard/ui.xml > "$OUT/ui-recording.xml" 2>/dev/null
  grep -q "with route" "$OUT/ui-recording.xml" && { ROUTE=1; break; }; sleep 1
done
adb shell dumpsys location > "$OUT/location.txt" 2>/dev/null
[ $ROUTE = 0 ] && grep -iE "gps|fused" "$OUT/location.txt" | grep -q "$PKG" && ROUTE=1
# Minimised, the route must keep recording (positions further south, so they can be told apart).
adb shell input keyevent KEYCODE_HOME; sleep 2
for i in $(seq 1 12); do
  adb emu geo fix "$(awk "BEGIN{print 151.2140 + $i * 0.0004}")" "$(awk "BEGIN{print -33.8800 - $i * 0.0003}")" > /dev/null 2>&1
  sleep 2
done
adb shell dumpsys location > "$OUT/location-background.txt" 2>/dev/null
adb shell am start -W -n $PKG/.MainActivity > /dev/null; sleep 3
tap_record; sleep 2
shot 8a-recorded
# Debug builds: check the file itself has positions.
F=$(adb shell run-as $PKG ls files/logs 2>/dev/null | tr -d '\r' | grep csv | tail -1)
if [ -n "$F" ]; then
  adb exec-out run-as $PKG cat "files/logs/$F" > "$OUT/recorded.csv"
  head -1 "$OUT/recorded.csv" | grep -q "Latitude" && awk -F, 'NR>1 && $(NF-2) != "" {n++} END {exit !(n > 0)}' "$OUT/recorded.csv" && ROUTE=1
fi
[ $ROUTE = 1 ] && log "route: saved with the recording" || { log "route: FAILED (not saved)"; FAIL=1; }
if [ -s "$OUT/recorded.csv" ]; then
  BG=$(awk -F, 'NR>1 && $(NF-2) != "" && $(NF-2)+0 < -33.8790 {n++} END {print n+0}' "$OUT/recorded.csv")
  [ "$BG" -ge 5 ] && log "route while minimised: kept ($BG rows)" || { log "route while minimised: FAILED (only $BG rows with a position)"; FAIL=1; }
fi
tap_tab Dashboard; scroll_top; XY=$(view_center connectButton); [ -n "$XY" ] && adb shell input tap $XY; sleep 3
# The drive card for this drive should draw the route that was just recorded.
adb logcat -c
if tap_text "Share as a picture"; then
  sleep 3; shot 8a2-card-with-route
  PTS=$(adb logcat -d -s OBD2Share:I 2>/dev/null | grep -o "route of [0-9]* points" | grep -o "[0-9]*" | tail -1)
  [ "${PTS:-0}" -ge 2 ] && log "drive card: route outline ($PTS points)" || { log "drive card: FAILED (no route outline, ${PTS:-0} points)"; FAIL=1; }
  tap_text "Cancel" > /dev/null || adb shell input keyevent KEYCODE_BACK; sleep 1
else
  log "drive card: FAILED (no trip summary after the recorded drive)"; FAIL=1
fi
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
    # Android can take a moment to finish writing it to Movies: try a few times.
    for k in 1 2 3 4 5; do SZ=$(pull_made overlay.mp4 Movies "$OUT/overlay.mp4"); [ -n "$SZ" ] && [ "$SZ" -gt 10000 ] && break; sleep 2; done; SZ=${SZ:-0}
    # Android 8 and 9 release builds keep the video private until it's shared; "Video ready" is the check there.
    [ "$SZ" = 0 ] && [ "$API" -lt 29 ] && ! adb shell run-as $PKG true 2>/dev/null && SZ=99999
    command -v ffmpeg > /dev/null && ffmpeg -loglevel error -y -ss 12 -i "$OUT/overlay.mp4" -frames:v 1 "$OUT/8-video-frame.png"
    # The route's mini-map sits above the trace panel, on the right: there, the frame isn't green.
    if [ -s "$OUT/8-video-frame.png" ]; then
      MAP=$(python3 - "$OUT/8-video-frame.png" <<'PY'
import sys, zlib, struct
d = open(sys.argv[1], 'rb').read(); i = 8; idat = b''; w = h = ctype = 0
while i < len(d):
    n = struct.unpack('>I', d[i:i+4])[0]; t = d[i+4:i+8]; body = d[i+8:i+8+n]
    if t == b'IHDR': w, h, _, ctype = struct.unpack('>IIBB', body[:10])
    if t == b'IDAT': idat += body
    i += 12 + n
bpp = {2: 3, 6: 4}.get(ctype, 3); raw = zlib.decompress(idat); stride = w * bpp; prev = bytearray(stride); rows = []
p = 0
for y in range(h):
    f = raw[p]; line = bytearray(raw[p+1:p+1+stride]); p += 1 + stride
    for x in range(stride):
        a = line[x-bpp] if x >= bpp else 0; b = prev[x]; c = prev[x-bpp] if x >= bpp else 0
        if f == 1: line[x] = (line[x] + a) & 255
        elif f == 2: line[x] = (line[x] + b) & 255
        elif f == 3: line[x] = (line[x] + (a + b) // 2) & 255
        elif f == 4:
            pa, pb, pc = abs(b - c), abs(a - c), abs(a + b - 2 * c)
            line[x] = (line[x] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
    rows.append(line); prev = line
k = w / 1920.0; x0, x1, y0, y1 = int(1580 * k), int(1840 * k), int(520 * k), int(780 * k)
n = green = 0
for y in range(y0, y1, 4):
    for x in range(x0, x1, 4):
        r, g, b = rows[y][x*bpp], rows[y][x*bpp+1], rows[y][x*bpp+2]; n += 1
        if g > 180 and r < 90 and b < 90: green += 1
print(int(100 * (n - green) / max(n, 1)))
PY
)
      [ "${MAP:-0}" -ge 60 ] && log "video overlay: route mini-map shown" || { log "video overlay: FAILED (no mini-map, ${MAP:-0}% covered)"; FAIL=1; }
    fi
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
# Tried twice: on a busy emulator the demo car is sometimes still connecting the first time.
SCAN=missing
for attempt in 1 2; do
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
    adb shell input keyevent KEYCODE_BACK; sleep 2
    [ $SEEN = 1 ] && { SCAN=shown; break; } || SCAN="not shown"
  fi
done
case "$SCAN" in shown) log "car scan: shown" ;; missing) log "car scan: FAILED (row missing)"; FAIL=1 ;; *) log "car scan: FAILED (not shown)"; FAIL=1 ;; esac
# Deeper checks (read-only): readiness monitors, self-test results and other modules' codes,
# each read from the demo car. The app logs what it read under OBD2Deep.
deep_check() {   # $1 row title, $2 name for the log, $3 pattern the OBD2Deep log must match
  local ok=0
  for attempt in 1 2; do
    tap_tab Diagnostics; sleep 2
    for k in 1 2 3; do adb shell input swipe 540 2000 540 600 150; done; sleep 1
    adb logcat -c
    if tap_scrolled "$1"; then
      for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15; do
        adb logcat -d -s OBD2Deep:I > "$OUT/deep-$2.txt" 2>/dev/null
        grep -Eq "$3" "$OUT/deep-$2.txt" && { ok=1; break; }; sleep 2
      done
      sleep 2; shot "13-$2"
      adb shell input keyevent KEYCODE_BACK; sleep 2
      [ $ok = 1 ] && break
    fi
  done
  if [ $ok = 1 ]; then log "$2: $(grep -Eo "$3.*" "$OUT/deep-$2.txt" | head -1 | cut -c1-120)"; else log "$2: FAILED ($(tail -1 "$OUT/deep-$2.txt" 2>/dev/null | cut -c1-120))"; FAIL=1; fi
}
deep_check "Readiness monitors" readiness "readiness: .*(complete|not complete)"
deep_check "Self-test results" self-tests "self-tests: [1-9]"
deep_check "Codes from other modules" modules "modules: .*ABS and stability control=C1145-1C"
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
# Swiping sideways across the screen moves between tabs: Dashboard → Graphs, then back.
tap_tab Dashboard > /dev/null; sleep 2; adb logcat -c
adb shell input swipe 980 1300 100 1320 250; sleep 2
shot 14-swiped-to-graphs
adb shell input swipe 100 1300 980 1320 250; sleep 2
SW=$(adb logcat -d -s OBD2Swipe:I 2>/dev/null | grep -o "swiped to tab [0-9]" | tr '\n' ' ')
case "$SW" in *"tab 1"*"tab 0"*) log "swipe between tabs: OK" ;; *) log "swipe between tabs: FAILED (${SW:-no swipe seen})"; FAIL=1 ;; esac
# Open on screen, the phone doesn't lock, connected or not.
adb shell am start -W -n $PKG/.MainActivity > /dev/null; sleep 3
if adb shell dumpsys window windows 2>/dev/null | grep -A12 "Window{.*$PKG/$PKG.MainActivity" | grep -q "KEEP_SCREEN_ON"; then log "screen stays on while open: OK"; else log "screen stays on while open: FAILED"; FAIL=1; fi
# Minimised (Home), the app keeps running in the background.
tap_text "Demo car" > /dev/null || true; sleep 6
adb shell input keyevent KEYCODE_HOME; sleep 15
alive && log "minimised: still running" || { log "minimised: FAILED (app stopped)"; FAIL=1; }
# Swiped away from Recents: the app stops its work.
adb logcat -c
adb shell input keyevent KEYCODE_APP_SWITCH; sleep 3
shot 12-recents
adb shell input swipe 540 1300 540 150 250; sleep 4
shot 12b-recents-after
if adb shell dumpsys activity recents 2>/dev/null | grep -q "A=.*$PKG\|realActivity=$PKG"; then
  log "closed from Recents: not checked (couldn't swipe the app away on this emulator)"
elif adb logcat -d -s OBD2Close:I 2>/dev/null | grep -q "closed: stopping"; then
  log "closed from Recents: stopped"
elif ! alive; then
  # The demo runs without the background service, so Android may end the whole app at once.
  log "closed from Recents: stopped (Android ended the app)"
else
  log "closed from Recents: FAILED (app didn't stop its work)"; FAIL=1
fi
adb shell input keyevent KEYCODE_HOME
adb logcat -d > "$OUT/logcat.txt"
if grep -q "FATAL EXCEPTION" "$OUT/logcat.txt" && grep -A3 "FATAL EXCEPTION" "$OUT/logcat.txt" | grep -q "$PKG"; then
  log "crashes: FOUND"; grep -A20 "FATAL EXCEPTION" "$OUT/logcat.txt" > "$OUT/crash.txt"; exit 1
fi
log "crashes: none"
exit $FAIL
