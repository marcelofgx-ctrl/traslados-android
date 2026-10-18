# Matriz operativa de módulos, propietarios y contratos — Traslados

**Versión inicial:** 18/10/2026. Ampliación de [METODOLOGIA_PERMANENTE_TRASLADOS.md](METODOLOGIA_PERMANENTE_TRASLADOS.md); **no es una metodología paralela** ni un cambio de arquitectura desplegada.

## Regla de propiedad

Cada responsabilidad tiene un **propietario lógico** (reglas verificables) y adaptadores de web, PWA o Android. Un propietario lógico no exige crear inmediatamente otro módulo Gradle. Nunca reimplementar reglas de negocio en una vista diferente; identificar interfaces, estados y riesgos antes de editar.

| Dominio | Responsable actual / superficie | Contrato blindado | Prueba / puerta |
|---|---|---|---|
| Direcciones y POI | `web-pasajero/address-engine.js`, `geo-search.js`; adaptador `app.js::picker` | Exactitud de puerta, `direcUnica`, alternativas, departamento, precisión, sin falsos resultados | PR #122/#125; picker real + IDE read-only + Chrome/Samsung |
| Reservas, agenda y decisiones | `core-reservations/QuickReservationLogic.java`, contratos PWA y RPC Supabase; adaptador Conductor integrado | Una sola creación, estados y aceptación/rechazo tarifa fija vs presupuesto legado, sin duplicados | `bash scripts/ci_fast_contracts.sh reservations`; #123/#126; integración E2E |
| Precio, km y ETA | `web-pasajero/route-engine.js`, `eta-engine.js`, RPC de tarifa | km y minutos de viaje separados del tramo a recoger; nunca estimación ficticia | tests JS existentes + ruta proveedor verificada |
| GPS, navegación, historial | `NavigationHeadingPolicy.java`, `TrackingService`, `reservation-live.js` | Map heading-up real, privacidad y consentimiento, sin perder traza GPS | `bash scripts/ci_fast_contracts.sh navigation`; #119; QA ruta real |
| Cierre/estadísticas de viajes | `TripClosePolicy.java`, `TripPlatformChoice.java`; adaptadores Mapa | No cerrar viaje inválido, conservar plataforma y correcciones | `bash scripts/ci_fast_contracts.sh trip`; CI modular/E2E |
| Estado y salud | `SystemStatusPolicy.java`, `SystemStatusActivity` | Límites de cuota claros, sin estado falso | `bash scripts/ci_fast_contracts.sh system`; CI Android |
| Identidad y sesión | `DriverIdentity.java` en Mapa, Supabase para autenticación | Vínculo explícito, sin PIN reiterado, sin caché de otro conductor | #121/#127/#129; Keystore y Samsung |
| Recepción, burbuja, avisos y chat | `TransferAlerts.java`, `ReservationMonitorService.java` | No perder solicitud, no alertar duplicados, respuestas tardías descartadas | #120/#127/#129; QA abierta/minimizada |
| Presentación premium | Web/PWA `app.js`, CSS, pantallas nativas Mapa | Contratos de accesibilidad, interfaz clara, no romper negocio | pruebas visuales + captura real |

## Puertas de compilación (sin eliminar cobertura)

**A · Prueba aislada.** `bash scripts/ci_fast_contracts.sh [reservations|navigation|trip|system|all]`. Solo `javac`/`java` sobre archivos Java puros, sin descargar SDK, Gradle ni construir APK. CI: `.github/workflows/fast-modular-contracts.yml`, opt-in manual y automático en cambios de políticas puros. La selección manual exige responsabilidad por el módulo.

**B · Integración.** El workflow existente `validate-modular-mapa.yml` mantiene sus tests asociados y `:mapatrayectos:assembleDebug`. El nuevo camino A ofrece respuesta rápida, **pero inicialmente no sustituye ni cancela B**; separar condicionalmente B o exigir checks de rama requiere una segunda fase y protección de ramas verificada. Para cambios Java nativos o antes del merge, B sigue siendo obligatorio.

**C · Firma y QA.** Un único `:mapatrayectos:assembleRelease` en el SHA integrado, comprobación de `applicationId`, `versionCode`, firma, release/artifact, instalación sobre versión previa **sin desinstalar** y QA Samsung. No se ejecuta C por cambios puramente web o documentales.

## Cambio limitado / G1

Antes de modificar, dejar en el PR: propietario, archivos previstos, contratos/invariantes afectados, pruebas de nivel A y B, y superficie/QA. Comprobar los paths reales con `git diff --name-only <base>...HEAD`. Si aparecen archivos fuera del alcance, detener la promoción para justificar el cambio.

**Futura automatización pendiente:** gate de alcance por rutas y contratos declarados, checks obligatorios, reglas de protección y comparación de métricas. **NO afirmar que hoy bloquea merges**: esta versión solo añade un runner rápido reproducible, una matriz y documentación. No retirar tests para parecer más rápido.

## Precaución con ramas

En `main` aún aparecen referencias de compilación de módulos `:cliente` y `:conductor` por historia del código. La arquitectura **vigente como objetivo definitivo** es web pública + Web/PWA pasajeros + **única APK Mapa**; el refactor oficial de `settings.gradle` y `prepareEmbeddedConductor` vive en ramas de revisión. No mezclar esa migración estructural con nuevos CI rápidos ni declarar publicada R25.31 sin QA Samsung.

## Memoria entre sesiones

En Supabase Traslados privado: `project_ops.work_memory` indexa responsables, contratos, PR, estados y próximas acciones, usando `details` JSONB y enlaces a este archivo. Los cambios deben guardarse de forma explícita, tras confirmar GitHub y workflows. `project_ops.work_memory_events` conserva la bitácora. **No guardar datos personales, PIN, reservas ni GPS.**

**Éxito de esta fase =** test rápido ejecutable sin APK y sin alterar ni deshabilitar CI anterior. La reducción de tiempo total de la fase B solo puede declararse después de medir/ajustar workflows y verificar la nueva política de merge.
