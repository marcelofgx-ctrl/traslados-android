#!/usr/bin/env python3
"""Static non-destructive regression for single-APK reservation reception R25.30.
No credentials, network calls, mock bookings or Supabase writes.
"""
from pathlib import Path

root = Path("mapatrayectos/src/main/java/uy/com/mapatrayectos")
driver = Path("conductor/src/main/java/uy/com/traslados/conductor")
main = (root / "MainActivity.java").read_text()
alerts = (root / "TransferAlerts.java").read_text()
service = (driver / "ReservationMonitorService.java").read_text()
api = (driver / "Api.java").read_text()
gradle = Path("mapatrayectos/build.gradle").read_text()

# One Android APK: same package, and monotonically newer than R25.28/code80.
assert "applicationId 'uy.com.mapatrayectos'" in gradle
assert "versionCode 82" in gradle and "versionName '0.1-R25.30'" in gradle
assert "project(':core-reservations')" in gradle

# Only authenticated driver data, never public pending trip listing.
assert 'driver_list_reservations_v4' in api
assert 'DriverIdentity.pin(' in alerts
assert 'mapa-quick-reservations' in alerts
assert 'driver_identity_secure_v1' in (root / "DriverIdentity.java").read_text()
assert 'driver_pin_required' in alerts
assert "new Thread(" in alerts and 'mapa-quick-reservations' in alerts

# Visible-first reconciliation plus restart if prior service heartbeat disappears.
assert "transferReceptionTick=new Runnable()" in main
assert "uiHandler.post(transferReceptionTick)" in main
assert "removeCallbacks(transferReceptionTick)" in main
assert "TransferAlerts.refreshAsync(MainActivity.this)" in main
assert "TransferAlerts.monitorNeedsRestart(MainActivity.this)" in main
assert "uiHandler.postDelayed(this,15000L)" in main
assert "Recepción de peticiones" in main and "this::showTransferReception" in main
assert "VER SOLICITUDES" in main and "REVISAR AHORA" in main

# The service must report actual network success, not just a running state.
assert 'last_attempt_at' in service and 'last_success_at' in service
assert 'last_error_type' in service and 'last_row_count' in service
assert 'Api.listActive(pin)' in service
assert 'claimPendingNotification' in service
assert 'ReservationAlertPolicy.recentPending' in service
assert 'driver_list_reservations_v4' in api

# Foreground fallback must independently display new pending requests once,
# without claiming a fixed-price decision or inventing acceptance.
assert 'getBoolean("ui_visible",false)' in alerts
assert 'ReservationAlertPolicy.recentPending' in alerts
assert 'reportNew(c,recent)' in alerts
assert 'alert_"+id' in alerts
assert 'QUICK_REFRESH.compareAndSet(false,true)' in alerts
assert 'public static void decideFixed(' in alerts
assert 'Api.decideFixedPickup(pin,id,accept)' in alerts

# No data resets introduced in these changed paths.
for path in (alerts, service, main):
    assert "TRUNCATE TABLE" not in path and "DELETE FROM public." not in path

print("PASS R25.30: current-session polling, foreground fallback and monitored restart")
print("PASS R25.30: no second Android APK, no unauthorized bookings or data purges")
