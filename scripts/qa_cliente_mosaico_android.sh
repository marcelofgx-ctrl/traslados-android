#!/usr/bin/env bash
set -euo pipefail
# One emulator boot for BOTH security modes; no live reservation creation.
# All tests use the existing native PR99 instrumentation suites.
sleep 12
mkdir -p qa-cliente-mosaico
python3 - <<'PY'
from pathlib import Path
p=Path('cliente-premium/app/build.gradle')
g=p.read_text()
if "testInstrumentationRunner 'android.test.InstrumentationTestRunner'" not in g:
    g=g.replace("defaultConfig {", "defaultConfig {\n        testInstrumentationRunner 'android.test.InstrumentationTestRunner'")
if "useLibrary 'android.test.runner'" not in g:
    g=g.replace("compileSdk 35", "compileSdk 35\n    useLibrary 'android.test.runner'\n    useLibrary 'android.test.base'")
p.write_text(g)
manifest=Path('cliente-premium/app/src/androidTest/AndroidManifest.xml')
manifest.parent.mkdir(parents=True,exist_ok=True)
manifest.write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android"><application><uses-library android:name="android.test.runner"/><uses-library android:name="android.test.base"/></application></manifest>')
PY
status=0
for mode in isolated operational; do
    if [[ "$mode" = operational ]]; then flag=--operational; testClass=OperationalJourneyTest; else flag=""; testClass=PremiumPreviewTest; fi
    echo "===== WebView MOSAIC $mode ====="
    python3 scripts/prepare_premium_preview.py ${flag}
    mkdir -p cliente-premium/app/src/main/res/drawable-nodpi
    cp web-pasajero/icons/icon-512.png cliente-premium/app/src/main/res/drawable-nodpi/traslados_brand.png
    mkdir -p cliente-premium/app/src/androidTest/java/uy/com/traslados/cliente
    rm -f cliente-premium/app/src/androidTest/java/uy/com/traslados/cliente/{PremiumPreviewTest,OperationalJourneyTest}.java
    cp "cliente-premium/preview-tests/${testClass}.java" cliente-premium/app/src/androidTest/java/uy/com/traslados/cliente/
    adb uninstall uy.com.traslados.cliente.debug >/dev/null 2>&1 || true
    started=$SECONDS
    if gradle -p cliente-premium :app:connectedDebugAndroidTest --build-cache --no-daemon --console=plain; then
        echo "PASS $mode" | tee "qa-cliente-mosaico/$mode-summary.txt"
    else
        echo "FAIL $mode" | tee "qa-cliente-mosaico/$mode-summary.txt"
        status=1
    fi
    printf '\nElapsed: %ss\n' "$((SECONDS-started))" >> "qa-cliente-mosaico/$mode-summary.txt"
    cp -a cliente-premium/app/build/outputs/androidTest-results "qa-cliente-mosaico/$mode-results" 2>/dev/null || true
    cp -a cliente-premium/app/build/reports/androidTests "qa-cliente-mosaico/$mode-reports" 2>/dev/null || true
done
adb install -r cliente-premium/app/build/outputs/apk/debug/app-debug.apk || true
adb shell am start -W -n uy.com.traslados.cliente.debug/uy.com.traslados.cliente.MainActivity || true
adb exec-out screencap -p > qa-cliente-mosaico/operational-screenshot.png || true
adb logcat -d -t 1400 > qa-cliente-mosaico/logcat-last.txt || true
cat qa-cliente-mosaico/*-summary.txt
exit "$status"
