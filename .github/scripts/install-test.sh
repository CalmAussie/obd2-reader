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
tap_tab Diagnostics && tap_text "Read codes" && sleep 3 && shot 4-diagnostics && log "diagnostics: OK" || { log "diagnostics: FAILED"; FAIL=1; }
tap_tab Graphs && sleep 4 && shot 3-graphs && log "graphs: OK" || { log "graphs: FAILED"; FAIL=1; }
adb shell input keyevent KEYCODE_BACK; sleep 1

alive && log "still running after tour: OK" || log "still running after tour: FAILED"
adb logcat -d > "$OUT/logcat.txt"
if grep -q "FATAL EXCEPTION" "$OUT/logcat.txt" && grep -A3 "FATAL EXCEPTION" "$OUT/logcat.txt" | grep -q "$PKG"; then
  log "crashes: FOUND"; grep -A20 "FATAL EXCEPTION" "$OUT/logcat.txt" > "$OUT/crash.txt"; exit 1
fi
log "crashes: none"
exit $FAIL
