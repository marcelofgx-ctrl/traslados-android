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

**B · Integración condicionada por alcance.** `validate-modular-mapa.yml` **siempre ejecuta sus regresiones de contratos**. Ante cambios en Java funcional, recursos, Gradle, workflows, archivos no reconocidos o diffs inciertos, también ejecuta `:mapatrayectos:assembleDebug`. **Única excepción:** PR que modifica exclusivamente alguno de los ocho archivos de tests ya ejecutados por B, y opcionalmente documentación; se omiten entonces SDK/Gradle/empaquetado. La ejecución manual `workflow_dispatch` siempre conserva compilación Android. **Esta excepción no autoriza cambiar código funcional sin compilar ni publicar sin pruebas físicas.**

**C · Firma y QA.** Un único `:mapatrayectos:assembleRelease` en el SHA integrado, comprobación de `applicationId`, `versionCode`, firma, release/artifact, instalación sobre versión previa **sin desinstalar** y QA Samsung. No se ejecuta C por cambios puramente web o documentales.

## Cambio limitado / G1

Antes de modificar, dejar en el PR: propietario, archivos previstos, contratos/invariantes afectados, pruebas de nivel A y B, y superficie/QA. Comprobar los paths reales con `git diff --name-only <base>...HEAD`. Si aparecen archivos fuera del alcance, detener la promoción para justificar el cambio.

**Control G1 ya incorporado:** el [PR #131](https://github.com/marcelofgx-ctrl/traslados-android/pull/131) introdujo `.github/workflows/pr-scope-guard.yml`, que compara paths exactos contra la declaración del PR y verifica responsable. **No equivale por sí solo a protección obligatoria de rama**: revisar branch rulesets antes de llamarlo bloqueo definitivo.

**G4 selectivo implementado y comprobado:** [PR #132](https://github.com/marcelofgx-ctrl/traslados-android/pull/132) introdujo `scripts/ci_mapa_build_impact.py` y nueve pruebas (`scripts/test_ci_mapa_build_impact.py`). Cuando cambia el workflow mismo, la integración Debug sigue siendo completa: [CI #39127340964](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/39127340964) SUCCESS. Si cambia exclusivamente una prueba conocida, el clasificador omite SDK, Gradle y `assembleDebug`, con regresiones restantes en verde: [CI #39127953694](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/39127953694) SUCCESS.

**Visual QA selectiva:** [PR #134](https://github.com/marcelofgx-ctrl/traslados-android/pull/134) excluye `mapatrayectos/tests/**` del disparador visual, conservando Visual QA para cambios funcionales, recursos, Gradle y su propio workflow; este último pasó [CI visual #39127674733](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/39127674733). La prueba [PR #133](https://github.com/marcelofgx-ctrl/traslados-android/pull/133), ejecutada nuevamente en SHA `05e3c3a05096`, solo activó G1, contratos rápidos y G4 sin empaquetado: **tres CI verdes y cero ejecución nueva de Visual QA**; el G4 totalizó ~34 s de inicio a fin. En cambios de código real continúan los tests y compilación Android; no se reduce cobertura ni se sustituye QA Samsung.

**Límite de cumplimiento obligatorio:** `main` se comprobó con `protected=false` y sin rulesets activos al 18/10/2026. Los CI son detectores y puertas de nuestra metodología, **todavía no protecciones de merge impuestas por GitHub**. Habilitar checks requeridos y protección de rama es un trabajo posterior, tras valorar la cadena de PR #119–#129 y permisos de repositorio.

## Precaución con ramas

En `main` aún aparecen referencias de compilación de módulos `:cliente` y `:conductor` por historia del código. La arquitectura **vigente como objetivo definitivo** es web pública + Web/PWA pasajeros + **única APK Mapa**; el refactor oficial de `settings.gradle` y `prepareEmbeddedConductor` vive en ramas de revisión. No mezclar esa migración estructural con nuevos CI rápidos ni declarar publicada R25.31 sin QA Samsung.

## Memoria entre sesiones

En Supabase Traslados privado: `project_ops.work_memory` indexa responsables, contratos, PR, estados y próximas acciones, usando `details` JSONB y enlaces a este archivo. Los cambios deben guardarse de forma explícita, tras confirmar GitHub y workflows. `project_ops.work_memory_events` conserva la bitácora. **No guardar datos personales, PIN, reservas ni GPS.**

**Éxito de esta fase =** test rápido ejecutable sin APK y sin alterar ni deshabilitar CI anterior. La reducción de tiempo total de la fase B solo puede declararse después de medir/ajustar workflows y verificar la nueva política de merge.
