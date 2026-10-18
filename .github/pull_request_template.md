# Objetivo / problema real

<!-- Describir conducta del usuario, causa comprobada (o hipótesis explícita) y contrato afectado.
     Norma: docs/METODOLOGIA_PERMANENTE_TRASLADOS.md -->

**Registro de regresión:** <!-- ID de docs/REGISTRO_DE_REGRESIONES_TRASLADOS.md o "no aplica" -->
**Severidad:** <!-- P0 / P1 / P2 -->
**Base del PR / commit SHA:**
**Propietario de la lógica:** <!-- módulo/fuente única; adaptadores no duplican reglas -->
**Superficies afectadas:** <!-- Web pública / PWA pasajeros / APK Mapa integrada / RPC-DB-Worker -->

## Criterios de aceptación y compatibilidad

- [ ] G0: Reproduje por el mismo camino que usa el usuario; revisé historial para evitar corrección repetida.
- [ ] G1: Identifiqué la única fuente de verdad y las reglas/contratos que NO deben cambiar.
- [ ] G2: Cambios mínimos; no creé otra implementación equivalente ni otra APK Conductor/Cliente.
- [ ] G3: Incorporé prueba de regresión del flujo realmente ejecutado (antes fallaba / ahora pasa).
- [ ] G3: Corrí pruebas anteriores del área y documenté resultados.
- [ ] G4: CI pertenece al SHA exacto del PR; si hay APK, se verifica package, firma y versionCode; si hay PWA, HTML/JS/CSS/SW y caché son coherentes.
- [ ] G5: Comprobé integración Web/PWA → Supabase → Mapa → estados/GPS/historial o declaré expresamente qué está pendiente.
- [ ] G5: Preservé identidad, privacidad de GPS, permisos, reservas, configuración y datos históricos; no borré registros.
- [ ] G6: No afirmo «publicado» hasta verificar deploy/release específico; revisión y producción diferenciadas.
- [ ] G6: Dejé enlace verificable y plan de rollback / compatibilidad; QA Samsung indicado (realizado o pendiente).
- [ ] Continuidad: actualicé el PR/REANUDAR con SHA, entregables y siguiente acción concreta.

## Evidencia

| Hito | Enlace / SHA / estado |
|---|---|
| Tests automatizados | |
| Build / firma / package Android (si aplica) | |
| Pages/PWA desplegada (si aplica) | |
| Backend/migración aplicada (si aplica) | |
| Prueba real en Samsung | |
| Riesgo y rollback | |

**Estado solicitado:** <!-- DRAFT / READY FOR REVIEW / LISTO PARA PROMOCIÓN. No confundir aprobación de CI con publicación. -->
