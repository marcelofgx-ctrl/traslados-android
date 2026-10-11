# Traslados — arquitectura modular, escalabilidad y compilaciones eficientes

## Integración adaptativa Mapa / Cliente / Supabase — 11/10/2026

- Backend [traslados-web PR #8](https://github.com/marcelofgx-ctrl/traslados-web/pull/8): `supabase/migrations/20261011_pickup_adaptive_demand.sql` aplicada en Supabase real con `pickupDemandActive` calculado únicamente para dispositivo autorizado por actividad ETA reciente (<75 s); Edge `pickup-eta` v5 activa, caché RAM 20 s por ruta verificada **después** de cuota SQL, CORS y verificación, sin entregar GPS del conductor al cliente.
- Android [PR #33](https://github.com/marcelofgx-ctrl/traslados-android/pull/33): `PickupPresenceCadence.java` módulo aislado (test Java independiente), `TrackingService` usa 25 s en ausencia de demanda, 15 s con movimiento reciente y demanda, 30 s detenido con demanda. GPS local sin cambios, opt-in revocable, ocupado/jornada/viaje conservados. Mapa RELEASE `R24.8` versionCode 50 firmada, certificada y publicada (run [38109157734](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38109157734)).
- PWA `pickup-cadence.js` y v40: disponibilidad 15 s en primer plano, ETA 25/45 s según `motionHint` del SQL, rangos reales coherentes con los 5 min de redondeo del Edge (e.g. 5–10 min). Tests Node, cache PWA offline y checks HTTP actualizados.
- Cliente [PR #34](https://github.com/marcelofgx-ctrl/traslados-android/pull/34): `Cliente Premium v15` versionCode 203, UA `TrasladosClienteNative/15`, misma WebView apuntando a GitHub Pages con diseño **idéntico**; workflow RELEASE nuevo con comparación SHA-256 de certificado respecto de R12 y publicación verificada ([run 38109359918](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38109359918)); workflow v14 histórico solo manual. CI DEBUG Android de regresión sin secretos.
- Instalación [PR #35](https://github.com/marcelofgx-ctrl/traslados-android/pull/35): PWA v42 actualiza link a APK v15 existente, reinicia caché SW; ningún cambio de CSS o apariencia. PWA 14 sigue reconocida para quienes la tengan; prioridad de instalación nativa preservada.
- **Límites pendientes:** prueba E2E física en Samsung y coche reales, variabilidad red/GPS y consumo de batería; CI valida código y firma, no demuestra latencia exacta de producción. Sin publicación de ubicación sin opt-in del conductor ni exposición de lat/lng a visitantes.

---


## Cierre UI plegable — PWA v38 y corrección v39 (11/10/2026)

- [PR #31](https://github.com/marcelofgx-ctrl/traslados-android/pull/31): `booking-steps.js` controla tres secciones expandibles de forma accesible, resúmenes de ruta, fecha y pasajero y barra de progreso sin borrar datos al plegar. Ajustes móviles/Montevideo-Canelones/resto del país; CSS premium v38 sin rehacer estética. Ambas suites PWA PASS; [Pages main v38 SUCCESS](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38107584523).
- [PR #32](https://github.com/marcelofgx-ctrl/traslados-android/pull/32): PWA **v39**. Cuando hay una parada sin coordenadas verificadas, la antigua ruta deja de mostrarse y se invalida su respuesta; al quitar paradas, se recalcula. El encabezado indica claramente qué falta. En urgencias se oculta el botón sin acción «Continuar con mis datos»; `Ahora` / `En 10 min` siguen siendo consulta, nunca una reserva automática. Dos CI de PR PASS. **La paleta, texturas, CSS v38, backend y las APK existentes se conservaron.**
- Verificación pendiente: prueba de interacción real desde Samsung y WebView Cliente, cambios entre modos, dirección exacta, paradas autenticadas, disponibilidad/ETA en vivo y confirmación conductor/pasajero; verificar Pages final del commit v39 antes de confirmar publicación. No confundir pruebas unitarias con E2E físico.

---



## Fase completada — transporte RPC web reutilizable (PWA v37)

- **Integrado:** [PR #30](https://github.com/marcelofgx-ctrl/traslados-android/pull/30), commit `7f8c1c5819766451144f256fd72b75dfa6280166`. PWA CI [38106796065](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38106796065) SUCCESS y revisión de UI [38106796048](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38106796048) SUCCESS; PWA de `main` [38106829513](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38106829513) SUCCESS. Pages: [38106829276](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38106829276) (comprobar resultado antes de asegurar despliegue).
- **Separación técnica:** `web-pasajero/rpc-client.js` contiene únicamente POST RPC Supabase, JSON, timeout 18 segundos, parseo de errores y respuesta. `app.js` conserva el guardia offline, nombre RPC, sesión, carga útil y presentación; `booking-contracts.js` conserva la estructura exacta de parámetros.
- **Seguridad operativa:** no repetir automáticamente una solicitud con respuesta incierta para impedir duplicados; no guardar tokens ni incorporar credenciales de servidor; el módulo no altera backend, privacidad de ubicación ni reglas de cotización.
- **Integración:** script `defer` antes de `app.js`, caché SW v37, verificación del empaquetado PWA y nueva prueba `tests/rpc-client-smoke.cjs`. El verificador de contratos de reservas deja de requerir una versión fija del JS para evitar bloqueos futuros.
- **Compatibilidad:** sin ajustes de diseño, CSS, Android, paquete, firma ni permisos. PWA v37 está integrada y compiló con CI; publicación Pages sujeta a verificación del run indicado. Cliente Premium v14 consume la web existente sin recompilar.
- **Pendiente en fase Android:** la biblioteca `:feature:driver` continúa sin migrarse. Realizarla por comparación de comportamiento y compilaciones firmadas, preservando `prepareEmbeddedConductor` hasta que las pruebas de notificaciones, burbuja, panel y firma sean equivalentes.


## Formulario PWA v38 — mejoras de diseño y aislamiento UI

El formulario de reservas ahora utiliza **tres tarjetas plegables** con cabeceras accesibles, indicadores de completitud y resúmenes que **no borran los valores** al contraerse. Se mantiene el diseño petróleo/dorado, la textura y la misma página aprobada. [PR #31](https://github.com/marcelofgx-ctrl/traslados-android/pull/31) fusionada en `main`.

- **Módulo UI nuevo:** `web-pasajero/booking-steps.js` administra apertura de ruta, fecha y pasajero mediante `window.TrasladosBookingSteps.mount(document)`. No contiene fetch, Supabase, GPS, tarifas ni llamadas de reservas.
- **Flujo progresivo:** inicia con Recorrido abierto; cuando origen y destino están seleccionados por primera vez, abre Fecha y pasajeros; botón explícito «Continuar con mis datos» abre el tercero en programadas. Cada cabecera se puede abrir/cerrar; al corregir un error de revisión se abre el paso correspondiente.
- **Resumen verificable:** ruta corta A→B; solo con motor ROAD válido aparecen km/min y, cuando existe, importe de referencia en una segunda línea compacta. Si cambia el recorrido, se retiran los valores previos. Fecha y hora se resumen con hora numérica 24 h; teléfono no se muestra en la cabecera.
- **Búsqueda/departamentos:** dos accesos rápidos Montevideo/Canelones y un selector con los otros 17; conserva motor IDE/POI y sus filtros. Buscador con ejemplo móvil más corto.
- **Ergonomía:** nombre y teléfono en una columna en teléfonos; separaciones menores, barra de progreso, respeto por `prefers-reduced-motion`; misma identidad visual premium.
- **Seguridad de negocio:** flujo programado conserva `booking-contracts.js` y `rpc-client.js` de PWA v37; «Ahora / En 10 min» continúan como consulta WhatsApp/ETA, no crean reserva; ningún cambio de firma ni compilación Android por esta revisión web.
- **QA:** tests `booking-steps-smoke.cjs` validan aria, plegado, estado, métricas y conservación; CI PWA y revisión de PR [SUCCESS](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38107520385) y [SUCCESS](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38107520398). GitHub Pages y dispositivo físico comprobar por separado.

**Pendiente:** revisar aspecto final en Samsung/Chrome y WebView Cliente Premium v14 y asegurar funcionamiento visible del selector, sugerencias y avance en un flujo real sin crear reservas de prueba. No inferir validación de navegador real únicamente desde pruebas DOM simuladas.

---

## Estado vigente — PWA v36 y medición Android (10/10/2026)

**Prioridad: código verificado en `main` frente a apuntes históricos de v33.** Las fases iniciales de modularización se ampliaron de forma incremental en cuatro PR adicionales.

| Fase | Integración | Evidencia |
|---|---|---|
| Direcciones | `web-pasajero/address-engine.js`, POI en `geo-search.js` | [PR #24](https://github.com/marcelofgx-ctrl/traslados-android/pull/24) |
| Reserva Android | Biblioteca Java `:core-reservations`; Mapa la consume sin modificar paquete Java | [PR #25](https://github.com/marcelofgx-ctrl/traslados-android/pull/25), [Mapa RELEASE firmada](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38105641839) |
| Rutas/precios PWA v34 | `route-engine.js` (Worker ORS, respaldo cache ROAD, validación vial, cotización orientativa TTL 5 min) | [PR #26](https://github.com/marcelofgx-ctrl/traslados-android/pull/26), [Pages SUCCESS](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38106169110) |
| Disponibilidad/ETA PWA v35 | `eta-engine.js` (cliente HTTP de Supabase; lógica 15 s estado, 30/60 s ETA y cuota permanece en `app.js`) | [PR #27](https://github.com/marcelofgx-ctrl/traslados-android/pull/27), [Pages SUCCESS](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38106334485) |
| Reservas PWA v36 | `booking-contracts.js` (datos puros para verificar agenda y crear reservas con/sin sesión; UI y RPC sin cambio) | [PR #28](https://github.com/marcelofgx-ctrl/traslados-android/pull/28), [Pages SUCCESS](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38106507081) |
| Rendimiento CI Android | `validate-modular-mapa.yml` cronometra Gradle, tareas cacheadas y publica perfil temporal, sin APK firmada | [PR #29](https://github.com/marcelofgx-ctrl/traslados-android/pull/29), [Java y Mapa DEBUG SUCCESS](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38106564106) |

**Medición inicial del workflow Android nuevo:** Gradle `assembleDebug` + `:core-reservations:classes` tardó **45 s** en la ejecución citada (Gradle imprimió «BUILD SUCCESSFUL in 44s»). Reutilizó desde caché `:core-reservations:compileJava`, `:mapatrayectos:prepareEmbeddedConductor` y `:mapatrayectos:compileDebugJavaWithJavac`. Es una medición DEBUG puntual, no comparar directamente contra RELEASE ni anunciar aceleraciones porcentuales. El workflow guarda un perfil Gradle como artefacto por siete días. La firma release de Mapa no se modificó en las fases web ni en la instrumentación de CI.

**Estado de interfaces:** el CSS, la estructura HTML premium, los textos del resumen, los flujos de reservas, la PWA oficial Pages y la APK Cliente Premium v14 (WebView de Pages) se mantienen. Los módulos JavaScript son scripts locales precargados en el service worker; `app.js` sigue coordinando UI, sesión, GPS del pasajero, cuotas, estado y acciones. Ni los contratos de backend ni la infraestructura Supabase/Cloudflare se migraron. No se crearon reservas de prueba reales.

**Pendiente antes de declarar equivalencia operativa completa:** instalación y pruebas físicas en Samsung de la PWA v36 y Cliente Premium v14, búsquedas IDE reales de Montevideo/Canelones, pasajero→conductor→respuesta, GPS/ETA estacionado/en movimiento, notificaciones/MP3 nativos, persistencia de datos al actualizar APK, firma y flujos Uber/Cabify. Las suites sintéticas y CI no sustituyen estas comprobaciones.

**Próximas fases:**
1. Continuar dividiendo `app.js` por responsabilidades reales cuando haya pruebas equivalentes; no crear módulos minúsculos por estética. Proteger especialmente calendario, autenticación y renderizado.
2. Tomar **3–5 ejecuciones comparables** de Gradle/CI y revisar dónde se consume el tiempo antes de activar paralelismo o reconstruir dependencias. Conservar `prepareEmbeddedConductor` mientras se define y prueba el reemplazo por Android Library `:feature:driver`. **No efectuar migración nativa masiva ni actualizar APK operativa sin equivalencia funcional, recursos, firma y pruebas reales.**
3. Mensajería/paquetería como dominio propio, separado de pasajeros, reutilizando motores de búsqueda, rutas y ETA solo cuando exista contrato claro y pruebas.

---

**Decisión de arquitectura, 10/10/2026.**
**Estado:** fases iniciales implementadas y validadas en GitHub; el resto permanece planificado. No se considera terminada toda la modularización.

## Implementación real verificada — 10/10/2026

- **Web Pasajero v33:** `web-pasajero/address-engine.js` abstrae búsqueda IDE, filtrado de calle/puerta/departamento, resolución de coordenadas y geocodificación inversa GPS mediante `window.TrasladosAddress.score/find/resolve/reverse`; `app.js` dejó de consultar directamente IDE. `geo-search.js` continúa como módulo POI. HTML, service worker y suite de pruebas actualizados; PR [#24](https://github.com/marcelofgx-ctrl/traslados-android/pull/24), dos suites de CI SUCCESS y despliegue GitHub Pages v33 confirmado.
- **Android:** nuevo módulo Gradle **`:core-reservations`** de biblioteca Java pura, con código original `QuickReservationLogic.java`, incluido por `mapatrayectos` como `implementation project(':core-reservations')`. Cambió la localización del archivo, no su paquete ni la API pública. Test JVM existente y compilación integral de Mapa DEBUG pasaron en dos revisiones; PR [#25](https://github.com/marcelofgx-ctrl/traslados-android/pull/25) integrado. El flujo RELEASE firmó, verificó certificado y publicó el APK tras la integración (ver ejecución de Build Mapa).
- **Optimización:** `org.gradle.caching=true` en proyecto principal y Cliente Premium; generación `prepareEmbeddedConductor` elegible para caché al conservar entradas/salidas declaradas. Workflow `validate-modular-mapa.yml` verifica JVM + Android en PR; workflow Mapa detecta cambios en `conductor/src/**`, `core-reservations/**` y scripts compartidos. El verificador de APK Cliente ya detecta versión web en vez de exigir v28.
- **Medición inicial, sin prometer aceleración porcentual:** última ejecución de la etapa previa a la segunda reestructura registró aprox. **1,4 min** Mapa RELEASE, **1,5 min** Conductor RELEASE y **1,2 min** Cliente Premium RELEASE. Hay que medir builds sucesivos comparables para comprobar cuánto mejora el tiempo. No confundir duración total de CI con compilación incremental.
- **Lo pendiente:** aislar rutas/ETA, tarifa y reservas de `app.js`; unificar geobúsqueda con backend compartido si beneficia a todas las plataformas; convertir el embedding Java de Conductor en biblioteca Android real con equivalencia de recursos, notificaciones y firma; métricas longitudinales de CI. No introducir modificaciones grandes sin pruebas firmadas.


## Principios acordados

El objetivo de modularizar no es solamente compartir funcionalidades entre Web Pasajero, APK Cliente Premium, Conductor y Mapa Trayectos. Debe dar **orden, escalabilidad, mantenimiento sencillo, aislamiento de fallos, reutilización, tiempos de compilación/CI menores y cambios verificables**. Evitar microservicios artificiales, dependencias cíclicas y módulos diminutos. Modularización **medida**, no estética.

### Diagnóstico comprobado en el repositorio

- `settings.gradle` principal incluye `:cliente`, `:conductor`, `:mapatrayectos` y `:core-reservations`, mientras **Cliente Premium** está en un proyecto Gradle independiente `cliente-premium/` con `:app`.
- `mapatrayectos/build.gradle` tiene `prepareEmbeddedConductor`, que copia y transforma fuentes Java y recursos de `conductor/src/main` en carpetas generadas. Hace posible incluir el panel Conductor en Mapa Trayectos, pero es un acoplamiento delicado; la transformación por texto y la dependencia del árbol completo complican una evolución modular y la compilación incremental. **No eliminar esta tarea abruptamente**: afecta la compilación actual, recursos, alertas y el APK ya instalado.
- `web-pasajero/geo-search.js` ya aísla parte de la geobúsqueda, y `web-pasajero/address-engine.js` concentra IDE, puerta y GPS inverso; `app.js` aún concentra UI, ETA, reservas, ruta y sesión. **Cambiarlo a trozos pequeños manteniendo API y UI existentes.**
- Los workflows Android usan `gradle/actions/setup-gradle@v4` (caché de Gradle), pero muchos workflows están versionados y verificados contra cadenas específicas. `verificar-cliente-premium-publica.yml` ya determina la versión publicada de manera dinámica. Mantener este criterio en todas las comprobaciones.
- `build-mapa-trayectos.yml` y `build-conductor-release.yml` compilan y firman APKs con rutas independientes. Cambios compartidos en `conductor/` también deberían hacer ejecutar validación de Mapa, porque Mapa integra esas fuentes, y el workflow de Mapa ya escucha los cambios de `conductor/src/**` y `core-reservations/**`.

## Arquitectura objetivo (sin cambiar infraestructura por cambiarla)

```
aplicaciones/
  web-pasajero        (interfaz, PWA, modo entrega futura)
  cliente-premium     (shell Android/WebView, permisos Android)
  conductor           (app independiente de respaldo)
  mapatrayectos       (app operativa GPS+Conductor integrado)
servicios-clientes/
  geo-search          (normalización, calles, puertas, departamentos, POI)
  routes              (rutas, kilómetros, minutos, caché)
  eta                 (disponibilidad, movimiento reciente, llegada estimada)
  bookings            (reservas, estado, agenda, presupuestos)
  messaging           (notificaciones y comunicación)
  pricing             (reglas de tarifas y referencia de presupuesto)
backend/
  Supabase            (RPC, base, Edge Functions, RLS, migraciones)
  Cloudflare Workers  (servicio vial y endpoints públicos)
futuro/
  shipments           (mensajería/paquetería como dominio separado)
```

Es un **mapa lógico**, no un mandato de crear todas esas carpetas o desplegar servicios separados ya mismo. El backend puede conservar Supabase y Cloudflare, con interfaces estables y módulos funcionales internos. No trasladar credenciales, secretos o coordenadas sensibles a la web.

### Android / Gradle: división por dependencia real

1. **`:core:model`** — contratos y modelos puros Java, sin Android, para que compile rápido y sea fácilmente testeable.
2. **`:core:network`** — clientes HTTP, validación de respuestas, errores y DTO compartidos donde realmente corresponda.
3. **`:core-reservations` (IMPLEMENTADO)** — lógica independiente de estados, fechas y conflictos del panel de reservas. Presupuestos completos siguen pendientes de extraerse.
4. **`:core:location`** — datos GPS y políticas de frescura (no reemplaza `TrackingService` ni consentimiento sin pruebas).
5. **`:feature:driver` (eventual)** — refactor más delicado: sacar funcionalidad Conductor del mecanismo de copia de Java y compartir un módulo Android Library normal entre standalone y Mapa. Requiere tratar cuidadosamente namespaces, recursos, manifest, alertas, servicio GPS, burbuja y firma.
6. **Módulos de aplicación existentes** permanecen ensamblajes separados. Cliente Premium sigue siendo WebView; no necesita copiar módulos nativos que no usa.

Criterio: dependencia siempre de aplicación hacia módulo compartido; nunca `core` hacia `app`. Un módulo solo se crea cuando tiene límites claros, pruebas propias y al menos una razón real de aislamiento/reutilización.

### Compilación y CI

- **Medición inicial**: registrar duración total y por etapa en CI (checkout, SDK, dependencias Gradle, generación, JavaCompile, empaquetado, firma, APK, publicación), para distinguir cuello de botella real antes de optimizar.
- **Caché Gradle**: conservar `gradle/actions/setup-gradle`; ensayar `org.gradle.caching=true` y caché de build con pruebas de APK reproducible/firma. `org.gradle.parallel=true` únicamente si hay tareas independientes suficientes; no asumir ganancia automática.
- **Configuración incremental**: inspeccionar `prepareEmbeddedConductor` y limitar inputs/outputs/dependencias; a largo plazo sustituir transformaciones de fuentes en cada construcción por biblioteca real. Evitar tareas que reescriban miles de archivos sin cambios.
- **CI por impacto**: web JS/CSS solo ejecuta pruebas PWA (y Pages), no Gradle; un módulo Android dispara solo pruebas y aplicaciones dependientes. **Cambios en Conductor deben validar también Mapa**, porque comparte su código. Publicar APK release solo con firma/versión y controles apropiados.
- **Versiones y tests sin valores pegados**: derivar versión web desde `index.html`/manifest y versión APK desde metadatos Gradle, no mantener greps `v28` cuando la actual sea `v32`. Mantener seguridad de certificados, hash, integridad y firma.
- **Compilaciones locales**: `./gradlew :modulo:tasks`, `--build-cache`, pruebas de unidad aisladas y compilación incremental; medir antes y después. La fragmentación extrema perjudica rendimiento y legibilidad.
- **Trazabilidad**: evitar duplicación creciente de workflows con nombres de release históricos; consolidar los activos en pipelines estables con parámetros y `workflow_dispatch`, sin borrar workflows viejos hasta confirmar desuso.

## Orden de migración recomendado

**Fase 0 — Línea base y protección (sin riesgos):** inventario de dependencias y artefactos; registrar tiempos de compilación en 3–5 ejecuciones comparables; pruebas de firma, reserva, ETA/GPS, búsquedas, rutas y disponibilidad. Identificar jobs redundantes e invariantes obsoletos. No modificar permisos GPS ni API.
  
**Fase 1 — Beneficio rápido (bajo riesgo):** CI selectiva y limpieza de verificadores hardcoded; mejorar caché y registros de tiempos; separar geobúsqueda exacta de `app.js` con contrato externo idéntico y tests (Montevideo/Canelones, calle y número, departamentos, POIs); no tocar motor de rutas en esta etapa.

**Fase 2 — Bibliotecas puras (riesgo bajo-medio):** compartir modelos, estado de reservas y validaciones; habilitar pruebas unitarias y build incremental. No convertir shell Cliente Premium en APK pesada.

**Fase 3 — Integración Android limpia (riesgo mayor):** extraer Conductor a `:feature:driver` sin perder ninguna alerta, pantalla, servicio ni recurso. Mapa seguirá siendo controlador único del GPS/burbuja. Solo retirar `prepareEmbeddedConductor` al pasar compilación comparativa, pruebas de funcionamiento, actualizaciones de APK y equivalencia de firma.

**Fase 4 — Nuevos dominios:** mensajería y paquetería como servicios `shipments`, separados de reservas de pasajeros, reutilizando geo, rutas, ETA, notificaciones y tarifas configurables sin duplicar implementación.

## Criterios de aceptación

- Misma interfaz visual premium, experiencia y comportamiento; no introducir regresiones de reservas, ruta, disponibilidad y ETA.
- Mismo package ID, versión creciente y certificado de firma para actualizar las APK existentes; no publicar builds de prueba como releases operativas.
- Cambiar el módulo geográfico no recompila Android nativo salvo que cambie código/dependencias que lo consumen.
- Cambiar las reglas de tarifas no obliga a modificar geobúsqueda ni GPS.
- Cada módulo tiene responsables, interfaces/documentación y pruebas de regresión.
- Comparar métricas de tiempo/fiabilidad pre/post; **no prometer porcentajes de aceleración sin mediciones**.
