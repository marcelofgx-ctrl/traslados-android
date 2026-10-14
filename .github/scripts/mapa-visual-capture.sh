#!/usr/bin/env bash
set -euo pipefail
mkdir -p docs/visual-qa
# Do not mistake an old source-controlled image for a screenshot from this run.
rm -f docs/visual-qa/mapa-preview-idle.png docs/visual-qa/mapa-preview-shift-active.png docs/visual-qa/mapa-preview-shift-close-expanded.png
APP=uy.com.mapatrayectos
adb shell wm size 1080x2400
adb shell wm density 440
adb install -r mapatrayectos/build/outputs/apk/debug/mapatrayectos-debug.apk
adb shell pm grant "$APP" android.permission.ACCESS_FINE_LOCATION
adb shell pm grant "$APP" android.permission.ACCESS_COARSE_LOCATION
adb shell pm grant "$APP" android.permission.POST_NOTIFICATIONS
adb shell appops set "$APP" SYSTEM_ALERT_WINDOW allow || true
adb emu geo fix -56.084 -34.880 || true

# Keep the Android home launcher enabled. Disabling com.android.launcher3
# caused recurring system dialogs that covered the map during QA.
adb shell pm list packages | grep -Ei '(launcher|quickstep)' || true
adb shell am start -n "$APP/.MainActivity"
sleep 14

capture_window() {
  adb exec-out screencap -p > "$1"
  test -s "$1"
}
is_modal() {
  # A bright road, marker or route may occupy the center pixel. Use the
  # ACTUAL Android view hierarchy, not a single RGB value, to reject popups.
  adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
  adb exec-out cat /sdcard/window.xml > docs/visual-qa/last-window.xml 2>/dev/null || true
  python3 - <<'PY'
import sys
import xml.etree.ElementTree as ET
try:
    root=ET.parse('docs/visual-qa/last-window.xml').getroot()
except (ET.ParseError, OSError):
    print('QA: Android UI hierarchy unavailable; treat screen as blocked')
    sys.exit(0)
texts=[(n.get('text','')+' '+n.get('content-desc','')).strip() for n in root.iter()]
visible=' '.join(texts).lower()
errors=('not responding', "isn't responding", 'keeps stopping',
        'se detuvo', 'no responde', 'cerrar app', 'close app',
        'activar acceso flotante')
if any(error in visible for error in errors):
    print('QA: visible Android error/permission modal')
    sys.exit(0)
app_on_screen='mapa trayectos' in visible and (
    'iniciar jornada' in visible or 'jornada activa' in visible)
if app_on_screen:
    print('QA: Mapa and jornada controls visible; no blocking modal')
    sys.exit(1)
print('QA: expected Mapa controls not visible; block capture')
sys.exit(0)
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
  # Do not kill Launcher3: killing it causes a repeated 'not responding' popup.
  adb shell am start -n "$APP/.MainActivity" || true
  sleep 5
}
for attempt in 1 2 3 4 5; do
  capture_window /tmp/mapa-window.png
  if ! is_modal /tmp/mapa-window.png; then break; fi
  echo "Detected system-level modal; dismissing attempt $attempt"
  dismiss_modal
done
capture_window docs/visual-qa/mapa-preview-idle.png
if is_modal docs/visual-qa/mapa-preview-idle.png; then
  echo "QA BLOCKED: Android hierarchy shows a blocking modal or missing app controls" >&2
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
capture_window docs/visual-qa/mapa-preview-shift-active.png
if is_modal docs/visual-qa/mapa-preview-shift-active.png; then
  echo "QA BLOCKED: modal obscured the active shift" >&2
  exit 1
