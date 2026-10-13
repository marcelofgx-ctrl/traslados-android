# REANUDAR TRASLADOS — contexto vivo del ecosistema

> **LECTURA RÁPIDA EN NUEVA SESIÓN:** primero [ESTADO AUTOMÁTICO — VERSIONES Y BUILDS](ESTADO_AUTOMATICO_TRASLADOS.md), después este documento para decisiones, reglas de negocio, QA y pendientes. El estado técnico lo genera GitHub Actions sin recompilar. **No copiar manualmente números de versiones acá cada vez.**


> **Última revisión verificada: 13 de octubre de 2026, Uruguay (UYT).** Este documento es el **punto único de entrada** para una nueva conversación; el resto de `docs/` conserva historia detallada y puede contener etiquetas «ACTUAL» ya obsoletas. **Antes de ejecutar trabajo nuevo, verificar `main`, GitHub Actions, migraciones aplicadas y versiones publicadas.** No inferir que esta foto seguirá siendo vigente indefinidamente.

## Instrucción de reanudación para ChatGPT

**ÚNICA FRASE PARA COPIAR EN CUALQUIER CHAT:** **Retomar Traslados Web desde Git**.

**Qué significa esta frase:** recuperar el proyecto completo (Web/PWA + Cliente + Conductor + Mapa Trayectos + Supabase + Cloudflare) de `marcelofgx-ctrl/traslados-android` (`main`), usando primero el [estado técnico automático](ESTADO_AUTOMATICO_TRASLADOS.md) y después este documento. No interpretar «Web» como una orden de ignorar APK.

**Respuesta inicial obligatoria al retomar:** informar *primero* la **fecha y hora de Uruguay de la última compilación de aplicación verificada y el último cambio funcional**, indicando qué aplicación, resultado, commit y enlace; verificar que el registro sea vigente frente a GitHub y no confundir commits de documentación con modificaciones funcionales. Luego resumir brevemente pendientes y continuar el trabajo.

**Al finalizar una compilación o publicación sobre la que se informa al usuario** (correcta o fallida): señalar resultado real, versión, fecha/hora y enlace; dejar SIEMPRE al final, lista para copiar: **Retomar Traslados Web desde Git**. Esto es una instrucción para futuras respuestas de desarrollo, no una promesa de notificaciones en segundo plano.

**Secuencia obligatoria al empezar:** (1) leer ESTE archivo, (2) contrastarlo con los últimos commits y Actions de ambos repositorios, (3) ampliar solo los documentos técnicos pertinentes, (4) preguntarle al usuario qué comportamiento vio en Samsung si depende de QA físico. Ante una contradicción, **el código publicado y la base vigente prevalecen sobre la prosa histórica**. No inventar pruebas ni instalaciones.

## Repositorios y servicios canónicos

