#!/usr/bin/env bash
set -euo pipefail
OUT="docs/visual-qa/conductor-v0"
mkdir -p "$OUT"
APK="mapatrayectos/build/outputs/apk/debug/mapatrayectos-debug.apk"
test -s "$APK"
adb wait-for-device
adb shell input keyevent 82 || true
adb install -r "$APK"
adb shell am force-stop uy.com.mapatrayectos || true
adb shell am start -W -n "uy.com.mapatrayectos/uy.com.mapatrayectos.conductorv0.ConductorV0PreviewActivity"
sleep 3
# On cold Android emulator boot, Quickstep (system launcher) occasionally
# shows its own ANR sheet on TOP of the healthy preview Activity.
# Inspect pixels; tap the system dialog's Wait action and retry. This is
# an emulator-only workaround, never application code.
for attempt in 1 2 3 4 5; do
  adb exec-out screencap -p > "$OUT/conductor-v0-android.png"
  test -s "$OUT/conductor-v0-android.png"
  if python3 - "$OUT/conductor-v0-android.png" <<'PY'
from PIL import Image
import sys
im=Image.open(sys.argv[1]).convert('RGB')
w,h=im.size
area=im.crop((int(w*.10),int(h*.31),int(w*.90),int(h*.48)))
px=list(area.getdata())
white=sum(1 for r,g,b in px if min(r,g,b)>216)
ratio=white/max(1,len(px))
print('System modal light-pixel ratio:',round(ratio,3))
sys.exit(0 if ratio>.19 else 1)
PY
  then
    echo "Dismiss emulator-only Quickstep ANR by selecting Wait (attempt $attempt)"
    adb shell input tap 285 1010
    sleep 2
  else
    break
  fi
done
adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1
adb exec-out cat /sdcard/window.xml > "$OUT/conductor-v0-ui.xml"
python3 - <<'PY'
import xml.etree.ElementTree as ET
from pathlib import Path
p=Path("docs/visual-qa/conductor-v0/conductor-v0-ui.xml")
txt=" ".join(n.get("text","") for n in ET.parse(p).getroot().iter())
required=["QUICK TRIP","Conductor","Jornada activa","Nuevas","Aceptadas",
          "En viaje","Nueva solicitud","18 de Julio, Montevideo",
          "Aeropuerto de Carrasco","21 km","30 min","$840","Rechazar","Aceptar"]
missing=[s for s in required if s not in txt]
assert not missing,("Missing elements in Android native view:",missing)
assert "PIN" not in txt
assert "Conductor sin vincular" not in txt
print("PASS native UI text, original image sample, and no PIN:",len(required),"elements")
PY
