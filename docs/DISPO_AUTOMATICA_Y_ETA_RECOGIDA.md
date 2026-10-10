# Traslados — disponibilidad automática por jornada, conducción reciente y ETA de recogida

## Corrección de interfaz — disponibilidad pública sin iniciar sesión (10/10/2026)

**Reclamo originado por captura Samsung:** Pasajero web v12 mostraba «Iniciá sesión en Mi cuenta para consultar la llegada aproximada». El conductor aclaró que **su disponibilidad en vivo se toma automáticamente de Mapa Trayectos**, y no debe depender de que el visitante tenga una cuenta para conocer si está activo. Separar dos preguntas:

- **¿El conductor está aceptando consultas ahora?** Respuesta pública sí/no basada exclusivamente en heartbeat voluntario de Mapa, sin exigir login.
- **¿A qué distancia/tiempo está del origen específico A?** Requiere cuenta Cliente válida, ruta ORS y coordenada privada solo en servidores. Esto evita que visitantes anónimos prueben muchas ubicaciones para deducir posición del vehículo.

**Backend real aplicado a Supabase operativo:** [`public_driver_availability_v1.sql`](https://github.com/marcelofgx-ctrl/traslados-web/blob/main/operativa/migrations/20261010034000_public_driver_availability_v1.sql), commit `0c8d669`. RPC SECURITY DEFINER `public_driver_availability_v1()` devuelve solamente `available:boolean`, `status:"available_for_requests"|"not_available"`, `confirmationRequired:true`. Exige autorización previa, jornada activa, no pausada, no viaje activo, no ocupado, GPS/heartbeat ≤90 s, conducción ≤5 min, precisión ≤45 m y ausencia de conflicto de agenda durante ventana conservadora de 60 min. **No retorna coordenadas, ID dispositivo, horarios, rutas ni datos del pasajero**. Pruebas SQL: anon EXECUTE=true para esta RPC, SELECT=false sobre `driver_live_presence`, EXECUTE=false para RPC privada `driver_pickup_eta_context_v1`.

**Estado verificado:** la tabla de presencia contenía **0 dispositivos autorizados** durante la auditoría. Por lo tanto la respuesta real actual es `not_available`. **No** afirmar que se lee una jornada del usuario en vivo antes de que instale Mapa R24.5, dé consentimiento mediante PIN, inicie jornada y circule con GPS reciente.

**PWA GitHub Pages v13:** `web-pasajero/app.js` ahora siempre consulta el estado público antes del login en modos Ahora/10 min, cache de 25 s y refresco mientras la página está visible cada 40 s. Si activo: «Conductor disponible para consultas» sin exigir cuenta. Si hay sesión y origen elegido: además consulta la Edge privada ETA. Si no hay sesión: informa disponibilidad pública, sin inventar km/min ni mostrar GPS. Si no está autorizado o hay señal caducada: «No disponible». `styles.css` añade distinción discreta de estado, cache SW v13, HTML `app.js?v=13`. Test VM [`public-driver-presence-smoke.cjs`](../web-pasajero/tests/public-driver-presence-smoke.cjs) ejecuta funciones reales y comprueba visitante anónimo, ocupado, sesión y privacidad. CI [38020967639](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38020967639) **SUCCESS**, Pages [38020967392](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38020967392) **SUCCESS**.

**Web Premium Cloudflare:** `src/components/DriverPickupEta.tsx` consume `public_driver_availability_v1()` sin login y habilita cálculo por origen solo con sesión cliente; CI Web [38020875344](https://github.com/marcelofgx-ctrl/traslados-web/actions/runs/38020875344) **SUCCESS**, sin publicación nueva del Worker Cloudflare. No confundir CI verde con Web Premium actualizada en producción.

**Cliente Android nativo:** `ClienteTripEnhancements.showPickupEta` consulta estado público aunque `sessionToken` sea vacío; con origen/sesión solicita ETA por Edge privada y conserva WhatsApp/confirmación manual. Se generó la versión **11.5-R12, versionCode 122** y desactivó la compilación automática obsoleta de R11 (queda manual). Hubo fallos intermedios por asignación `final` en Java corregidos en commit `be1d8e9`. **RELEASE R12 firmada CI SUCCESS [38021100002](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38021100002)**, artefacto `11658906107`, SHA256 APK `ac4dc6ae06705d9764fc25333f7b876f10ea8ab8586c7c6dac471bb26e8545c4`, firma v2 validada y certificado estable `d91f4b9a37f4c77653fdf18fe792e7011a046dd2c09fded1e13fd29d4267269d`. R11 es una versión anterior; no confundir.

**Mapa Trayectos** no precisó nueva compilación para esta corrección: mantiene **R24.5** con opt-in/heartbeat/ocupado. Solo cambió el consumidor de estado en backend y clientes. **Prueba positiva real con el coche y Samsung sigue pendiente.**

---

## Implementación integrada (10/10/2026) — diferenciar compilación y uso real

**Backend Supabase OPERATIVO** `zetaudvvutlouiqxopvg`, migración aplicada y versionada en
[`traslados-web/operativa/migrations/20261010023000_pickup_live_presence_v1.sql`](https://github.com/marcelofgx-ctrl/traslados-web/blob/main/operativa/migrations/20261010023000_pickup_live_presence_v1.sql):

- Tabla `driver_live_presence` (singleton, lat/lng PRIVADAS con RLS sin SELECT anon/auth). La tabla no tiene datos mientras el usuario no autorice el seguimiento desde Mapa.
- `mapa_presence_consent_v1` vincula exclusivamente el dispositivo Mapa autorizado con PIN del conductor; OFF por defecto y revocable. La posición se borra al desactivar.
- `mapa_presence_ping_v1` requiere el secreto de dispositivo, guarda solo GPS preciso ≤45 m y reciente ≤90 segundos; exige jornada activa/no pausada, sin viaje activo ni «ocupado», y movimiento confirmado ≤5 minutos.
- `driver_pickup_eta_context_v1` NO ejecutable por anon/auth, solo por service_role: comprueba sesión real del cliente, límite atómico 3 solicitudes/min y 20/h, conflictos de agenda en hora actual y devuelve coordenadas solamente al servidor Edge.
- API **Supabase Edge Function `pickup-eta` v1 ACTIVA**, fuente versionada en [traslados-web/operativa/edge-functions/pickup-eta/index.ts](https://github.com/marcelofgx-ctrl/traslados-web/blob/main/operativa/edge-functions/pickup-eta/index.ts). Requiere sesión cliente de la cuenta (verifica mediante SQL; `verify_jwt=false` solo porque utiliza autenticación propia por PIN), consulta ORS por Cloudflare servidor-servidor y responde solo kilómetros redondeados al medio km, minutos redondeados a 5 y antigüedad de lectura. **Jamás devuelve las coordenadas del conductor**; sin sesión respuesta HTTP 401. Pruebas de seguridad [GitHub Actions 38019205391](https://github.com/marcelofgx-ctrl/traslados-web/actions/runs/38019205391) SUCCESS: API pública con sesión inválida rechazó HTTP 401, no entregó GPS; tests de permisos.
- Se corroboró en Supabase que `anon` NO tiene SELECT en la tabla de presencia ni EXECUTE en la RPC que retorna coordenadas y que `service_role` sí lo tiene. No se crearon reservas de prueba.

**Android Mapa** código nuevo en `TrackingService.java`, `Api.java`, `MainActivity.java`, **R24.5 / versionCode 47**. En **Menú → Disponibilidad de recogida** ofrece activar con PIN (opt-in), «Ocupado (Uber/Cabify)» y desactivar/borrar ubicación. Nunca almacena PIN. Envía heartbeat en hilo independiente aprox. cada 25s mientras jornada activa y en eventos de pausa, inicio/fin viaje, fin jornada y cambio de disponibilidad. Conserva SQLite, MP3, firma de Mapa. CI release firmada en [38018783116](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38018783116) SUCCESS; para la entrega con nombre de artefacto corregido **R24.5**, revisar último run [38019254898](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38019254898). **No se activó GPS del usuario ni se hizo prueba real de ubicación Samsung**.

**Web Pasajero PWA GitHub Pages v12:** código de `web-pasajero/app.js` consulta `/functions/v1/pickup-eta` cuando el usuario inicia sesión, marca origen y elige «Ahora / En 10 min». Polling máximo 75s y deduplicación cliente 65s; conserva alternativa WhatsApp y no crea reserva inmediata inválida (lead_time_min=30). CSS petróleo/champagne, URLs JS/CSS v12 y service worker v12. [CI PWA 38019114673](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38019114673) SUCCESS y Pages [38019114312](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38019114312) SUCCESS. Prueba visual real de Samsung pendiente.

**Web Premium Cloudflare:** `src/components/DriverPickupEta.tsx` y página `src/routes/index.tsx`, incluyendo selección de modo «Ahora/10 min»; usa exactamente la API Edge central, CORS restringido y número sin posición GPS. CI [38019205391](https://github.com/marcelofgx-ctrl/traslados-web/actions/runs/38019205391) SUCCESS. **PENDIENTE de desplegar la NUEVA versión del Worker en Cloudflare**: el hecho de tener el código en GitHub y CI verde no actualiza automáticamente ese Worker. La clave ORS ya operaba para rutas A→B, no sustituye publicar el frontend nuevo.

**Cliente Android nativo 11.5-R11 / versionCode 121:** se recompila firmado con los antiguos sources recuperados y patch `cliente-pasajero/scripts/build_cliente_r11.py`. En «Ahora» y «+10 MIN» abre un diálogo de recogida, consulta la misma Edge Function con sesión y origen seleccionados, muestra km/min o estado, y ofrece WhatsApp. **No crea una reserva inválida para menos de 30 min**. [Actions 38019069484](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38019069484) **SUCCESS**, APK RELEASE firmada; prueba Android física pendiente. R10 legado está marcado para ejecución manual y no debe confundirse con R11.

### QA pendiente antes de declarar «en vivo probado»

1. Instalar R24.5 RELEASE sobre R24.4 (sin desinstalar, manteniendo firma y datos), abrir Mapa, menú → Disponibilidad de recogida → activación con PIN, iniciar jornada, circular de modo normal con GPS. **El asistente NO ha activado la compartición de ubicación por su cuenta**.
2. Desde otro dispositivo/navegador con cuenta Cliente iniciada, elegir A y «Ahora». Debe verse el estado real y km/min aproximados, sin posición cruda ni promesas de servicio aceptado.
3. Probar fin de jornada, pausa, ocupado Uber/Cabify, viaje activo, pérdida GPS (90s), inmóvil más de 5min, segundo plano, red móvil y cambio de usuario. En todos los casos no elegibles, ocultar ETA.
4. Probar conflicto por reserva programada, 10 minutos, tráfico/quota ORS, cliente sin sesión, demasiadas consultas. Ninguna acción debe crear una reserva real sin confirmación.
5. Publicar Cloudflare Premium mediante flujo manual con autorización y credenciales seguras; luego hacer prueba Samsung y actualización del contexto maestro. Conductor independiente solo administra reservas/presupuestos; el GPS se origina desde Mapa, no desde la aplicación independiente.

---


**Acordado conceptualmente:** 09/10/2026, tras captura del formulario de recogida «Ahora / En 10 minutos». **Estado:** especificación técnica verificada contra el código; no se implementó aún el canal de presencia comercial ni la respuesta ETA en producción. Contexto único: [CONTEXTO_MAESTRO_PROYECTOS.md](CONTEXTO_MAESTRO_PROYECTOS.md).

## La regla simple que pidió el usuario

Mientras **Mapa Trayectos tenga jornada iniciada, actividad de conducción reciente y ubicación GPS válida**, el sistema debe considerar al conductor **en actividad y potencialmente disponible** y mostrar al pasajero, cuando elija A:
- kilómetros **por carretera** desde **ubicación actual del Conductor → origen A** (no A→B);
- minutos estimados para llegar a recogerlo;
- estado honesto «Disponible para consultas / Sujeto a confirmación» o «Ocupado / No disponible»;
- actualización automática, sin tener que introducir manualmente kilómetros.

Esto debe integrarse con **reservas programadas**, no asumir que una agenda vacía significa ausencia de viajes Uber/Cabify.

## Señales comprobadas en Mapa

`mapatrayectos/src/main/java/uy/com/mapatrayectos/TrackingService.java` ya dispone de:
- `shiftActive`: jornada iniciada/terminada.
- `shiftPaused`: jornada pausada/reanudada.
- `tripActive`: viaje registrado en Mapa (no observa otros proveedores).
- `vehicleMoving`, `filteredSpeed`, `lastAcceptedTs`, `lastAcceptedAccuracy`, `lastAccepted`: conducción y GPS validados por el filtro de ruido de Mapa.
- `broadcastState()`: propaga `shift_active`, `shift_paused`, `trip_active`, `vehicle_moving`, `has_location`, `lat`, `lon`, `accuracy`, `location_age_ms`.
- La sincronización histórica existente de `Api.syncShift/syncTrip` NO publica ubicaciones activas en tiempo real. **No reutilizar puntos de viajes finalizados como ubicación actual**.

## Política V1 propuesta para implementar

1. **Consentimiento una vez:** control visible «Compartir disponibilidad para recogidas» en Mapa (desactivado por defecto y revocable). La app conserva la elección localmente. Iniciar jornada NO debe habilitar ubicación comercial sin esa autorización previa.
2. **Heartbeat autenticado** a una tabla nueva privada `driver_live_presence`, cada ~20–30 s con jornada iniciada y permiso activado. No exponer lat/lon mediante SELECT/RPC públicas. Vincular **PIN de conductor válido** con dispositivo autorizado; la identidad actual `mapa_trayectos_devices` no certifica conductor.
3. **Condiciones de disponibilidad:** jornada activa, no pausada, no `tripActive`; GPS con antigüedad máxima de **90 s**, precisión de hasta **45 m** y última conducción comprobada ≤**5 min**. No exigir movimiento actual: detenido en un semáforo o esperando al pasajero no equivale a desconectado. Además, sin bloqueos de agenda o un servicio programado que impida alcanzar el origen A. No presentar como certeza una heurística para Uber/Cabify.
4. **Ocupado manual** de un toque para Uber/Cabify u otro traslado externo; prevalece sobre todos los indicadores automáticos. Al terminar ese viaje vuelve a evaluar la regla automática, sin borrar datos.
5. **Caducidad servidor:** sin heartbeat actualizado por 90 s, al cerrar/pausar jornada, falta de GPS, viaje registrado activo o desactivación del permiso, estado **no disponible** y retirada inmediata de ETA pública.
6. **Servidor de ETA privado:** solo el backend conoce la coordenada del Conductor; invoca el motor ORS **server-to-server** (clave privada actual de Cloudflare) para ruta Conductor→A. Devuelve al pasajero únicamente `available`, `distanceKm`, `etaMin`, `locationAgeSeconds` y mensaje de estado. Sin coordenadas, trayecto histórico ni PIN; sin estimación aérea ni valor de prueba. Proteger el acceso entre Cloudflare y Supabase con identidad de servidor de alcance mínimo y límites por usuario/origen/caché; **no entregar coordenadas mediante RPC anónima**.
7. **Reserva programada:** mostrar compatibilidad de agenda; solo ofrecer ETA de posición ACTUAL al pasajero si pide recogida inmediata, nunca extrapolar esa posición para una reserva futura. «Ahora / En 10 min» sigue como **solicitud sujeta a aprobación**: la fecha/horario exactos necesitan un nuevo flujo de aceptación inmediata porque Supabase actual exige `lead_time_min=30` para reservas programadas.
8. **Comportamiento seguro:** si el usuario está conduciendo en Uber/Cabify y no marcó «Ocupado», puede aparecer potencialmente disponible aunque no lo esté; por eso mostrar «Disponible para consultas» y **nunca** «Confirmado» antes de su aceptación. Notificaciones y consulta no implican reserva.

## Experiencia visual compacta

Cuando el pasajero marque origen A y elija «Ahora» o «En 10 min», incluir una línea destacada integrada en la tarjeta de horario, sin caja gigante repetida:

> **Conductor en actividad · disponibilidad a confirmar**  
> **3,8 km · 8 min para llegar** *(solo ejemplo visual; publicar exclusivamente el resultado REAL del motor)*  
> Solicitar recogida

Si sin heartbeat fresco: «Conductor sin ubicación de recogida disponible. Consultar por WhatsApp». Si ocupado: «Conductor ocupado. Elegir otra hora». Diseño petróleo/champagne existente, textos breves, nada de coordenadas exactas en la web.

## Plan de entrega y QA

**P0:** tabla privada, RPC publicación autenticada, consentimiento persistido, heartbeat con apagado; simulaciones de cierre/pausa/jornada activa y verificación de permisos. **P1:** puente de servidor seguro para ORS y ETA, más frontend PWA y Premium. **P2:** cruces con reservas programadas, ruta previa al próximo servicio, ocupación externa y solicitudes urgentes admitidas por conductor. Verificar en Samsung movimiento, estacionamiento, pantalla bloqueada, datos sin señal, reinicio y degradación GPS; compilar Mapa RELEASE conservando firma, SQLite, PIN y MP3.

**Estado al documentar:** ninguna tabla/lat/lon en vivo creada, ninguna APK compilada para esta funcionalidad, ningún ETA público activo. La revisión fue de diseño, no una entrega de seguimiento GPS.
