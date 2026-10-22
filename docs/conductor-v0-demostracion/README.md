# Quick Trip — Demostración interactiva de Conductor V0

**Fecha:** 22/10/2026. **Rama:** `feat/conductor-v0-demo-visual-20261022`.

## Resultado visible
Abrir `docs/conductor-v0-demostracion/index.html` en cualquier navegador, sin conexión. El diseño replica la estructura y los colores de `ConductorV0View.java`; las solicitudes provienen de la misma muestra que `ImmediateTripSimulation.sample()`. Incluye dos solicitudes nuevas, una aceptada y una programada excluida del flujo inmediato; botones Aceptar/Rechazar, contadores, histórico plegable, error simulado y mapa ilustrativo.

## Alcance técnico y limitaciones
Esta demostración **NO es una ejecución de la APK** ni contiene código Android en producción. Es un prototipo HTML autocontenido sin peticiones de red, PIN ni Supabase. No crea ni responde reservas reales. La vista de mapa también es ilustrativa y no contiene GPS. El panel original nativo y los motores estables no se han modificado.

## Evidencia QA (navegador local)
Se ejecutaron 25 comprobaciones automáticas con Chromium local: estados iniciales, origen/destino, exclusión de programados, aceptación/rechazo, cambio de contadores, bloqueo de doble decisión, histórico, error simulado sin falso éxito, navegación y ausencia de peticiones de red. Pruebas ejecutadas fuera de GitHub Actions con Playwright; **no** constituyen QA de Android físico ni validan integración con Supabase.

## Rollback
Esta rama solo agrega estos archivos de demostración. Eliminarla o ignorarla recupera exactamente la etapa 4B (`40915905a75713a3fb6d18893238ad83895b9e68`). No afecta `main`, Supabase, la web de pasajeros ni ninguna APK instalada. Para una futura integración nativa hace falta verificación independiente y autorización expresa.
