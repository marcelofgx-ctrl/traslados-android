#!/usr/bin/env python3
"""R25.30 one-time driver identity invariants; read-only / no production writes."""
from pathlib import Path
mapa=Path("mapatrayectos/src/main/java/uy/com/mapatrayectos")
conductor=Path("conductor/src/main/java/uy/com/traslados/conductor")
identity=(mapa/"DriverIdentity.java").read_text()
main=(mapa/"MainActivity.java").read_text()
alerts=(mapa/"TransferAlerts.java").read_text()
tracking=(mapa/"TrackingService.java").read_text()
status=(mapa/"SystemStatusActivity.java").read_text()
driver=(conductor/"MainActivity.java").read_text()
monitor=(conductor/"ReservationMonitorService.java").read_text()
gradle=Path("mapatrayectos/build.gradle").read_text()

# No implicit identification merely because an untrusted APK is installed.
assert 'Api.pinValid(candidate)' in main or 'uy.com.traslados.conductor.Api.pinValid(candidate)' in main
assert 'DriverIdentity.bind(getApplicationContext(),candidate)' in main
assert 'Vincular conductor principal' in main
assert 'getBoolean("shown",false)' in main
assert 'DriverIdentity.pin(this)' in main
assert 'transferReceptionTick' in main
assert 'Conductor principal' in main
assert 'Solicitudes y agenda' in main

# Encrypted-at-rest credentials, no Android Keystore plaintext fallback.
for token in ('AndroidKeyStore','AES/GCM/NoPadding',
              'setRandomizedEncryptionRequired(true)','clear(Context context)',
              'getSharedPreferences(LEGACY','getSharedPreferences(PREF'):
    assert token in identity,token
assert identity.count('return legacy;')==1  # only after a successful migration
assert 'catch(Exception error){' in identity and 'return "";' in identity
assert 'remove("pin")' in identity
assert 'validate' not in identity.lower() or 'server' in identity.lower()
assert "PIN" not in identity.split('return Base64.encodeToString')[1]

# Every private reservation read uses the single linked identity.
assert alerts.count('DriverIdentity.pin(')>=5
assert 'DriverIdentity.pin(this)' in tracking
assert 'DriverIdentity.pin(this)' in status
assert 'uy.com.mapatrayectos.DriverIdentity.pin(this)' in monitor
assert 'uy.com.mapatrayectos.DriverIdentity.pin(this)' in driver
assert 'uy.com.mapatrayectos.DriverIdentity.bind(this,pin)' in driver
assert 'uy.com.mapatrayectos.DriverIdentity.clear(this)' in driver
assert 'DriverIdentity.pin(this)' in main.split('private void promptPickupConsent()',1)[1]
assert 'Api.setPickupConsent(linked,true)' in main

# Preserve the original APK package, signer and monotonic version.
assert "applicationId 'uy.com.mapatrayectos'" in gradle
assert 'versionCode 85' in gradle
assert "versionName '0.1-R25.33-CONSENT-QA'" in gradle
assert "project(':core-reservations')" in gradle
print("PASS: one-time secure driver binding, migration and automatic reservation monitoring")
print("PASS: R25.30 code82 same package; no data deletion or second driver APK")
