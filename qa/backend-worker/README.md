# Quick Trip v90 — Lote 1: Cloudflare Worker + D1 (NO DESPLEGADO)

**Solo código en QA.** No se creó Worker, D1, cuenta, secreto ni reserva; no hay conexión con Supabase, Android o Cloudflare operativo. Todos los endpoints requieren un backend D1 **nuevo y separado** que todavía no existe.

## Archivos

- `src.mjs` — API HTTP QA para pasajero y conductor. Solo utiliza `env.DB` y credenciales QA, nunca URL productiva ni cliente Supabase.
- `schema.sql` — SQLite D1: `qa_requests` y `qa_request_events`. Dos triggers guardan el evento inicial y cada cambio de estado en la misma operación atómica.
- `test/worker.test.mjs` — seis pruebas locales con SQLite en memoria (`node:sqlite`) y adaptador D1.
- `wrangler.example.jsonc` — plantilla **inactiva** con identificador ficticio, no se puede desplegar sin aprobación/configuración.
- `package.json` — `npm test`, sin dependencias externas.

## Autenticación QA

Configurar en un futuro despliegue, exclusivamente como *secrets* independientes:

- `QA_PASSENGER_TOKEN`: token de prueba largo (mínimo 32 caracteres), nunca incorporado al bundle público.
- `QA_DRIVER_TOKEN`: token distinto, mínimo 32 caracteres, **solo** en la variante Mapa QT QA.
- `QA_ALLOWED_ORIGIN`: origen HTTPS **exacto** de la Web/PWA QA, sin comodines.
- `DB`: binding D1 a una base **QA nueva**, no una tabla de producción.

Identidades del piloto: un pasajero de prueba y un conductor de prueba vinculados mediante la huella SHA-256 de su token. Los tokens no se almacenan en D1; no hay PIN real, cuenta comercial ni GPS privado. Los clientes deben obtener el token de forma segura en pruebas, jamás incrustarlo en código, HTML o Git. **Antes de ampliar a múltiples usuarios** se requerirá autenticación individual/rotación y rate limiting perimetral adicional.

## Contrato de endpoints (todos HTTPS, sin caché)

| Endpoint | Rol QA | Efecto |
| --- | --- | --- |
| `GET /health` | Público | Indica configuración de servicio, sin datos |
| `POST /v1/requests` | Pasajero | Crea `PENDIENTE`, con `idempotencyKey` UUID y origen/destino sintéticos |
| `GET /v1/requests/:id` | Pasajero propietario o conductor autorizado | Estado y eventos, sin GPS |
| `GET /v1/driver/requests` | Conductor | Hasta 25 pendientes vigentes o solicitudes asignadas |
| `POST /v1/requests/:id/transition` | Conductor o cancelación del pasajero | Cambia estado validado con `expectedVersion` |

Transiciones de conductor: `PENDIENTE → ACEPTADA → EN_CAMINO → EN_PUNTO → EN_VIAJE → FINALIZADA`, con `PENDIENTE → RECHAZADA`. El pasajero puede cancelar solamente antes de la llegada (`PENDIENTE`, `ACEPTADA`, `EN_CAMINO`).

La creación idempotente con la misma clave devuelve una sola solicitud; reutilizar la clave con datos distintos devuelve 409. La actualización exige versión y estado previos para impedir doble aceptación. Eventos y cambio de estado son atómicos por triggers SQLite. Una solicitud pendiente caduca a los 20 minutos. Hay un límite básico de 12 creaciones/hora/pasajero; para activar un Worker público debe añadirse una protección perimetral efectiva.

### Pruebas mínimas locales

Desde esta carpeta, con **Node 22.13+**:

```bash
npm test
```

Usan SQLite real en memoria y una adaptación pequeña de la API D1. Verifican credenciales y roles, CORS exacto, idempotencia, solicitud→lista conductor→aceptación→fases, rechazo, caducidad, cancelación y conteo de eventos. **No equivalen a una integración real desplegada en Cloudflare ni a pruebas Samsung.**

### Bloqueos expresos

- No ejecutar `wrangler deploy`, `d1 create` ni migraciones remotas sin autorización posterior y verificación del plan/cupos de Cloudflare Free.
- No llamar a `zetaudvvutlouiqxopvg`, `Oak & Ember`, trabajadores operativos ni endpoints de reservas reales.
- No modificar `main`, módulos Android ni pruebas o workflows existentes.
- El Lote 2 requiere crear *más adelante* el Worker/D1 QA aislado, credenciales seguras y variante Android con otro package ID. Por ahora **solo existe el contrato y su implementación local**.

Referencia: [contrato original de aislamiento](../../docs/QA_CANAL_SOLICITUDES_MAPA_QT_20261026.md). Cloudflare D1: <https://developers.cloudflare.com/d1/worker-api/d1-database/>.
