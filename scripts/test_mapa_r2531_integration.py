#!/usr/bin/env python3
"""R25.31 integration contract: single installable Mapa, both branches preserved."""
from pathlib import Path
p=Path("mapatrayectos")
g=(p/"build.gradle").read_text()
a=(p/"src/main/java/uy/com/mapatrayectos/TransferAlerts.java").read_text()
i=(p/"src/main/java/uy/com/mapatrayectos/DriverIdentity.java").read_text()
c=Path("conductor/src/main/java/uy/com/traslados/conductor/MainActivity.java").read_text()
m=Path("conductor/src/main/java/uy/com/traslados/conductor/ReservationMonitorService.java").read_text()
w=Path(".github/workflows/validate-modular-mapa.yml").read_text()
assert "applicationId 'uy.com.mapatrayectos'" in g and "versionCode 84" in g
assert "versionName '0.1-R25.32-PRESENCE-QA'" in g
assert 'ReservationQuoteLabel.title' in c
assert 'TransferAlerts.invalidateLocalSession(app)' in i
assert 'DriverIdentity.pin(c)' in a
assert 'notifyNew(r,pin)' in m
assert 'ReservationQuoteLabelTest' in w
assert 'test_mapa_session_privacy.py' in w
assert "include ':mapatrayectos'" in Path("settings.gradle").read_text()
assert "include ':conductor'" not in Path("settings.gradle").read_text()
print("PASS R25.32-PRESENCE QA: R25.31 STATUS+MON+AUTH preserved inside one APK")
