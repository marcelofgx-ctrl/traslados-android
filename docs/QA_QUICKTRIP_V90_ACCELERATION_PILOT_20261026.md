# Quick Trip / Mapa QT — piloto de aceleración CI + Playwright (26/10/2026)

**Solo QA. Rama:** \`qa/quicktrip-v90-acceleration-pilot-20261026\`. **Base inmutable:** \`8d882e63e1a4e779f956868af64c9e6158c4c0ac\` (v90).
**Prohibido:** modificar main, producción, Supabase real, Cloudflare Worker, GPS, motor de rutas, reservas, APK/Android, firma, publicación, fusionar PR.

## Diagnóstico inicial
La rama v90 tiene 76 archivos en \`.github/workflows\`: **73 YAML** y **3 no-YAML** (un APK, un TXT y un archivo \`yml (1)\`). El piloto añade **un** YAML QA: inventario resultante **74 YAML + 3 no-YAML = 77**. Tener un archivo YAML no demuestra que esté activo ni usado: requiere auditoría de triggers, runs y ownership.

Ya existen \`fast-modular-contracts.yml\`, \`pr-scope-guard.yml\`, \`validate-modular-mapa.yml\`, \`qa-quicktrip-v90-route-sandbox.yml\`, y \`web-pasajero/tests/quicktrip-v90-route-sandbox.cjs\` con Playwright-Core y capturas. **Reutilizar antes de inventar un segundo sistema.**

## Piloto implementado
1. **Inventario reproducible:** \`python3 scripts/qa_workflow_inventory.py --output quicktrip-v90-pilot-evidence/inventory\` → listado de archivos, tamaño y categoría aproximada. Sin APIs de escritura.
2. **Workflow nuevo, aislado:** \`.github/workflows/qa-quicktrip-v90-acceleration-pilot.yml\`; solo push a esta rama o despacho manual; \`contents: read\`, checkout sin credenciales persistidas, scope exactamente cuatro archivos nuevos. No hay acciones de deploy, publicación, firma ni operaciones en Supabase.
3. **Pruebas existentes v90:** se ejecutan sin modificación como línea de base, con OSRM falsificado y bloqueo de cualquier otro acceso externo.
4. **Extensión Playwright nueva:** pantallas 393 y 360 píxeles, precio a 40 UYU/km, conductor ficticio disponible/no disponible, una y dos paradas ficticias, retiro y recálculo, exceso de 4 paradas admitidas por el adaptador QA, NoRoute, respuesta vial inválida, red desconectada y recuperación. Se generan JSON y capturas PNG, sin GPS real.
5. **Artefacto de evidencia temporal:** \`QuickTrip-v90-Acceleration-QA-Evidence\` (14 días) con inventario, baseline y casos negativos. No es un preview ni una publicación.

### Límites explícitos
- OSRM sintético interceptado: comprueba **estados de interfaz y aritmética**, **no exactitud vial real**.
- Modelo Samsung de Chromium en CI: comprueba ancho/móvil y DOM, **no sustituye QA física Samsung**.
- No valida flujos de reserva real, autenticación, disponibilidad real del conductor, notificaciones push, ni Supabase.
- La v90 original limita su adaptador a máximo **4 paradas** (origen + 4 + destino = 6). La UI nativa puede tener otro límite. La prueba es de la política **del adaptador QA**, no una decisión de producto.
- El inventario clasifica por nombres, no presupone que los workflows heredados estén operativos.

## Propuesta futura de CI unificado (NO implementada sobre los pipelines existentes)
**G0 / preflight (segundos):** identificar módulos cambiados y separar \`Web/PWA\`, \`Android\`, \`backend\`, \`docs\`. Bloquear archivos protegidos y exigir declaración de alcance; reusar \`pr-scope-guard.yml\`.
**G1 / contratos rápidos:** tests JS/Python puros y \`fast-modular-contracts.yml\`, **sin APK**.
**G2 / Web/PWA:** Playwright con mocks, captura móvil, escenarios negativos de rutas, paradas, precio, conexión, CSP y ciclo SW, reportando evidencias.
**G3 / Android:** \`validate-modular-mapa.yml\` y otras pruebas selectivas **solo si hay cambios Android y autorización**; APK firmada solo cuando se apruebe un candidato.
**G4 / revisión:** reporte de casos, capturas, commit SHA, resultado y enlace. Un fallo bloquea propuesta de integración.
**G5 / promoción manual:** fusionar o desplegar únicamente bajo nueva autorización explícita; este piloto nunca ejecuta G5.

**Consolidación futura sugerida:** matriz de flujos heredados por owner, trigger, última ejecución exitosa, dependencias y uso real. Identificar duplicados y candidatos a archivo histórico sin borrar ni deshabilitar ningún workflow durante este piloto.

## Evidencias y aceptación
Inspeccionar el último GitHub Actions run de este workflow, descargar el artefacto y comprobar:
- Scope guard PASS, inventario \`74 YAML + 3 no-YAML\` y JSON.
- Baseline v90 PASS con capturas.
- Extensión Playwright PASS con capturas, ausencia de requests reales a backend y de page errors.
- Ningún APK construido, ninguna página publicada, ningún commit en main.
No declarar PASS hasta ver el run. Los archivos creados por sí solos **no** prueban que Playwright haya ejecutado satisfactoriamente.