fi
# Persisted state is the authoritative evidence of the gesture transition.
# UIAutomator occasionally fails its SECOND dump on emulator API35 while
# the screen and app remain functional; do not confuse that with an app failure.
adb shell run-as "$APP" cat shared_prefs/tracking_state.xml > docs/visual-qa/state-after-start.xml
test -s docs/visual-qa/state-after-start.xml
python3 - <<'PY'
import xml.etree.ElementTree as ET
idle=ET.parse('docs/visual-qa/idle-ui.xml').getroot()
before=' '.join(n.get('text','')+' '+n.get('content-desc','') for n in idle.iter()).upper()
assert 'INICIAR JORNADA' in before, 'No se encontró el deslizador de iniciar jornada'
state=ET.parse('docs/visual-qa/state-after-start.xml').getroot()
active=[n for n in state.iter('boolean') if n.get('name')=='shift_active']
assert active and active[0].get('value')=='true', 'Deslizamiento no inició jornada'
print('Verified jornada: inicial INICIAR JORNADA -> shift_active true')
PY
adb shell uiautomator dump /sdcard/active-ui.xml >/dev/null 2>&1 || true
adb exec-out cat /sdcard/active-ui.xml > docs/visual-qa/active-ui.xml 2>/dev/null || true

# R25.20: UI test of the real circular opener and secondary reverse slider.
# A simple tap must NEVER close the journey. Read its physical touch area from
# accessibility, not from coordinates hardcoded to a specific Samsung model.
orb_coords=""
for attempt in 1 2 3 4; do
  adb shell uiautomator dump /sdcard/shift-orb.xml >/dev/null 2>&1 || true
  adb exec-out cat /sdcard/shift-orb.xml > docs/visual-qa/orb-ui.xml 2>/dev/null || true
  orb_coords="$(python3 - <<'PY'
import xml.etree.ElementTree as ET,re
try:
    root=ET.parse('docs/visual-qa/orb-ui.xml').getroot()
    for node in root.iter():
        if 'Desplegar el deslizador para finalizar jornada' in node.get('content-desc',''):
            vals=list(map(int,re.findall(r'\d+',node.get('bounds',''))))
            if len(vals)==4:
                print((vals[0]+vals[2])//2,(vals[1]+vals[3])//2)
                break
except (ET.ParseError,OSError):
    pass
PY
)"
  if [ -n "$orb_coords" ]; then break; fi
  sleep 3
done
test -n "$orb_coords" || { echo "QA: no apareció el círculo desplegable" >&2; exit 1; }
echo "QA: expanding jornada circle at $orb_coords"
adb shell input tap $orb_coords
sleep 3
capture_window docs/visual-qa/mapa-preview-shift-close-expanded.png
if is_modal docs/visual-qa/mapa-preview-shift-close-expanded.png; then
  echo "QA BLOCKED: no controls after circular expansion" >&2
  exit 1
fi
adb shell run-as "$APP" cat shared_prefs/tracking_state.xml > docs/visual-qa/state-after-orb.xml
adb shell uiautomator dump /sdcard/expanded-ui.xml >/dev/null 2>&1 || true
adb exec-out cat /sdcard/expanded-ui.xml > docs/visual-qa/expanded-ui.xml 2>/dev/null || true
python3 - <<'PY'
import xml.etree.ElementTree as ET
root=ET.parse('docs/visual-qa/state-after-orb.xml').getroot()
active=[n for n in root.iter('boolean') if n.get('name')=='shift_active']
assert active and active[0].get('value')=='true','Tocar círculo finalizó jornada incorrectamente'
ui=ET.parse('docs/visual-qa/expanded-ui.xml').getroot()
labels=' '.join((n.get('text','')+' '+n.get('content-desc','')) for n in ui.iter())
assert 'DESLIZAR ← FINALIZAR JORNADA' in labels,'Falta el deslizador inverso luego de tocar círculo'
print('Verified: círculo expande deslizador de fin, jornada sigue activa')
PY
adb logcat -d -t 700 | grep -E 'FATAL EXCEPTION|AndroidRuntime:.*Process: uy.com.mapatrayectos' > docs/visual-qa/fatal-log.txt || true
test ! -s docs/visual-qa/fatal-log.txt
