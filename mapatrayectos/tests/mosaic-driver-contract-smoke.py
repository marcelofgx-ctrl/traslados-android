"""Mosaic Android architecture contract — no device/network/DB writes."""
from pathlib import Path
root=Path(__file__).resolve().parents[2]
def read(path): return (root/path).read_text(encoding="utf-8")
settings=read("settings.gradle")
mapa=read("mapatrayectos/build.gradle")
conductor=read("conductor/build.gradle")
feature=read("feature/driver/build.gradle")
host=read("mapatrayectos/src/main/java/uy/com/mapatrayectos/MapaHostApplication.java")
bridge=read("feature/driver/src/main/java/uy/com/traslados/conductor/DriverHostBridge.java")
monitor=read("feature/driver/src/main/java/uy/com/traslados/conductor/ReservationMonitorService.java")
activity=read("feature/driver/src/main/java/uy/com/traslados/conductor/MainActivity.java")
manifest=read("mapatrayectos/src/main/AndroidManifest.xml")
assert "include ':feature:driver'" in settings
assert "implementation project(':feature:driver')" in mapa
assert "implementation project(':feature:driver')" in conductor
assert "com.android.library" in feature
assert "src/main/java" in feature and "src/main/res" in feature
assert "conductor/src/main/java" not in feature
assert "conductor/src/main/res" not in feature
assert "prepareEmbeddedConductor" not in mapa
assert "replace('super.onResume();" not in mapa
assert "uy.com.mapatrayectos" not in bridge
assert "uy.com.mapatrayectos" not in monitor
# A package-id string for UI labeling is allowed; importing Mapa Java types is not.
assert "import uy.com.mapatrayectos." not in activity
assert "uy.com.mapatrayectos.TransferAlerts" not in activity
assert "uy.com.mapatrayectos.TrackingService" not in activity
assert "DriverHostBridge.install(this)" in host
assert 'android:name=".MapaHostApplication"' in manifest
assert "DriverHostBridge.onPendingSnapshot(this,a)" in monitor
assert "DriverHostBridge.onNewRequest(this,r)" in monitor
assert "DriverHostBridge.onDriverVisible(this)" in activity
assert "DriverHostBridge.onDriverHidden(this)" in activity
assert "if(!DriverHostBridge.isEmbedded())driverBubbleStartLoop();" in monitor
assert "if(DriverHostBridge.isEmbedded())return;" in monitor
assert "ConductorAppTheme" in manifest and "ConductorAppTheme" in read("feature/driver/src/main/res/values/styles.xml")
assert not (root/"conductor/src/main/java/uy/com/traslados/conductor/MainActivity.java").exists()
for filename in ("MainActivity.java","Api.java","DemandMapActivity.java",
                 "TripTelemetryService.java","ReservationMonitorService.java"):
    assert (root/"feature/driver/src/main/java/uy/com/traslados/conductor"/filename).is_file()
for filename in ("conductor_app_icon.png","conductor_bg_texture.png"):
    assert (root/"feature/driver/src/main/res/drawable-nodpi"/filename).is_file()
for filename in ("reserva_calida.wav","viaje_proximo.wav"):
    assert (root/"feature/driver/src/main/res/raw"/filename).is_file()
assert (root/"conductor/src/bootstrap/res/drawable-nodpi/app_icon.png").is_file()
assert (root/"feature/driver/src/main/AndroidManifest.xml").is_file()
print("PASS Mosaico: Android library owns driver Java/assets; bridge isolated; Mapa owns one bubble; standalone backup intact")
