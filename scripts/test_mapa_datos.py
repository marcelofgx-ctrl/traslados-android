#!/usr/bin/env python3
"""Static guardrails for non-destructive Mapa Datos review."""
from pathlib import Path

main=Path("mapatrayectos/src/main/java/uy/com/mapatrayectos/MainActivity.java").read_text()
api=Path("mapatrayectos/src/main/java/uy/com/mapatrayectos/Api.java").read_text()
backup=Path("mapatrayectos/src/main/java/uy/com/mapatrayectos/CloudDataBackup.java").read_text()
migration=Path("supabase/migrations/20261017000000_driver_data_export_page_v1.sql").read_text()

def part(text,start,end):
    assert start in text, start
    assert end in text, end
    return text.split(start,1)[1].split(end,1)[0]

menu=part(main,"private void showMaintenanceMenu(View anchor){","private void addDataMenuRow")
assert "Exportar base local" in menu
assert "Importar base local" in menu
assert "Exportar historial de nube" in menu
assert "Espacio y estadísticas" in menu
assert "Empezar de cero" not in menu
assert "promptResetDatabase(" not in menu
assert "executeResetDatabase(" not in menu
assert "No se borr" not in menu or "borrados automáticos" in menu
assert 'addMainMenuRow(menu,"▣","Datos"' in main
assert 'this::showDataSafeConfiguration' in main
assert "showSoundPackMenu" in main
assert "DatabaseBackup.exportBackup" in main
assert "DatabaseBackup.importBackup" in main
assert "CloudDataBackup.export" in main
assert "REQ_EXPORT_CLOUD" in main

for dataset in ("jornadas","viajes_mapa","puntos_gps","reservas",
                "eventos_reserva","paradas_reserva","mensajes_reserva"):
    assert f'"{dataset}"' in backup
    assert f"when '{dataset}'" in migration
assert "ZipOutputStream" in backup and "ZipInputStream" in backup
assert "verify(context,destination,counts)" in backup
assert "registerCloudBackupDevice" in backup
assert "getCloudHistoryPage" in backup and "getCloudHistoryPage" in api
assert "p_device_secret" in api and "p_device_secret" in migration
assert "driver_pin_valid" in migration
assert "limit $3" in migration.lower()
assert "grant execute" in migration.lower()
for forbidden in ("truncate", "delete from", "drop table", "drop function",
                  "update public", "insert into public"):
    assert forbidden not in migration.lower(), forbidden

print("PASS — separate Data / Settings; preserve local ZIP; cloud read-only PIN + device auth")
print("PASS — all seven cloud datasets; streamed ZIP, manifest and verification")
print("PASS — no DELETE/TRUNCATE/purge in cloud SQL; no visible local reset")
