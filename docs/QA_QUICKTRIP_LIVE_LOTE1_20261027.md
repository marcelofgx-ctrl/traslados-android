# Quick Trip Live — Lote 1, solamente código QA

**Alcance:** módulo GPS de prueba local para Quick Trip/MAPA QT, sin red pública, sin compilar Android, sin nuevos recursos Cloudflare/D1 ni modificaciones a Supabase. Punto de partida: `qa/quicktrip-v90-acceleration-pilot-20261026@8db0c66441ba8eef150d3962b8f81df6353bba6f`.

## Propietarios y archivos
- **Backend QA:** `qa/backend-worker/src.mjs` solo añade despacho a `tracking.mjs`; `schema.sql` incorpora dos tablas y trigger terminal; `test/quicktrip-live.test.mjs` prueba el contrato.
- **Web/PWA QA:** `web-pasajero/quicktrip-live-qa.js` implementa adaptador opcional compatible con `reservation-live.js` / `premium-map.js`, sin editar los motores ni los recursos de producción. `web-pasajero/tests/quicktrip-live-qa.test.cjs` prueba el adaptador.
- **Documentación:** este contrato. No se modifica `AGENTS.md`, `main`, rutas, APK ni workflows.

## Contrato HTTP QA (protegido por autenticación y CORS del Worker existente)

| Método / recurso | Acceso | Resultado |
| --- | --- | --- |
| `POST /v1/requests/:id/tracking-consent` | Conductor asignado | `{enabled:boolean, expectedVersion:int}`: consentimiento por solicitud y versión |
| `POST /v1/requests/:id/location` | Conductor asignado y consentimiento vigente | GPS `{seq,lat,lng,accuracyM,headingDeg?,measuredAt}`, `seq` creciente |
| `GET /v1/requests/:id/location` | Pasajero propietario | `{available:false,qaOnly:true}` o última ubicación válida sin identidad del conductor |

Requiere que la solicitud esté en `ACEPTADA`, `EN_CAMINO`, `EN_PUNTO` o `EN_VIAJE`, con `driver_ref` asignado. Coordenadas limitadas al dominio de Uruguay, precisión de 50 m o mejor, muestra medida hace no más de 15 s y máximo 2 s de adelanto. Expira 45 s después de recibida; si caduca no se entrega. La secuencia impide retrocesos o repeticiones. El servidor guarda **solo la última posición**, no una trayectoria histórica.

Al revocar consentimiento se bloquean lecturas y nuevas escrituras y se elimina la posición. Un trigger elimina posición y revoca permiso al cambiar a `FINALIZADA`, `CANCELADA` o `RECHAZADA` dentro de la misma transacción de estado. El registro antiguo de una sesión inactiva podría seguir físicamente en D1 hasta una limpieza posterior: **antes de exponer un entorno remoto se exige definir y probar una política de retención/purga de datos**. No registrar coordenadas o tokens en logs ni artefactos.

## Adapter QA y compatibilidad
- No se inyecta ningún `script` en `index.html` o `sw.js`; se importa solo en una prueba o integración QA autorizada.
- Requiere las marcas `quicktrip-qa-isolation=v90-route-sandbox...` y `traslados-preview=read-only`. URL backend explícita HTTPS que contenga `quicktrip-qa` (o localhost), sin claves embebidas: `tokenProvider()` inyectado en runtime de prueba.
- Convierte la solicitud QA activa al contrato del renderer existente y sustituye su `rpc` privado por lectura aislada del Worker. Recicla el mapa y la ruta vial verificada; **no inventa ruta, ETA ni posición del conductor**.
- La CSP actual de v90 permite solo lecturas externas previas; por lo tanto, el adaptador no está conectado ni probado en navegador con backend real. Añadir una nueva conexión exige autorización independiente y CORS/CSP específicos.
- Mientras no exista backend QA y APK QA aislada, se trabaja con tokens y coordenadas **sintéticas**. La APK actual permanece vinculada al proyecto operativo y no puede usarse para enviar posiciones al Worker.

## Pruebas locales, sin red
En la raíz del repositorio:

```sh
cd qa/backend-worker && npm test
cd ../../web-pasajero && node --test tests/quicktrip-live-qa.test.cjs
```

Los cuatro nuevos tests de backend cubren permisos, consentimiento, secuencias, geolocalización inválida, expiración, revocación y cierre; los cuatro tests Web validan aislamiento, GET sin caché, rechazo de GPS inválido y reutilización del renderer. Se han ejecutado los ocho tests nuevos localmente con Node 22.16 y un esquema SQLite local equivalente para las tablas necesarias: **8/8 PASS**. La ejecución integrada de `npm test` sobre la rama publicada, CI remoto, Cloudflare real, CSP real y Samsung **no está demostrada por esa ejecución** y permanece pendiente de verificación aparte.

## Puertas para lotes posteriores
1. Verificar tests completos GitHub y protección de rama, además de no haber diffs fuera de los siete archivos declarados.
2. Antes de crear Worker/D1: consentimiento de costos, cuotas, autenticación individual, retención/purga de GPS, protección contra abuso, CORS y CSP aislados. No usar Supabase real.
3. Antes de GPS real: compilar solo Mapa QT QA con `applicationIdSuffix=.qa` y auditoría absoluta de endpoints; verificar que todas las comunicaciones de Mapa/Conductor QA están desacopladas de producción.
4. La prueba de movimiento GPS real, suspensiones, reconexión y revocación deberá hacerse en Samsung y otro dispositivo; el Lote 1 **no equivale a seguimiento real operativo**.
