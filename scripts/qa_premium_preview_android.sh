#!/usr/bin/env bash
set -eu
sleep 15
test_result=0
gradle -p cliente-premium :app:connectedDebugAndroidTest --no-daemon --stacktrace || test_result=$?
# AGP uninstalls both APKs after instrumentation; reinstall for the screenshot.
adb install -r cliente-premium/app/build/outputs/apk/debug/app-debug.apk
adb shell am start -W -n uy.com.traslados.cliente.debug/uy.com.traslados.cliente.MainActivity
sleep 3
adb exec-out screencap -p > android-pr99-webview.png
adb logcat -d > android-pr99-logcat.txt
# Some emulator WebViews do not expose an accessibility root immediately.
# The native assertions above determine PASS/FAIL; retain the screenshot either way.
if adb shell uiautomator dump /sdcard/pr99-ui.xml && adb pull /sdcard/pr99-ui.xml android-pr99-ui.xml; then
  true
else
  printf '%s\n' 'Accessibility dump unavailable; see native tests, screenshot and logcat.' > android-pr99-ui-note.txt
fi
exit "$test_result"
