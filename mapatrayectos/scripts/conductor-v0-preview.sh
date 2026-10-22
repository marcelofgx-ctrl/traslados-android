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
adb exec-out screencap -p > "$OUT/conductor-v0-android.png"
test -s "$OUT/conductor-v0-android.png"
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