| Parte | Referencia verificada |
|---|---|
| Código Android + PWA + documentación | [`marcelofgx-ctrl/traslados-android`](https://github.com/marcelofgx-ctrl/traslados-android), rama `main` |
| Código backend, SQL y Worker | [`marcelofgx-ctrl/traslados-web`](https://github.com/marcelofgx-ctrl/traslados-web), rama `main` |
| Web/PWA del **pasajero** (única interfaz pública) | https://marcelofgx-ctrl.github.io/traslados-android/web-pasajero/ |
| Worker de rutas y utilidades, **no** reemplaza el frontend aprobado | https://traslados-web.marcelof-gx.workers.dev/ |
| Supabase productivo del proyecto | `zetaudvvutlouiqxopvg` (ID de proyecto, **no** clave) |
| Web PWA fuente | `web-pasajero/`; desplegada en GitHub Pages desde `main` |
| Android pasajero | `cliente-premium/` / WebView que carga la misma PWA; comprobar estructura exacta antes de editar |
| Conductor y Mapa | `conductor/` y `mapatrayectos/`; APK separadas con funciones complementarias |

## Corte de referencia histórico del 13/10/2026 (consultar estado automático vigente)

| Componente | Versión | Comprobación | Descarga / acceso |
|---|---|---|---|
| Pasajero PWA | **v68**, caché `traslados-cliente-pwa-v68` | [Pages SUCCESS](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38352735507) | [Abrir web](https://marcelofgx-ctrl.github.io/traslados-android/web-pasajero/) |
| Cliente Premium Android | **v15**, `versionCode 203`; WebView de PWA v68 | [paridad WebView SUCCESS](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38351814058) (código de PR) | [APK cliente v15](https://marcelofgx-ctrl.github.io/traslados-android/web-pasajero/downloads/traslados-cliente-premium-v15.apk) |
| Conductor Android | **11.5-R6**, `versionCode 128` | [RELEASE firmado SUCCESS](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38352100436), R5 conservada | [APK conductor R6](https://marcelofgx-ctrl.github.io/traslados-android/web-pasajero/downloads/traslados-conductor-r6.apk) |
| Mapa Trayectos Android | **0.1-R25.0**, `versionCode 52` | [RELEASE SUCCESS](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38352100294) | [APK mapa R25.0](https://marcelofgx-ctrl.github.io/traslados-android/web-pasajero/downloads/mapa-trayectos-r25.0.apk) |
| Backend Supabase | Políticas R1 y guards aplicados | [CI `traslados-web` SUCCESS](https://github.com/marcelofgx-ctrl/traslados-web/actions/runs/38352487078), PR [#15](https://github.com/marcelofgx-ctrl/traslados-web/pull/15), [#16](https://github.com/marcelofgx-ctrl/traslados-web/pull/16) | No requiere nueva APK para la lógica SQL |
| Worker de rutas | Recuperación ORS corregida y publicada manualmente | [Cloudflare SUCCESS](https://github.com/marcelofgx-ctrl/traslados-web/actions/runs/38348786259) | Endpoint de rutas a través del Worker |

Corte Git de referencia previo a este documento: Android `6de4cafd046a192b876129ff6f256206b96856f0`; backend `93e30286381319e5e0608b9e3e9c2590a7a3c05b`. **Son referencias históricas, no punteros permanentes:** siempre comprobar las cabezas actuales.

## Contrato funcional aprobado

1. **Solicitud programada:** cualquier fecha/hora futura si el conductor tiene activada la recepción de programadas; la franja y los días son **horario habitual orientativo**, no veto automático por defecto. La opción `allow_scheduled_any_time=true` está activa inicialmente; si se apaga, las solicitudes se limitan al horario/días configurados. Debe validarse también en backend tanto para invitados como para clientes registrados.
2. **Solicitud inmediata («Ahora» o «En 10 minutos»):** depende de jornada activa y no pausada, GPS reciente/preciso, conductor libre y ausencia de viaje incompatible. Con `allow_live_outside_hours=true` puede seguir recibiendo pedidos fuera del horario habitual mientras trabaja. Activar esa opción NO activa la jornada ni simula ubicación o disponibilidad.
3. **Ninguna solicitud equivale a reserva confirmada:** entra `PENDIENTE`; Conductor revisa la agenda, decide si enviar presupuesto; el pasajero lo acepta o rechaza. Solo entonces se confirma; el servidor revisa nuevamente choques, márgenes, bloqueos y reposicionamiento. No prometer un horario libre por recibir una solicitud.
4. **Vencimientos:** solicitudes inmediatas antiguas (>90 min) no admiten envío/aceptación de presupuesto; solicitudes programadas con hora ya pasada tampoco. Visual de caducidad en historial sin modificar silenciosamente filas antiguas.
5. **Paradas:** pasajero autenticado puede incluir paradas; Conductor R6 recibe la lista en orden y las incorpora a Google Maps. El presupuesto debe considerar la ruta TOTAL, no solo origen→destino directo.
6. **Tarifa:** la cifra pública es **orientativa** y puede carecer de peajes/extras; el conductor decide el precio definitivo que recibe el pasajero. No mezclar km del viaje, km hasta recogida, km GPS reales, ni tarifa aproximada vs. cotización firme.
7. **Privacidad:** no revelar lat/lng crudos del conductor a usuarios; ETA y distancia hasta recogida se calculan en servidor solo bajo autorización y control de cuota. No copiar PIN, tokens, claves privadas ni ubicación de personas a repositorios/logs/documentos.

## Últimos cambios realmente implementados

- [Android PR #69](https://github.com/marcelofgx-ctrl/traslados-android/pull/69): PWA **v68** y Conductor **R6**. Menú **Conductor → Horarios** con días, franja y dos controles: «Programadas a cualquier hora» e «Inmediatos fuera de horario con jornada activa». Mostrar todas las paradas y navegación con waypoints, aviso de presupuestar recorrido completo. Release R6 firmado con mismo certificado que R5, sin reinstalación obligatoria de Cliente v15.
- [Backend PR #15](https://github.com/marcelofgx-ctrl/traslados-web/pull/15) aplicada en Supabase: bandera de programadas e inmediatas fuera de horario, RPC `driver_set_availability_settings_v12`, función `schedule_request_policy_v1`, validación mediante trigger para invitados/registrados, `requestable` vs. `available` por separado, lista privada de paradas en RPC de Conductor. RPC antigua compatible para R5.
- [Backend PR #16](https://github.com/marcelofgx-ctrl/traslados-web/pull/16) aplicada: frena presupuestos programados enviados o aceptados cuando ya venció la hora, conservando autorización.
- [Android PR #68](https://github.com/marcelofgx-ctrl/traslados-android/pull/68): PWA v67 corrigió espera de ETA (hasta 18 s), concurrencia e invalidaciones; eliminó la leyenda redundante bajo auto animado, mantuvo animación verde. La v68 conserva esto.
- [Backend PR #14](https://github.com/marcelofgx-ctrl/traslados-web/pull/14): ORS presupuesto 5,2 s más caché ROAD 1,2 s dentro de timeout Edge 7,5 s; errores temporales con reintento limitado; sin falsear distancia en línea recta. Worker publicado manualmente y ruta de control HTTP 200 verificada; **ETA real de recogida en Samsung aún no demostrada después del fix**.

## Pruebas realizadas vs. pendientes

**Sí verificado:** CI web, modular, compilación Android DEBUG, builds RELEASE, firma Conductor R6, Pages PWA, compatibilidad WebView; pruebas SQL transaccionales con `ROLLBACK`: programada a las 23:30 fuera de horario 08–22 recibida como solicitud, doble envío sin duplicar, cambio de presupuesto `PENDIENTE → ACEPTADA`, opción de recepción fuera de horario desactivada rechaza la solicitud, y protección contra aceptar presupuesto vencido. Sin reservas falsas persistidas por esas pruebas.

**NO verificado aún (no afirmar que está resuelto):** validación táctil de todas las pantallas en Samsung real; guardar y recargar las dos opciones nuevas de Horarios en Conductor R6; flujo bidireccional real PWA/Cliente→Conductor→presupuesto→aceptación→en camino→en punto→en viaje→finalizada; disponibilidad y ETA concreta conductor→pasajero tras fix ORS, tanto navegador como WebView; ruta y presupuesto con paradas en un viaje verdadero; sonido/vibración/popups con distintos estados. Push **FCM con cliente cerrado** sigue pendiente.

## Deuda técnica y siguiente plan priorizado

1. **QA física Android:** instalar Conductor R6 (no borrar datos; actualizar con misma firma), entrar a Horarios y probar guardado de preferencias; cotejar con pantalla pública del pasajero. Verificar Mapa R25.0 y Cliente v15.
2. **E2E controlado:** probar un pedido programado dentro y fuera de franja, usuario invitado y registrado, propuestas de precio y transiciones sin molestar al conductor ni clientes verdaderos; avisar y obtener autorización antes de insertar datos reales de prueba. Confirmar choques de agenda.
3. **ETA hasta recogida:** registrar resultado funcional del Worker/Edge, revisar errores de ORS sin exponer coordenadas; no confundir distancia del viaje origen→destino con distancia conductor→origen.
4. **Estadísticas del recorrido:** hoy **no se guardan de forma uniforme** km/min orientativos al presentar nuevas solicitudes; definir campos confiables con origen y hora de cálculo y garantizar que el presupuesto definitivo lo decide Conductor. Revisar caso histórico de viaje finalizado con km=0, sin reescribir datos sin permiso.
5. **Notificaciones cerradas:** FCM real para Cliente; actualmente avisos dentro de PWA abierta y posibilidad de vibración/sonido dependiendo del dispositivo. No prometer push cerrado.
6. **Paquetería/mensajería:** aparece en portada como «próximamente», no crear reservas de bultos bajo contratos de pasajeros. Consolidar producto y decidir después la versión `1.0.0`; fue discutida pero no adoptada formalmente para toda la suite.

## Reglas de intervención y publicación

- Preservar estética premium petróleo/grafito/champagne, texturas, medallas/íconos orbitales y UI plegable; no alterar por accidente otras apps.
- **Solo la PWA de GitHub Pages es interfaz pasajero**; Cloudflare Worker complementario, no sustituir frontend ni crear segundo formulario divergente.
- Cambios SQL con migración versionada en `traslados-web/supabase/migrations/`, CI y verificación explícita de aplicación a producción. Cambios a Worker necesitan workflow manual de publicación; CI/merge ≠ deploy.
- Cambios Android firmados requieren comprobar `versionCode` creciente, certificado/keystore existente y build SUCCESS; no publicar o afirmar APK nueva sin verificar archivo real; conservar R5 y versiones anteriores como rollback. Cliente WebView consume PWA sin recompilar APK salvo cambios nativos.
- Las pruebas automáticas NO equivalen a prueba física. Evitar insertar pruebas persistentes en producción, enviar avisos reales sin autorización o alterar reservas existentes sin consentimiento.
- Al cerrar una fase aprobada: las **versiones/links/Actions** se actualizan automáticamente en [ESTADO_AUTOMATICO_TRASLADOS.md](ESTADO_AUTOMATICO_TRASLADOS.md). Solo actualizar **este** documento cuando cambien decisiones, lógica funcional, QA o pendientes; no reescribir historia ni obligar a generar documentación extra en cada commit. Para retomar otra sesión basta con «Retomar Traslados Web desde Git» y leer ambos documentos.

## Documentos para ampliar únicamente cuando haga falta

- [Contexto maestro histórico y decisiones](CONTEXTO_MAESTRO_PROYECTOS.md)
- [Flujo completo pasajero/conductor](FLUJO_MAESTRO_TRASLADOS_CLIENTE_CONDUCTOR.md)
- [Horarios flexibles: alcance y pruebas](HORARIOS_FLEXIBLES_PWA68_CONDUCTOR_R6.md)
- [Arquitectura modular/compilación](ARQUITECTURA_MODULAR_Y_COMPILACION.md)
- [Retomar exclusivamente Mapa Trayectos](RETOMAR_MAPA_TRAYECTOS.md)
- [Backend y reparación de ORS](https://github.com/marcelofgx-ctrl/traslados-web/blob/main/docs/PICKUP_ETA_PROVIDER_RECOVERY_20261013.md)
