#!/usr/bin/env python3
"""Static invariant gate for integrated Mapa Testing QA, no external calls."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

root=Path(__file__).resolve().parents[1]
build=(root/"mapatrayectos/build.gradle").read_text()
api=(root/"mapatrayectos/src/main/java/uy/com/mapatrayectos/Api.java").read_text()
tracking=(root/"mapatrayectos/src/main/java/uy/com/mapatrayectos/TrackingService.java").read_text()
manifest=(root/"mapatrayectos/src/qa/AndroidManifest.xml").read_text()
base_manifest=(root/"mapatrayectos/src/main/AndroidManifest.xml").read_text()
web=(root/"web-pasajero/index.html").read_text()
live=(root/"web-pasajero/reservation-live.js").read_text()

assert "applicationId 'uy.com.mapatrayectos'" in build
assert "applicationIdSuffix '.qa'" in build
assert "versionNameSuffix '-MAPA-TESTING'" in build
assert 'android:label="Mapa Testing"' in manifest
assert 'tools:node="replace"' not in manifest
assert 'android:allowBackup="false"' in manifest
assert '@drawable/mapa_testing_icon' in manifest
ET.fromstring(manifest)
assert "uy.com.traslados.conductor.MainActivity" in base_manifest
assert "uy.com.traslados.conductor.ReservationMonitorService" in base_manifest
assert 'android:name=".TrackingService"' in base_manifest
assert "uy.com.mapatrayectos.MainActivity" not in manifest  # never replace launcher
assert "LiveQaActivity" not in manifest
assert "mapa_reservation_position_v1" in api
assert 'APP.getPackageName().equals("uy.com.mapatrayectos.qa")' in api
assert 'getPackageName().equals("uy.com.mapatrayectos.qa")' in tracking
assert '!pickupPresenceEnabled()' in api
assert 'RESERVATION_GPS_BUSY' in api
assert "gpsAtMs>now+10000L" in api and "now-gpsAtMs>12000L" in api
assert 'Api.publishPickupPresenceAsync(' in tracking
assert 'Api.publishReservationPositionQaAsync(' in tracking
assert "customer_reservation_position_v1" in live
assert 'data-qt-contract="rc1"' in web
assert 'quicktrip-rc1-ui.js' in web and 'quicktrip-rc1.css' in web
for banned in ('quicktrip-qa-stage-b-v90.js','quicktrip-qa-route-v90.js','quicktrip-live-console-qa.js'):
    assert banned not in web
print("PASS: Mapa Testing retains Mapa+Conductor; GPS additional writes QA-only with explicit consent; RC1 web is real-engine")
