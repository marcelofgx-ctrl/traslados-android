# Traslados — cierre de despliegue PWA v54 y QA Samsung (11/10/2026)

## Alcance y versión
- Repositorio `marcelofgx-ctrl/traslados-android`, rama `main`.
- Funcionalidad v54: PR #52, commit `33960c0f45183f3d17ea784609db5858fbac6187`.
- Commit posterior `62d2de4e917e2f21f0d9d71ce4cd51d88c31626d`: documentación; **no reemplaza los cambios funcionales**.
- PWA y WebView Cliente Premium v15 usan la misma web pública. Sin modificación de `app.js`, CSS, service worker, contratos, Supabase, Mapa R24.8, versiones ni certificados APK en este seguimiento.

## Publicación GitHub Pages — VERIFICADA
- PWA CI sobre v54/main: [run 38169921186](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38169921186) **SUCCESS**.
- Primer Pages del commit funcional: [run 38169920488](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38169920488) **CANCELLED** al ser reemplazado por una publicación posterior; no interpretar como compilación rota.
- [Pages run 38169989200](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38169989200) para `62d2de4`: **SUCCESS**, pasos Jekyll `build` y `deploy` exitosos. Contiene v54 porque el commit posterior solo cambió documentación.
- **Límite de evidencia:** no se comprobó el HTML servido directamente con navegador ni la caché del Samsung. `SUCCESS` confirma publicación de Pages, no actualización del service worker ya instalado en cada teléfono.

## Diagnóstico de producción adicional — lectura exclusivamente de observación
- Consulta `public_driver_availability_v1()` aproximadamente **19:57 UYT, 11/10/2026**: `available=false`, `status=not_available`, `motionHint=stationary`.
- Evaluación separada de `availability_has_conflict(...,60,...,true)` al mismo momento: **false**. El bloqueo de agenda de 15:33–17:03 explicado en `PWA_V54_DIAGNOSTICO_DISPONIBILIDAD.md` ya no era causa activa en ese instante.
- `driver_live_presence`, consulta de booleans/edades, sin recuperar ni publicar lat/lng: `enabled=true`, `shift_active=false`, `shift_paused=false`, `trip_active=false`, `manually_busy=false`; edad del último heartbeat **2.078 segundos** (~35 min); edad GPS nula/sin lectura válida.
- **Interpretación:** sin jornada activa y sin presencia fresca no corresponde servir ETA, aunque la agenda esté libre. Este resultado es una fotografía temporal, no diagnóstico persistente de falla GPS; no modificar agenda ni falsificar viajes para probarlo.
- En el RPC de disponibilidad un `available=false` no revela al pasajero la causa: puede ser GPS, jornada, opt-in o agenda. Mantener mensajes honestos y no revelar detalles privados.

## Cobertura automatizada reforzada
El smoke `web-pasajero/tests/public-driver-presence-smoke.cjs` usa mocks de red (nunca reservas reales) y ahora verifica la secuencia:
1. Disponibilidad positiva + origen verificado ⇒ ETA pública coarse (guest) y resultado `ready`.
2. Alternar `Ahora`/`En 10 min` no duplica llamadas; ETA detenido 45 s, moviendo 25 s, respeto por 429.
3. Cambiar a **no disponible** ⇒ fase `blocked`, sin métricas obsoletas ni nueva petición ETA.
4. Simular fallo HTTP 503 de disponibilidad ⇒ fase **`issue`**, distinta de agenda ocupada, sin métricas ni petición ETA.
5. Recuperar disponibilidad y sesión ⇒ vuelve a ETA de cuenta y `ready` sin exponer GPS; modo programado oculta ETA inmediata.

La suite se ejecuta con `node web-pasajero/tests/public-driver-presence-smoke.cjs` desde el workflow `deploy-web-pasajero-pwa.yml`. Las suites complementarias ya existentes cubren chat, resumen, reservas, presupuesto, UI responsiva y observabilidad v54. Los mocks NO constituyen prueba de dispositivo real.

## Prueba funcional pendiente en Samsung — no marcar como ejecutada
1. **Carga v54.** Abrir [PWA oficial](https://marcelofgx-ctrl.github.io/traslados-android/web-pasajero/) en Chrome Samsung y Cliente Premium v15; recargar ambas, comprobar misma cabecera/estilo, formulario plegable, sin scroll lateral ni elementos superpuestos. Si persiste versión anterior, revisar Service Worker/cache antes de re-instalar APK.
2. **Disponibilidad y consentimiento.** Con Mapa R24.8 y conductor auténtico, habilitar publicación de recogida, iniciar jornada, verificar sin datos personales que heartbeat/GPS estén frescos y que no haya viaje activo, pausa, ocupación manual ni conflicto de agenda.
3. **Sin origen.** Pulsar «Ahora»: disponibilidad positiva debe pedir origen, sin inventar km/min. Si no está disponible, ver «Recogida inmediata no disponible»; si la consulta RPC falla, «No pudimos comprobar la disponibilidad».
4. **Origen real con permiso.** Seleccionar una dirección válida; verificar fases `checking` → `routing` → `ready` **solo cuando ocurre consulta real**. En estado `ready`, km y rango de minutos corresponden a **Mapa → origen**, distintos de distancia/tarifa **origen → destino**. No se divulga coordenada precisa del conductor.
5. **Jornada/agenda.** Deshabilitar presencia de forma voluntaria o iniciar pausa y observar que desaparezca ETA; reactivar para verificar recuperación. Si hay una reserva aceptada legítima, respetar sus buffers; no crear ni eliminar reservas ficticias.
6. **Cadencias.** En pantalla frontal, disponibilidad ~15 s, ETA no más frecuente que 25 s moviendo o 45 s detenido; con 429 respetar enfriamiento. No prometer actualización de ubicación segundo a segundo. Validar con tiempos reales y sin forzar trayectos riesgosos.
7. **Viajes y atención.** Probar con reserva REAL autorizada (no de demo) el seguimiento de estados: solicitud, recepción Conductor, presupuesto enviado, confirmación pasajero, mensajes en ambos sentidos, inicio/finalización y vista de historial; comprobar que un cambio de origen o paradas invalide ruta/tarifa antigua.
8. **Avisos.** Con PWA abierta, verificar popup, sonido/vibración si fueron consentidos. **No declarar push con app cerrada**: FCM nativo Cliente v15 aún pendiente. Comprobar alertas de Conductor según el servicio esté activo.
9. **Diagnóstico privado opcional.** En Chrome remoto, agregar `?diagnostico=1` y ejecutar `window.TrasladosPickupHealth.summary()`. Compartir únicamente `step`, `outcome`, `latencyMs`, sin tokens, teléfono, PIN, IP, textos de direcciones o lat/lng.

## Criterio de aceptación
Apto para liberar cambios funcionales nuevos solo después de CI verde y prueba física Samsung con jornada real y ambas variantes Chrome/WebView. Si la agenda está libre pero la jornada no está activa o el GPS ya no es reciente, el resultado **no disponible** es correcto y no debe tratarse como fallo del motor de rutas.
