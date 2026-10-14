#!/usr/bin/env bash
set -euo pipefail
mkdir -p docs/visual-qa
APP=uy.com.mapatrayectos
adb shell wm size 1080x2400
adb shell wm density 440
adb install -r mapatrayectos/build/outputs/apk/debug/mapatrayectos-debug.apk
adb shell pm grant "$APP" android.permission.ACCESS_FINE_LOCATION
adb shell pm grant "$APP" android.permission.ACCESS_COARSE_LOCATION
adb shell pm grant "$APP" android.permission.POST_NOTIFICATIONS
adb shell appops set "$APP" SYSTEM_ALERT_WINDOW allow || true
adb emu geo fix -56.084 -34.880 || true

# The Pixel 6 Launcher repeatedly throws an ANR on a busy CI emulator.
# This process is not used by our app; disable it on this test device only.
adb shell pm disable-user --user 0 com.google.android.apps.nexuslauncher || true
adb shell am start -n "$APP/.MainActivity"
sleep 14

capture_window() {
  adb exec-out screencap -p > "$1"
  test -s "$1"
}
is_modal() {
  python3 - "$1" <<'PY'
import sys
from PIL import Image
im=Image.open(sys.argv[1]).convert('RGB')
w,h=im.size
# Center is map/panel (deep petroleum), never a light system dialog.
rgb=im.getpixel((w//2,h//2))
print("Screenshot center pixel:", rgb)
sys.exit(0 if sum(rgb)/3 > 160 else 1)
PY
}
dismiss_modal() {
  adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
  adb exec-out cat /sdcard/window.xml > docs/visual-qa/last-window.xml || true
  local target
  target="$(python3 - <<'PY'
import xml.etree.ElementTree as ET
import re
try:
    root=ET.parse('docs/visual-qa/last-window.xml').getroot()
    texts=[(n.get('text',''),n.get('bounds','')) for n in root.iter()]
    print("Visible popup texts:",str([t[0] for t in texts if t[0]][-16:]),file=__import__('sys').stderr)
    for text,bounds in texts:
        if text.lower() in ('close app','cerrar app','cerrar aplicación','close','ok'):
            b=list(map(int,re.findall(r'\d+',bounds)))
            if len(b)==4:
                print(f"{(b[0]+b[2])//2} {(b[1]+b[3])//2}")
                break
except Exception as e:
    print("Window XML parse:",e,file=__import__('sys').stderr)
PY
)"
  if [ -n "$target" ]; then
    adb shell input tap $target
  else
    adb shell input keyevent KEYCODE_BACK
  fi
  adb shell am force-stop com.google.android.apps.nexuslauncher || true
  adb shell am start -n "$APP/.MainActivity" || true
  sleep 5
}
for attempt in 1 2 3 4 5; do
  capture_window /tmp/mapa-window.png
  if ! is_modal /tmp/mapa-window.png; then break; fi
  echo "Detected system-level modal; dismissing attempt $attempt"
  dismiss_modal
done
capture_window docs/visual-qa/mapa-r25.15-idle.png
if is_modal docs/visual-qa/mapa-r25.15-idle.png; then
  echo "QA BLOCKED: light system dialog obscures the actual UI" >&2
  exit 1
fi

# Use the actual Android accessibility bounds of the custom slider, not
# fragile hard-coded locations or guesses about phone density.
adb shell uiautomator dump /sdcard/idle-ui.xml >/dev/null 2>&1 || true
adb exec-out cat /sdcard/idle-ui.xml > docs/visual-qa/idle-ui.xml
coords="$(python3 - <<'PY'
import xml.etree.ElementTree as ET,re
root=ET.parse('docs/visual-qa/idle-ui.xml').getroot()
for n in root.iter():
    label=n.get('content-desc','')+' '+n.get('text','')
    if 'INICIAR JORNADA' in label.upper() and n.get('bounds'):
        b=list(map(int,re.findall(r'\d+',n.get('bounds'))))
        print(b[0]+50,(b[1]+b[3])//2,b[2]-35,(b[1]+b[3])//2)
        break
else:
    raise SystemExit('Slider de iniciar jornada NO visible en jerarquía Android')
PY
)"
echo "Start journey swipe: $coords"
adb shell input swipe $coords 1350
sleep 11
capture_window docs/visual-qa/mapa-r25.15-shift-active.png
if is_modal docs/visual-qa/mapa-r25.15-shift-active.png; then
  echo "QA BLOCKED: modal obscured the active shift" >&2
  exit 1
fi
adb shell uiautomator dump /sdcard/active-ui.xml >/dev/null 2>&1 || true
adb exec-out cat /sdcard/active-ui.xml > docs/visual-qa/active-ui.xml
# Fail closed if start/stop journey actions do not appear in the right states.
python3 - <<'PY'
import xml.etree.ElementTree as ET
def labels(path):
    root=ET.parse(path).getroot()
    return ' '.join(n.get('text','')+' '+n.get('content-desc','') for n in root.iter()).upper()
before=labels('docs/visual-qa/idle-ui.xml')
after=labels('docs/visual-qa/active-ui.xml')
assert 'INICIAR JORNADA' in before, 'Start action not accessible in idle'
assert 'FINALIZAR JORNADA' in after, 'End action missing after start; cannot approve release'
assert 'INICIAR JORNADA' not in after, 'Shift did not transition to active'
print('Verified journey start -> end CTA visually accessible')
PY
adb logcat -d -t 700 | grep -E 'FATAL EXCEPTION|AndroidRuntime:.*Process: uy.com.mapatrayectos' > docs/visual-qa/fatal-log.txt || true
test ! -s docs/visual-qa/fatal-log.txt
