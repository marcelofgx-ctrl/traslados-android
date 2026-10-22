# Mapa QT — Recuperación ordenada, punto de control 1 (22/10/2026)

Se parte del commit R25.30 `9bdf71d11ac3d96cf8251bf611aada1a98e9779f`. Cambios intencionales limitados al **nombre visible** y al incremento de `versionCode` (82→83), más documentación y un workflow firmado de verificación sin despliegue.

## Identidad
- Nombre definitivo: Mapa QT.
- Mismo paquete que la aplicación original: `uy.com.mapatrayectos`.
- El APK candidato debe ser `assembleRelease` con el certificado guardado en los secretos originales de GitHub; no firma debug ni sufijo QA.
- Conductor continúa integrado tal como en R25.30. No se agregó todavía Conductor V0 experimental.

## Condición de salida y límite
CI debe compilar con firma release, acreditar nombre e identificador y conservar evidencia. **No equivale a funcionamiento verificado en el Samsung.** No reemplazar aún una instalación sin revisar la versión/certificado instalados, pues la versión podría ser mayor o distinta. No se despliega ni integra en main.

## Siguiente paso
Comparar huella de firma release con la de la APK instalada; probar apertura/arranque en emulador Android y después prueba controlada en Samsung. Solo entonces incorporar Conductor V0 en la misma app y probar sus flujos, sin tocar motores.
