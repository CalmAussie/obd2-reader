#!/bin/bash
# Installs the published APK on a fresh emulator exactly as a user would get it, opens it,
# starts the demo car, visits every tab and checks nothing crashed. Saves screenshots.
set -u
API=$1; OUT=reports/api-$API; mkdir -p "$OUT"
PKG=com.obdreader.app
log() { echo "$*" | tee -a "$OUT/result.txt"; }

adb wait-for-device
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

shot 1-opened
alive && log "open: OK" || log "open: FAILED (app not running)"
tap_text "Demo car" && log "demo: tapped" || log "demo: button not found"
sleep 8
tap_text "Allow" > /dev/null || true
shot 2-demo-dashboard
tap_text "Graphs" && sleep 4 && shot 3-graphs || log "graphs tab not found"
tap_text "Diagnostics" && sleep 1 && tap_text "Read codes" && sleep 3 && shot 4-diagnostics || log "diagnostics tab not found"
tap_text "Dashboard" && sleep 1

alive && log "still running after tour: OK" || log "still running after tour: FAILED"
adb logcat -d > "$OUT/logcat.txt"
if grep -q "FATAL EXCEPTION" "$OUT/logcat.txt" && grep -A3 "FATAL EXCEPTION" "$OUT/logcat.txt" | grep -q "$PKG"; then
  log "crashes: FOUND"; grep -A20 "FATAL EXCEPTION" "$OUT/logcat.txt" > "$OUT/crash.txt"; exit 1
fi
log "crashes: none"
