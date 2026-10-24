# Inventario de fuentes y recuperación — Quick Trip

**Corte:** 24/10/2026. **Relevamiento GET previo:** [auditoría integral](PRODUCTO_CANONICO_QUICKTRIP.md).  
**Ámbito:** referencias existentes. **NO reconstruye producción ni borra históricos**. Todas las rutas abajo son relativas al repositorio `marcelofgx-ctrl/traslados-android`.

## 1. Snapshot y backups

| Rol | Commit / ref | Calificación |
|---|---|---|
| **Base única de código Web/PWA** | `main@888d0696ebff98773e61039e3df6e46efd872317` | Baseline de código publicado, **no aprobado visualmente** |
| Backup protegido equivalente | `backup/continuidad-20261024-main` → mismo commit `888d0696` | No tocar |
| Backup previo a PR #194 | `backup/continuidad-20261024-web-pre-pr194` → `c3614f62c9f9d14707b26dadacb311d10e834a1d` | Solo rollback |
| Despliegue Pages | GitHub Actions [#39987500067](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/39987500067) | SUCCESS 23/10; NO acredita Samsung |
| PR #194 | fusionado a main el 23/10 | Cambió UI/cache v83 |
| Web QA v85 compacta | `qa/quicktrip-tu-viaje-compacto-v85-20261024` | Acción #40093954281, no integrada |
| Web QA v85 resiliencia | `qa/quicktrip-v85-resiliencia-fix-20261024@452ffc8e9b0d76610a5638b87f3c0cd94fe73fd3` | Acción #40100493658, no integrada |
| Web QA compuesta V6/v85 | `qa/quicktrip-v85-visual-v6-circulos-samsung-20261024@e99623b50720f678ed42d74b66a9613f89a5d026` | Chromium #40131257144, Samsung físico pendiente |
| Mapa QT QA R25.38 | `qa/mapa-qt-r2538-firma-aislada-20261024` | Diferente línea de integración Android, no fusionar |
| Mapa QT `main` | `mapatrayectos/build.gradle`: versionCode 77 / `0.1-R25.25` | No confundir con R25.38 QA (versionCode 91) |

## 2. Contratos con precedencia

| Fuente | Git blob SHA | Papel |
|---|---|---|
| `AGENTS.md` | `80b47ffb191955783ae2c4a3cb9f6e0bb5124e69` | Reglas de continuidad |
| `docs/METODOLOGIA_PERMANENTE_TRASLADOS.md` | `81540d2714054f581fb9f0997c51733ac3148db6` | G0–G6 y no declarar Samsung por CI |
| `docs/QUICK_TRIP_V6_G4_FUNCIONAL_20261019.md` | `3e4818250e8dd6cea770ec51d2af66685e7c5dd8` | Portada, círculos, tema |
| `docs/CONTRATO_VISUAL_TU_VIAJE_4_PANTALLAS_20261019.md` | `a30ee274e6f190c593b1e922e15e2cba16060fc3` | **Histórico**, no rige pantalla única nueva |
| `docs/TU_VIAJE_PANTALLA_UNICA_CONTRATO_20261020.md` | `8a5c1e202a205845c49266063a9255ca1cc9a393` | Flujo actual compacto |
| `docs/CONTINUIDAD_METODOLOGIA_Y_CAPTURAS_20261018.md` | Fuente de ZIP privado de capturas, no adjunto | La imagen original no se presume recuperada |

## 3. Blobs de motores y estado del código en la base

Estos hashes son **SHA Git de blobs**, no SHA256. Se comprobarán contra `git rev-parse HEAD:<archivo>` en CI. Sirven para detectar alteración exacta en esta fase, no como prohibición perpetua a corregir motores con autorización posterior.

| Archivo en la base | Git blob SHA | Responsabilidad |
|---|---|---|
| `web-pasajero/app.js` | `49156fb93615514729085ae5f03f318582bbbb3c` | DOM + búsquedas/picker, reserva, sesión |
| `web-pasajero/route-engine.js` | `028adf0d3f009da475ed9bf096c2331d64622c55` | Rutas verificadas |
| `web-pasajero/address-engine.js` | `b989852d7b701118e700abfe58015945a6cf3dcc` | Direcciones, fallback IDE |
| `web-pasajero/eta-engine.js` | `7ea575b66c05cf74c1c0f187aa6fd348641cd870` | Disponibilidad/ETA |
| `web-pasajero/booking-contracts.js` | `8fb535ad1742fe5ae72ce13dc2de33e48c5a4686` | Reserva/precio/validación |
| `web-pasajero/rpc-client.js` | `8a1a1f377b4831495c4595cf8b6ae4c6d6d666ed` | Transporte RPC |
| `mapatrayectos/build.gradle` | `a240d380c59d2f50c8b6ef0e0fe2c02220e5c2a0` | Versionado/firma Android |

**Archivos operativos auditados, pero deliberadamente no modificados:** `web-pasajero/index.html` (`d2fcbbeea6fd5231bf009dee63b86e754d4c91d3`), `web-pasajero/quicktrip-clean.css` (`15161d428296208eb4c6da95353ed507f8fb1f49`), `web-pasajero/quicktrip-clean.js` (`d53b2085f167884ca60a47c732fd76cb54bc8fc0`), `web-pasajero/sw.js` (`3e0092625fed06d5c90b96f15c7589d21fc75e16`). Estos cuatro representan la **presentación y caché v83 realmente presentes en main**, no una visual aprobada.

**Otros motores inmutables por alcance:** `geo-search.js`, `premium-route.js`, `premium-map.js`, `pickup-observability.js`, `pickup-cadence.js`, `pickup-origin-drift.js`, `booking-steps.js`, `owned-pickup.js`, `reservation-live.js`, `guest-history.js`, `trip-board.js`, `core-reservations/`, `mapatrayectos/`, `conductor/`, `db/`.

## 4. Evidencia visual y límites

El repositorio conserva `web-pasajero/pruebas/visual-v6/` (prototipo) y `docs/visual-qa/` (imágenes de Mapa), pero eso **no demuestra posesión de los originales privados aprobados para Quick Trip Pasajeros**. El reporte del 18/10 refiere un ZIP privado de 12 capturas. A la fecha, no existe SHA256 comprobado de esos originales en esta rama.

Antes de promover una candidata, referenciar en registro seguro:
- ID abstracto de captura aprobada; hash SHA256 de cada imagen; uso `portada`/`Tu Viaje`/`Programado` y modo.
- SHA exacto de candidata y resultado de comparación 360/393/412 CSS px.
- Evidencia física Samsung y aprobación **expresa**.
- **No publicar imágenes con teléfonos, direcciones, posiciones o reservas privadas en este repo público**.

## 5. Seguridad/CI observado y siguientes bloqueos

- Ruleset activo **Proteccion Quick Trip Main** (id 25770144): no borrar / no force-push.
- Ruleset activo **QuickTrip Backup** (id 25770262): no borrar / no force-push / no actualizar.
- La protección clásica de `main` devolvió 403; no afirmar que no existen otras reglas.
- `.github/workflows/pr-scope-guard.yml` prueba declaración de alcance PR; su existencia no asegura required checks en GitHub.
- `.github/workflows/check-quicktrip-v6-functional.yml` exige archivos de V7 y caché v83; protege historia, no fidelidad de capturas.
- **Pieza fase 1:** `scripts/qa_quicktrip_phase1_guard.py` + `scripts/test_quicktrip_phase1_guard.py` + CI aislado, con negativos deliberados.

## 6. Regla operativa

La rama de fase 1 **no recibe componentes de interfaz ni motores de las ramas listadas**. Documenta precedencia y prueba políticas. La base de recuperación permanece `888d0696`. No fusionar ramas completas. Todo trabajo visual posterior deberá crear candidata nueva con **una sola capa visual**, pruebas por SHA, fotos de referencia y validación Samsung.
