# CONTEXTO MAESTRO — ECOSISTEMA TRASLADOS / MAPA TRAYECTOS

**Corte comprobado:** 09/10/2026, auditoría posterior de repositorios, artefactos y CI (America/Montevideo).
**Carácter:** documento de inicio de sesión, vivo y versionado en GitHub.
**Repositorio canónico de coordinación:** `marcelofgx-ctrl/traslados-android`, rama `main`.
**Segundo repositorio:** `marcelofgx-ctrl/traslados-web`, rama `main`.
**Regla de confianza:** este archivo fija el estado conocido en el momento del corte. **Antes de cualquier cambio**, consultar commits, código, Actions, página pública y backend actuales. Ante contradicciones, gana el código/estado verificado más reciente, y este documento se actualiza.

> **PROMPT PARA PEGAR EN CUALQUIER NUEVA SESIÓN**
>
> «Retomá el ecosistema **Traslados / Mapa Trayectos** desde el contexto maestro:
> https://github.com/marcelofgx-ctrl/traslados-android/blob/main/docs/CONTEXTO_MAESTRO_PROYECTOS.md.
> Leé el documento, comprobá los últimos commits y GitHub Actions de `traslados-android` y `traslados-web`, y revisá las referencias técnicas allí enlazadas antes de cambiar nada. Trabajá sobre los repositorios reales, no sobre muestras ni datos inventados. Separá IMPLEMENTADO / COMPILADO / PUBLICADO / PROBADO EN SAMSUNG. No borres datos, viajes, PIN, sonidos o reservas. Conservá el diseño premium aprobado. Al terminar actualizá el contexto maestro con lo que cambió y su evidencia».

---

## 0. ACTUALIZACIÓN DE AUDITORÍA CI Y VERSIONES — 09/10/2026 (posterior al corte inicial)

**Fuentes verificadas:** `main` de `traslados-android` y `traslados-web`, logs de GitHub Actions, jobs, artefactos y módulos `conductor/`, `mapatrayectos/` y workflows. Esta auditoría **no modificó la lógica de reservas, el esquema Supabase ni el diseño visual de producción**. Clasificación estricta:

| Producto | Código real | Compilación comprobada | Publicado / probado |
| --- | --- | --- | --- |
| **Mapa Trayectos + Conductor integrado** | R24.3 / code 45 en `mapatrayectos/` | RELEASE firmada [37924921923](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/37924921923) SUCCESS | APK disponible; prueba física definitiva Samsung y flujo completo Supabase **pendientes**. |
| **Conductor independiente** | `conductor/` 11.5-R1 / code 123 | **RELEASE firmada** [37924922002](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/37924922002) SUCCESS (artefacto 11613278802). **DEBUG desde fuente** [38009749431](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38009749431) SUCCESS (artefacto 11652782972) | No hay QA Samsung confirmado. Pantalla de demanda **BETA con datos simulados**; no publicitar datos en vivo. |
| **Cliente Android nativa** | 11.5-R10 en su workflow RELEASE | [37945735891](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/37945735891) SUCCESS, APK firmada; artefacto 11624055221 | QA Samsung y reserva real end-to-end **pendientes**. |
| **PWA Pasajero Pages** | `web-pasajero/` con índice de POIs | Smoke público [38008909648](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38008909648) SUCCESS y Pages [38008909342](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38008909342) SUCCESS | Publicación de esas revisiones comprobada por CI; envío real de reserva, teclado/recorrido y estados **pendientes en móvil**. |
| **Web Premium Cloudflare** | `traslados-web/main` [152298f](https://github.com/marcelofgx-ctrl/traslados-web/commit/152298fcb1300d1fcf68ef3c1bd354bc76de7584) | CI [38008884026](https://github.com/marcelofgx-ctrl/traslados-web/actions/runs/38008884026) SUCCESS | CI **no equivale por sí solo** a despliegue nuevo ni prueba de flujos de Cloudflare; validar sitio y Workers. |

**Diagnóstico del error Conductor:** el workflow antiguo `build-conductor.yml` decía **v9.2** y recompilaba un paquete histórico tar/base64 en vez del árbol actual. Falló repetidamente (último observado [38009749433](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38009749433)); en [38008909682](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38008909682) el compilador reportó `org.maplibre.android.* does not exist` al compilar `DemandMapActivity` con dependencias antiguas. **No significa que Mapa R24.3 ni Conductor 11.5-R1 RELEASE fallaran.** Retirado YAML obsoleto de `main` en [1ca663d](https://github.com/marcelofgx-ctrl/traslados-android/commit/1ca663d6ac76c39b85dd1901221d6fcfea95f0ec); los runs históricos quedan en Actions. El workflow DEBUG de fuente actual se corrigió en [6c3421f](https://github.com/marcelofgx-ctrl/traslados-android/commit/6c3421f41d02f633f24fe0449c710cdbbc5d87b2) y produjo el artefacto correcto. **No instalar DEBUG encima de RELEASE sin comprobar firma/datos.**

**Cliente legado v9.0:** el job `Build Traslados Cliente v9.0` compilaba otra APK DEBUG desde tar histórico en **cada** push; que figurase SUCCESS no era una nueva versión de Cliente 11.5-R10. Se retiró su YAML `build-apks.yml` de `main` en [3fe7ad0](https://github.com/marcelofgx-ctrl/traslados-android/commit/3fe7ad0396d0f3e6ac41bad49b72e26c2c2b1cc3). Sus referencias históricas fijadas por SHA siguen accesibles desde Git; no se modificó la release. Avisar si se reintroducen trabajos obsoletos antes de habilitar ejecuciones automáticas en cada push.

**Documentación especializada nueva:** [Conductor independiente — CI, release, beta y error antiguo](CONDUCTOR_INDEPENDIENTE_CI.md) y [Cliente Android — releases y CI](CLIENTE_ANDROID_CI.md). Esta fuente queda como **único contexto maestro transversal**; arquitectura de rutas, GPS, Web Cloudflare, Mapa y PWA siguen en sus documentos técnicos propios.

**Cambios efectuados en esta tanda (sin tocar producción):** `6c3421f` (workflow DEBUG fuente actual), `1ca663d` (retirar Conductor v9.2 legado), `3fe7ad0` (retirar Cliente v9.0 legado), `a66a746` y `24a0c15` (documento especializado Conductor y evidencia de SUCCESS), `686ac86` (documento especializado Cliente). **Al modificar solamente CI/documentación no se generó nueva RELEASE ni nueva funcionalidad de pasajero.**

**Verificación posterior de publicación del contexto:** [Pages 38009930301](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38009930301) finalizó **SUCCESS** sobre el commit [0ff5e8c](https://github.com/marcelofgx-ctrl/traslados-android/commit/0ff5e8cb20917e5a743c85cd57796751b4d1077f) que actualiza este contexto. Corrobora ejecución de despliegue de GitHub Pages, no prueba visual ni envío real. En esta comprobación adicional las URLs públicas no pudieron inspeccionarse mediante el navegador de consulta; **no** inferir indisponibilidad del servicio a partir de ese límite de herramienta. Usar el smoke público y pruebas en Chrome/Samsung.

**Pendiente P0:** ensayo consentido de **una sola** reserva real PWA/Cliente → Mapa Conductor → presupuesto → respuesta → historial; QA de IME Samsung en Mapa R24.3; verificar motor rutas/ORS y presencia comercial antes de ofrecer «Ahora / En 10 min» como disponibilidad garantizada. El API Cloudflare y la web pública no fueron probados de punta a punta durante esta auditoría, aunque sus CI más recientes revisados estaban verdes.

---

## 0B. NUEVO AVANCE — MOTOR DE RUTA Y TARIFA ORIENTATIVA (09/10/2026)

**Pedido:** tras elegir A (origen) y B (destino) mostrar km por calles, duración de viaje y **valor de referencia**. Tarifa parametrizable interna inicial de **40 UYU/km** (8 km → $320). El Conductor dentro de Mapa envía **siempre** el importe final, independiente del estimador. Por separado, para «Ahora» y «En 10 min» se quiere mostrar km y ETA **Conductor→A**, no confundirlos con A→B.

**Realizado en código (sin afirmar GPS live ni Cloudflare publicado):**
- Repositorio **traslados-web**, cambios motor en commit [f38a607](https://github.com/marcelofgx-ctrl/traslados-web/commit/f38a607cd21fb388254f0465b69fb8862a5770d6): `/api/public/route-estimate` calcula `referenceFareUyu` solo cuando ORS devuelve distancia por calles; parámetro interno Worker `FARE_REFERENCE_UYU_PER_KM` (por defecto 40), redondeo $10, sin peajes/esperas/extras y sin venderlo como oferta final. Cotización de Conductor/Supabase sigue intacta. Componentes [BookingQuickSummary](https://github.com/marcelofgx-ctrl/traslados-web/blob/main/src/components/BookingQuickSummary.tsx) y [RoutePreview](https://github.com/marcelofgx-ctrl/traslados-web/blob/main/src/components/RoutePreview.tsx) presentan precio junto a km/min; página /distancia hereda la mejora. Pruebas nuevas **8km→$320 y 43km→$1720**, commit [4273694](https://github.com/marcelofgx-ctrl/traslados-web/commit/4273694f82306a2d0e08c695ef08c812aa56d336). Web CI [38010767896](https://github.com/marcelofgx-ctrl/traslados-web/actions/runs/38010767896) **SUCCESS**.
- Repositorio **traslados-android / web-pasajero**, PWA Pages: tarjeta precio/km/min después de A/B, valor en pantalla de revisión, copia explícita «orientativo, final conductor», estética premium compacta; recursos/cache PWA v4. Recorrido DOM pasa a **A/B primero, fecha/hora después**, commit [4dd2bb6](https://github.com/marcelofgx-ctrl/traslados-android/commit/4dd2bb64f80ccaaca2308f1f566f65255e8a8101). Pages [38010884638](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38010884638) **SUCCESS** sobre commit 1f7ced3, pero queda smoke nuevo de recurso v4 y prueba humana para validar publicación efectiva.
- El smoke público se ajustó a correr después de Pages, evitando falso fallo inicial cuando la versión 4 todavía no había desplegado. Run transitorio [38010809884](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38010809884) rojo por leer el JS anterior **antes del despliegue**; no confundir con error de ruta o backend.
- **Limitación operacional principal:** CI Cloudflare **no despliega automáticamente el Worker**. La nueva propiedad `referenceFareUyu` será visible en las webs solo al publicar el código actualizado en Cloudflare **y** con clave `ORS_API_KEY` correctamente configurada. La PWA no inventa precios si el servidor sigue viejo o sin proveedor; muestra «A confirmar». No se confirmó todavía esa configuración del Worker desde una ejecución HTTP real.
- **GPS comercial:** sigue **PENDIENTE**. Los puntos ya guardados por Mapa en Supabase no se publican a pasajeros sin opt-in expreso, vínculo de identidad móvil/Conductor, estado manual Disponible/Ocupado/Pausado, caducidad y endpoint seguro que solo devuelva km/ETA (no coordenadas). No calcular “estoy a 7 min” hasta que exista telemetría fresca y motor de ruta A conductor→A pasajero. Ver diseño detallado en [Flujo Web, tarifas y presencia](https://github.com/marcelofgx-ctrl/traslados-web/blob/main/docs/traslados-flujo-web-tarifa-presencia-2026-10-09.md).

**HALLAZGO P0 COMPROBADO, no simple hipótesis:** GitHub Pages PWA v4 publicó el código nuevo y el smoke público [38011099526](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38011099526) terminó **SUCCESS**. En sus logs la consulta real a Cloudflare /api/public/route-estimate devolvió **HTTP 503 / reason not_configured**: el **Worker de producción NO tiene ORS_API_KEY disponible**. Por tanto **km/min por ruta y precio UYU no se calculan actualmente** pese al código en GitHub; el frontend deja «A confirmar» y enlace Maps, no inventa resultados. Faltan configurar secret ORS_API_KEY en Worker y publicar su código actualizado. En [traslados-web](https://github.com/marcelofgx-ctrl/traslados-web) se agregó workflow **manual** [publicar-cloudflare-manual.yml](https://github.com/marcelofgx-ctrl/traslados-web/blob/main/.github/workflows/publicar-cloudflare-manual.yml) (commit 4365d412): requiere secretos GitHub CLOUDFLARE_API_TOKEN y CLOUDFLARE_ACCOUNT_ID para despliegue controlado; NO se ejecutó ningún deploy Cloudflare. La tarifa 40 UYU/km es variable opcional FARE_REFERENCE_UYU_PER_KM. Documentación actualizada [flujo web y diagnóstico](https://github.com/marcelofgx-ctrl/traslados-web/blob/main/docs/traslados-flujo-web-tarifa-presencia-2026-10-09.md).

**Continuación verificada (09/10/2026):** Web Premium redujo tarjetas repetidas de reserva en [a876eae](https://github.com/marcelofgx-ctrl/traslados-web/commit/a876eae31f11da95903e81cb0b3e00fd3b8f6222): la ficha A/B, km/min y valor es la principal; el mapa detallado se abre por demanda; el panel externo de Recorrido se compacta al completar puntos; las paradas siguen plegables. CI [38011297730](https://github.com/marcelofgx-ctrl/traslados-web/actions/runs/38011297730) **SUCCESS**. Se incorporó atribución completa ORS / HeiGIT y OSM en web Premium (último commit [739f723](https://github.com/marcelofgx-ctrl/traslados-web/commit/739f723fadf05afa354bf01f426a8d5a7156f041), CI **38011385494 SUCCESS**) y PWA Pages, con actualizaciones de caché JS v5 y smoke de publicación. **Esta mejora de Web Premium todavía no fue desplegada en Cloudflare**. Solo ejecutar workflow `publicar-cloudflare-manual.yml` cuando haya credenciales seguras en Actions y `ORS_API_KEY` cargado en el Worker; no activar ni inventar disponibilidad inmediata. Endpoint HeiGIT `api.heigit.org/openrouteservice/v2/directions/driving-car/geojson` es el correcto para la API migrada, confirmado con fuentes oficiales, por lo que el HTTP 503 `not_configured` se explica por el secret faltante, NO por URL antigua. UX y pruebas documentadas en [Flujo técnico Web](https://github.com/marcelofgx-ctrl/traslados-web/blob/main/docs/traslados-flujo-web-tarifa-presencia-2026-10-09.md).

**Prioridad P0:** probar despliegue Cloudflare/ORS/tarifa real; luego **una reserva consentida de punta a punta** PWA/Cliente → Conductor Mapa → presupuesto final → aceptación → historial. **Prioridad P1:** publicar disponibilidad autorizada desde Mapa y ETA de recogida segura, contemplando viajes Uber/Cabify externos. **P2:** pantalla interna privada para parametrizar tarifa (hoy se controla vía variable del Worker), y homogeneizar UX Premium y Pages. Todas las pruebas y migraciones deben proteger los datos reales.

---

## 0C. PASAJERO PWA — CORRECCIÓN A PARTIR DE CAPTURA REAL (09/10/2026)

**Captura recibida:** Samsung/Chrome en `marcelofgx-ctrl.github.io`, ruta «ROQUE SAENZ PEÑA 1711 → Aeropuerto de Carrasco». Mostraba una tarjeta enorme con **dos cajas «Ver en Maps»**, más «A confirmar», párrafos largos y CTA gigante: la PWA consumía pantalla sin informar km reales. La captura es evidencia UX del despliegue anterior, NO un fallo de cálculo de A/B por error en datos.

**Código implementado y comprobado en GitHub Actions** (sin tocar APK, datos ni reservas): 
- `web-pasajero/app.js`: se reescribió `renderRouteSummary` como resumen móvil **compacto**, con origen/destino legibles en dos líneas cortas, **una sola** franja km/min/precio orientativo, un único enlace a Google Maps y trazado/copyright ORS/OSM dentro de detalles plegables. Si ORS/Worker devuelve error, mostrar **km — / min — / precio «Pendiente»**, sin falsificar distancia ni repetir «Ver en Maps». El backend solo almacena `latestRoad` cuando retorna kilómetros válidos para los puntos actualmente seleccionados. Commit base [fb403c6](https://github.com/marcelofgx-ctrl/traslados-android/commit/fb403c6ea21a009dc34c1c650ed91083b9f0057f).
- `web-pasajero/styles.css`: superficie petróleo/champagne premium, formato compacto mobile-first, contraste ajustado, botones táctiles, sin tarjetas superpuestas, detalles plegables. Commit [f271fc7](https://github.com/marcelofgx-ctrl/traslados-android/commit/f271fc7cf43af5d9aac1f3f7b60e2a0a001a5397).
- **Recogidas «Ahora / En 10 min»**: `setWhen` y `refreshPickupPresence` ahora muestran mensaje **explícito** de distancia conductor→origen desconocida y botón WhatsApp con origen, destino y modalidad precompletados. No se muestra un ETA fingido. Commit [ed450dc](https://github.com/marcelofgx-ctrl/traslados-android/commit/ed450dcb02082f3021728830d90af5fad15dc4d2). Se debe diseñar e implementar después el GPS opt-in para reemplazar este mensaje.
- PWA HTML y service worker se versionaron en **v7** para evitar conservar JS/estilos viejos en Chrome; el sitio debe reabrirse recargando si Android dejó el service worker anterior. El fichero `web-pasajero/tests/booking-summary-smoke.cjs` prueba DOM del resumen sin paquetes externos: ruta no disponible no inventa números, un solo enlace Maps, detalles cerrados y cálculo de muestra de **8 km / 14 min / $320** cuando el servidor entrega datos. El workflow `deploy-web-pasajero-pwa.yml` ejecuta el test. Build [38012982264](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38012982264) **SUCCESS**, incluye pruebas sintácticas y paquete PWA; **la última publicación GitHub Pages debe verificarse por separado**.
- No se editó la lógica de presupuesto final ni se enviaron reservas de prueba a Supabase. El diseño de la web Cloudflare sigue su otro repositorio y continúa con el formulario compacto integrado del commit `a876eae`.

**Bloqueo funcional que persiste:** `/api/public/route-estimate` de Cloudflare respondió **503 `not_configured`** porque falta `ORS_API_KEY` en el Worker. Aunque el código esté listo y la PWA publicada, **hasta configurar una clave válida y desplegar el Worker** no se calculan km/precio reales; no confundir UI corregida y precio calculado. El Worker tiene precio parametrizable `FARE_REFERENCE_UYU_PER_KM=40`; el valor final lo envía Conductor integrado desde Mapa. El usuario no debe compartir secretos en chat.

**Siguientes verificaciones imprescindibles:** 1) confirmar Pages v7 y smoke post-Pages SUCCESS; 2) introducir clave privada ORS en Cloudflare por canal seguro y desplegar `traslados-web`; 3) verificar A/B del ejemplo real con km/min/precio, en Chrome del Samsung; 4) verificar una reserva consentida end-to-end y visualización del presupuesto; 5) proyecto de GPS comercial con consentimiento expreso, PIN, disponibilidad libre/ocupado, privacidad y caducidad (90 s), antes de habilitar un ETA Conductor→A. Los archivos técnicos por aplicación son la referencia; un CI verde **no** certifica pruebas reales del teléfono.

---

## 0D. CORRECCIÓN URGENTES Y PWA V9 — verificación posterior (09/10/2026)

**Hallazgo nuevo en Supabase real:** la función `availability_settings_effective()` devuelve actualmente **`lead_time_min = 30`** (no hay fila de configuración explícita en `driver_availability_settings`). Por tanto **«Ahora» y «En 10 min» NO pueden usar de forma honesta el mismo RPC de reserva programada**: la comprobación de agenda rechazaría la mayoría de solicitudes por `TOO_SOON`.

**Cambios de seguridad/UX realizados para Pasajero PWA** (no se tocó producción Supabase ni el APK instalada):
- Para `programado`: se conserva el formulario completo, chequeo de agenda, revisión, reserva Supabase, presupuesto final de conductor.
- Para `ahora` y `10 min`: **no se intenta crear reserva con horario inválido**; se muestra CTA claro de **consulta WhatsApp**, con modalidad, origen y destino precargados, y aviso de que no es reserva confirmada. Se desactiva el flujo de revisión/envío normal. Para volver a programada se propone fecha/hora a más de 45 min si la anterior expiró. Commit principal [da489b0](https://github.com/marcelofgx-ctrl/traslados-android/commit/da489b02fcac81d8aab246270424ebc27108cfc1). Esto es fallback temporal mientras no exista GPS/estado comercial real; NO prometer llegada o viaje.
- Desde la captura se mejoró también `#login-for-stops`: ya no ocupa un bloque de fondo alto; es una indicación compacta. Se eliminó el aviso repetido de tarifa en «Datos del pasajero» (sigue la información en resumen). La Web PWA usa caché/service worker **v9** con `app.js?v=8` y `styles.css?v=9` para renovar el JS tras actualizaciones. Código final principal [420911d](https://github.com/marcelofgx-ctrl/traslados-android/commit/420911d1ff20bfd63e4c34077959913061f02003).
- Prueba nueva [booking-summary-smoke.cjs](../web-pasajero/tests/booking-summary-smoke.cjs), sin librerías externas, crea nodos DOM falsos y ejecuta la **función real** `renderRouteSummary()`: cuando API de ruta falla exige solo 1 enlace Maps, sin kilómetros/precios ficticios y mapa plegado; cuando la API entrega 8 km/14 min/$320 exige los valores correctos. Prueba que el flujo urgente no use la confirmación RPC convencional. Corren bajo `deploy-web-pasajero-pwa.yml`; run [38013194341](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38013194341) **SUCCESS**, paquete exportado. **El despliegue Pages y prueba física Chrome/Samsung todavía son validaciones separadas**, revisar su run más reciente.
- Runbook externo para activar `ORS_API_KEY` y el deploy Web Premium [ACTIVACION_MOTOR_RUTAS_Y_TARIFAS.md](https://github.com/marcelofgx-ctrl/traslados-web/blob/main/docs/ACTIVACION_MOTOR_RUTAS_Y_TARIFAS.md). Sin secreto válido el Worker responde 503 `not_configured`, el precio en producción sigue pendiente. `FARE_REFERENCE_UYU_PER_KM=40` permite A→B a 40 UYU/km; NUNCA se debe mostrar como presupuesto definitivo ni inventar kilómetros.
- **ETA real Conductor→A sigue sin implementar**: Mapa recoge GPS local, pero su sincronización existente `mapa_trayectos_points` se realiza sobre trayectos finalizados y no implica posición pública autorizada. Falta consentimiento de compartir, vincular conductor autorizado mediante PIN, estado libre/ocupado/pausado (incluido Uber/Cabify), canal seguro con caducidad y servidor que calcule ruta por carretera sin revelar coordenadas. Concluir ese circuito requiere cambios y pruebas de APK y backend específicos; no se entregó una APK nueva en esta sesión.

**No se tocó el backend de reservas ni se insertaron datos ficticios.** Prioridad: Cloudflare clave ORS + deploy, QA visual de PWA v9 en Samsung, prueba programada de punta a punta consentida, después telemetría segura y cálculo ETA.

---

## 1. Mapa de componentes y responsabilidades

| Pieza | Ubicación real | Función | Último estado observado |
| --- | --- | --- | --- |
| **Mapa Trayectos** — APK conductor principal | `traslados-android/mapatrayectos/` | GPS y mapa, jornadas Uber/Cabify/personal, historial, alertas, acciones y **Conductor integrado** | Código `0.1-R24.3`, `versionCode 45`; APK RELEASE firmada verificada en Actions **37924921923**; validación visual integral en Samsung pendiente. |
| **Conductor nativo** integrado | `traslados-android/conductor/` y tarea `prepareEmbeddedConductor` de Mapa | Reservas Supabase, presupuestos, aceptar/rechazar, monitoreo periódico | Forma parte de Mapa R24.x. Conductor **independiente 11.5-R1 RELEASE firmado** pasó Actions **37924922002**; DEBUG de código actual pasó **38009749431**. El job **v9.2 legado fallido** fue retirado, no confundir con las releases exitosas. |
| **Traslados Cliente** — APK nativa | `traslados-android/cliente-pasajero/` y workflows Android | App nativa distinta de la PWA; reservas e historial | RELEASE nativa **11.5-R10** verificada, run **37945735891**; artefacto firmado 11624055221. El viejo job **v9.0 DEBUG** (otro código/paquete embebido) fue retirado; no confundir sus successes con actualizaciones. |
| **Traslados Pasajero GitHub Pages** | `traslados-android/web-pasajero/` | Web/PWA ligera: invitados y clientes con teléfono/PIN, recorrido, reservas, presupuesto | Está **bajo /web-pasajero/**, no en raíz; smoke público **38005505447** y Pages **38005504188**, ambos success al corte. |
| **Índice público del proyecto** | `traslados-android/index.html` | Página de acceso a apps y documentación; mantener como puerta de entrada y continuidad | Raíz GitHub Pages separada del formulario. |
| **Traslados Web Premium Cloudflare** | `traslados-web/` | Otra interfaz web completa: portada premium, historial avanzado, PWA/passkeys en desarrollo, itinerarios, reservas | `main` **152298fcb1300d1fcf68ef3c1bd354bc76de7584**; CI **38008884026** success. Publicación/experiencia real a validar en sitio. |
| **Backend único de reservas** | Supabase **`zetaudvvutlouiqxopvg`** | Clientes, sesiones, reservas, presupuestos, paradas, funciones RPC, estados | Producción real compartida: proteger esquema y registros existentes. |

**Advertencia central:** la PWA GitHub Pages y la web Cloudflare son **dos frontends distintos**, aunque comparten Supabase. Un cambio visual en una NO aparece automáticamente en la otra. La APK Cliente es otro producto; no confundir APK nativa con PWA instalable. **No crear una tercera base ni reservas ficticias para simular integración.**

### Enlaces correctos (comprobados por estructura y automatizaciones)

- **Índice/continuidad:** https://marcelofgx-ctrl.github.io/traslados-android/
- **Formulario real GitHub Pages / PWA Cliente:** https://marcelofgx-ctrl.github.io/traslados-android/web-pasajero/
- **Web premium Cloudflare:** https://traslados-web.marcelof-gx.workers.dev/
- **Calculadora pública Cloudflare:** https://traslados-web.marcelof-gx.workers.dev/distancia
- **Repositorio Android:** https://github.com/marcelofgx-ctrl/traslados-android
- **Repositorio Web Cloudflare:** https://github.com/marcelofgx-ctrl/traslados-web
- **Actions Android:** https://github.com/marcelofgx-ctrl/traslados-android/actions
- **Actions Web:** https://github.com/marcelofgx-ctrl/traslados-web/actions

**Nunca volver a entregar la raíz GitHub Pages como si fuera el formulario.** Se confundieron las URL, el usuario vio el índice del proyecto y no pudo probar la reserva. La estrategia correcta es mantener ambas rutas por separado. El workflow de Pages usa **publicación desde `main`/raíz**, mientras el workflow `deploy-web-pasajero-pwa.yml` verifica/empaca; no instalar dos despliegues de Pages que se sobrescriban.

---

## 2. Experiencia visual y funcional aprobada

- Estética premium petróleo/verde grafito/champagne/dorado **legible** (evitar marrones de bajo contraste), superficies finamente texturadas, bordes suaves, tarjetas compactas, botones accesibles y sin superposiciones.
- **Móvil primero:** el usuario utiliza Samsung Android y comparte capturas; diseñar y probar desplazamiento, teclado, controles flotantes y anchuras ~360–420 px. Los círculos flotantes de Uber y de Mapa pueden ser **overlays de Android**, no HTML; no atribuirlos sin análisis al CSS.
- **Reserva progresiva:** elegir Origen → contraer, elegir Destino → contraer; resumen prioritario Origen/Destino/Km por ruta/Minutos; paradas, datos extra y mapa en desplegables. «Editar», «Invertir», «Ver Google Maps», acceso a itinerario y confirmación sin tarjeta interminable.
- **Búsqueda Uruguay integral:** calles y números (IDE Uruguay) + lugares por nombre (índice OSM de >22.000 POIs) + Geoapify opcional si se configura. Buscar `Punta Carreta Shopping` → `Punta Carretas Shopping`, también Plaza Italia, aeropuertos, hoteles y hospitales sin agregarlos manualmente uno por uno. Departamentos priorizan; **NO filtran** otros destinos uruguayos. Confirmar el acceso físico mediante mapa si importa la puerta exacta.
- **Rutas:** separar línea recta de kilómetros por calles. Cloudflare expone `/api/public/route-estimate`; usa ORS mediante secreto servidor `ORS_API_KEY` **si está configurado**. Sin proveedor, mostrar salida honesta a Google Maps, **no inventar kilómetros ni minutos**. La PWA Pages ya consulta este endpoint Cloudflare. El endpoint fue actualizado para permitir origen CORS específico GitHub Pages.
- **Estados de reserva:** solicitud `PENDIENTE`, presupuesto enviado, aceptada, confirmada, en viaje, finalizada, cancelada/rechazada. Presupuesto enviado por **Conductor**, cliente puede aceptarlo/rechazarlo según estado; no anunciar precio final automático antes de confirmación.
- **Historial:** próximos vs. pasados, búsqueda, filtros por año/mes/estado/periodo, año → mes → semana → reservas plegables, repetir recorrido, exportar cuando esté soportado por frontend.
- **Acceso:** teléfono/PIN en cliente legado y flujos WebAuthn/passkeys preparados en web premium. `PASSKEY_AUTH_ENABLED` solo indica configuración: no equivale a prueba biométrica completa en Android. No registrar credenciales públicas inseguras ni revelar PIN.
- **Audio y hábitos Conductor/Mapa:** conservar MP3 elegidos por usuario, atajos de WhatsApp/tarjeta VCF, menú ✦, flecha flotante, slider de jornada, import/export y apariencia R20–R24. No introducir sonidos genéricos ni tocar automáticamente datos SQLite.

---

## 3. GitHub Pages Pasajero: incidente y últimas correcciones

**Incidente real del 09/10/2026:** al pulsar confirmar, la PWA mostró `function gen_random_bytes(integer) does not exist`. En Supabase el proveedor criptográfico está en el esquema `extensions`; se corrigió `public.generate_reservation_code()` y `public.create_reservation(...)` para usar **`extensions.gen_random_bytes(...)`** y `search_path` explícito mediante migración aplicada `fix_public_reservation_crypto_schema_20261009`. La generación de código se probó bajo rol `anon`. **No afirmar que se creó una reserva completa real hasta ejecutarlo desde el teléfono con autorización.**

**UI de revisión corregida:** panel compacto con Origen/Destino, fecha y hora, pasajeros, estado presupuesto «a confirmar», Maps y datos privados plegados. CTA **«ENVIAR SOLICITUD AL CONDUCTOR»** en vez de sugerir viaje confirmado. PWA con caché red-primero para código CSS/JS, modo offline sin reservas.

**Cambios posteriores presentes en `main`:** `web-pasajero/geo-search.js`, `web-pasajero/data/uy-pois.json`, interfaz de recorrido km/min, enlace Maps, búsqueda nacional por lugares, inicio de selección compacto. `.github/workflows/smoke-public-passenger.yml` comprueba HTTP real, recursos y que el índice tenga los POIs clave.

**Evidencia último corte:**
- GitHub Pages: https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38005504188 — **success**.
- Smoke público: https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38005505447 — **success**.
- Comprobaciones estáticas/paquete: https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38001065374 — **success**.
- Aun con HTTP correcto, queda **prueba humana de envío de reserva, PIN, presupuesto y revisión móvil**. Evitar generar múltiples pedidos reales al probar.

**Regla de publicación:** la raíz del sitio es el índice y `/web-pasajero/` la reserva; no cambiar ese contrato. Un éxito de `build`/smoke no demuestra que todos los estados de negocio funcionen.

---

## 4. Android Conductor / Mapa: referencia y estado

- **Fuente principal de detalle:** [RETOMAR_MAPA_TRAYECTOS.md](RETOMAR_MAPA_TRAYECTOS.md) y [HISTORIAL_VERSIONES_MAPA_TRAYECTOS.md](HISTORIAL_VERSIONES_MAPA_TRAYECTOS.md). El contexto largo histórico [CONTEXTO_MAESTRO_MAPA_TRAYECTOS.md](CONTEXTO_MAESTRO_MAPA_TRAYECTOS.md) **no debe reemplazar las verificaciones en main**.
- Mapa `mapatrayectos/build.gradle`: `versionName '0.1-R24.3'`, `versionCode 45` observado en este corte. Package `uy.com.mapatrayectos`; Android Java/Gradle/SDK35; release firmada.
- Mapa R24.3: corrección del diálogo **Preparar presupuesto** del Conductor integrado ante teclado Samsung; UI scrolleable y acciones visibles. GitHub Actions **37924921923** success; APK respaldada en Drive según el documento RETOMAR. Falta prueba física definitiva.
- Conductor integrado se empaqueta desde `conductor/` mediante `prepareEmbeddedConductor`; dentro de Mapa están reservaciones, presupuesto, estados y monitor. R24.2 introdujo Centro rápido de reservas/agenda/badges y R24.1 menú/burbuja más compactos.
- Conductor independiente sigue como **respaldo**. La RELEASE **11.5-R1** está firmada y CI SUCCESS en **37924922002**; DEBUG 11.5-R1 desde fuente CI SUCCESS en **38009749431**. La falla v9.2 estaba en un workflow histórico empaquetado, retirado de main; ver [diagnóstico y precauciones](CONDUCTOR_INDEPENDIENTE_CI.md).
- Cliente Android nativa 11.5-R10 RELEASE firmada: https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/37945735891 . `cliente-pasajero/` contiene scripts/fuentes extra. Se retiró CI obsoleto v9.0 DEBUG que se confundía con la versión actual; ver [Cliente Android CI](CLIENTE_ANDROID_CI.md).
- Las alertas de Conductor por ahora consultan Supabase periódicamente (~15 s, si Android permite el servicio), **NO son push FCM garantizados**. No prometer recepción con app forzada a detener. FCM y GPS público vivo siguen pendientes de producto.
- **Datos sensibles:** conservar SQLite, historiales, sonidos importados, versiones instaladas, PIN, SharedPreferences y firma APK. No desinstalar ni borrar datos para actualizar sin backup/permiso.

---

## 5. Backend único, seguridad y límites

**Supabase operativo:** `zetaudvvutlouiqxopvg` (NO el antiguo proyecto Lovable `xetklwcxebcpnkrdrmaa`). El secreto de servicio **NUNCA** se sube a GitHub ni se inserta en Android/PWA. Las variables Cloudflare `PASSKEY_SUPABASE_SERVICE_ROLE_KEY`, `ORS_API_KEY`, `GEOAPIFY_API_KEY` se guardan como secretos en servidor (pueden no estar configuradas: verificar existencia, **no pedir sus valores**). `wrangler.jsonc` conserva origen público WebAuthn y feature flag, no claves.

Funciones/Supabase que requieren compatibilidad: `create_reservation`, `get_reservation_by_token`, `customer_create_reservation_v12`, `customer_list_reservations_v12`, `customer_check_availability_v11_4`, `customer_get_available_slots_v11_4`, `customer_quote_decision_v11_4`, `driver_list_reservations_v2`, `driver_set_status_v2`, RPC de presupuestos, sesiones y passkeys. Revisar esquemas/firmas actuales directamente antes de invocarlas; no depender solo de este inventario.

**No borrar registros reales ni duplicar una reserva para pruebas** sin acuerdo expreso. Versionar migraciones aditivas; proteger las políticas RLS y la titularidad de tokens. La PWA no expone tokens en URL pública ni crea reservas offline. Los servicios de rutas sin clave o en modo demo no son fiables para prometer km.

---

## 6. Solicitudes «Ahora», «En 10 min» y presencia del conductor

**Objetivo acordado:** el pasajero puede elegir «Ahora», «En 10 min» o «Programar». Para recogidas próximas se necesita mostrar **distancia y minutos de conductor → origen** y separarlos de **origen → destino** (tiempo/distancia del propio viaje).

**AÚN NO IMPLEMENTADO COMO DISPONIBILIDAD REAL END-TO-END:**
1. GPS de `TrackingService.java` de Mapa con consentimiento explícito de compartir disponibilidad comercial, envío autenticado de posición fresca al backend y estado manual **DISPONIBLE / OCUPADO / PAUSADO / FUERA DE SERVICIO**.
2. Vincular el dispositivo a conductor autorizado; nunca permitir que un ID auto-registrado publique ubicación del conductor.
3. El conductor puede estar ocupado por Uber/Cabify aunque Supabase no tenga reserva: **no deducir libre por agenda vacía**.
4. Lat/lng exactos deben permanecer en servidor privado. Entregar al pasajero **solo km/ETA y edad de dato**; invalidar GPS caducado (referencia de diseño 90 s), sin rastreo público.
5. Calcular la ruta por carretera conductor→origen en servidor y verificar que ETA + margen permita llegada «En 10 min». Antes de aceptar, chequeo transaccional de conflictos y confirmación del conductor.
6. Sin datos frescos o proveedor de rutas, mostrar `Disponibilidad no confirmada` y ofrecer WhatsApp/programación. No fabricar «Conductor libre» ni «7 minutos».

**Estado web Premium v16.4 en código:** selector visual «Ahora / En 10 min / Programar» y consulta WhatsApp incluyendo itinerario; km/min de trayecto mediante motor compartido si ORS responde. **La opción urgente no constituye reserva automática confirmada.**
**Documento técnico completo:** en `traslados-web/docs/traslados-premium-booking-pickup-2026-10-09.md`.

---

## 7. Diseño de próximos trabajos (priorización)

**P0 — operacional y comercial**
- Probar **una** reserva real controlada desde `/web-pasajero/` → verla en Mapa/Conductor → enviar presupuesto → cliente acepta/rechaza → comprobar estados e historial; confirmar que desapareció `gen_random_bytes` y no se duplican reservas.
- Probar UX Android en Samsung con capturas: búsqueda «Punta Carretas Shopping», «Plaza Italia Shopping», Aeropuerto Carrasco, origen/destino compacto, km/min o Maps, teclado sin tapar CTA, contraste.
- Si falla la distancia en la PWA, verificar Cloudflare ruta `/api/public/route-estimate`, proveedor ORS y CORS autorizado; no crear falsos km.
- Continuar pruebas físicas de Conductor RELEASE 11.5-R1 y DEBUG desde fuente ya compilados; el job legado v9.2 se retiró. No romper la APK Mapa principal.

**P1 — integración Mapa ⇄ Web**
- Implementar GPS/estado del conductor con consentimiento, seguridad, frecuencia, caducidad y privacidad.
- Crear endpoint para km/ETA de recogida que **no** exponga GPS exacto.
- Conectar el modo inmediato con agenda/conflictos y aceptación real.
- Probar Android en segundo plano, energía y pérdida de conexión; no asegurar FCM sin Firebase/backend configurados.

**P2 — mantenimiento y calidad**
- Reducir duplicación de lógica UI entre PWA GitHub Pages y web Premium Cloudflare sin romper acceso legado a clientes.
- Unificar criterios de diseño premium, compactación progresiva, filtros y presupuesto en las tres superficies.
- Revisar métricas de uso y cuotas de proveedores gratuitos antes de abrir al público.
- Mantener trazabilidad de versiones, SHA-256 y archivos Drive/Actions.

---

## 8. Convención de trabajo entre sesiones (OBLIGATORIA)

1. **Leer este documento primero** y seguir enlaces técnicos especializados según el componente solicitado. No pedir al usuario que repita contexto ya guardado.
2. **Verificar `main` y Actions** de ambos repos antes de editar: buscar fallos actuales, tests verdes y si GitHub Pages/Cloudflare entregan el commit esperado. Comprobar la web **correcta**: raíz índice ≠ `/web-pasajero/` ≠ Cloudflare.
3. En cambios multi-repo, mantener **un contrato común** de esquemas/RPC y registrar ambos commits; no fingir que dos frontends se actualizan solos.
4. Nunca dar como terminado algo sin clasificarlo: **PROYECTADO**, **IMPLEMENTADO EN CÓDIGO**, **COMPILADO**, **PUBLICADO**, **PROBADO EN SAMSUNG**, **PROBADO END-TO-END EN SUPABASE**.
5. No usar números ni ETA ficticios como datos reales, no crear reservas de prueba sin consentimiento, no meter secretos en repo público. Nunca borrar SQLite/MP3/reservas ni romper firma de APK.
6. **Al terminar una tanda de cambios**, actualizar **este archivo** (fecha del corte, estado, link a commits/CI y pendientes), más el histórico especializado si procede. No crear infinitos «contexto-v2-v3-v4» ni depender de sesiones previas.
7. Si el archivo se vuelve largo, conservar aquí lo **vigente y navegable**; enviar detalles cerrados a documentos especializados. El historial no se pierde porque está versionado en Git.
8. Para reanudar, basta enlazar este documento en una conversación nueva. La IA podrá consultar GitHub si tiene el conector disponible; si no, abrir el enlace o pegar su contenido.
9. Prefiera un PR pequeño con CI y revisión antes de fusionar si toca producción; preservar comportamiento visual aprobado y tests.
10. Al entregar trabajo, informar **qué repos, archivos, commits y URLs** cambiaron, qué comprobó GitHub Actions, qué se publicó y qué requiere prueba del usuario.

---

## 9. Fuentes técnicas y evidencias

**Android / Mapa**
- [Retomar Mapa Trayectos y entregar APK](RETOMAR_MAPA_TRAYECTOS.md) — versiones firmadas, fixes IME, QA.
- [Historial Android](HISTORIAL_VERSIONES_MAPA_TRAYECTOS.md) — histórico de releases, SHA y pruebas.
- [Contexto histórico Mapa](CONTEXTO_MAESTRO_MAPA_TRAYECTOS.md) — decisiones anteriores.
- [Estado integrado de apps](ESTADO_INTEGRADO_CLIENTE_WEB_PWA_CONDUCTOR.md) — compatibilidad y Supabase.
- [Conductor independiente — CI y beta](CONDUCTOR_INDEPENDIENTE_CI.md) — release firmada real, error histórico v9.2, DEBUG desde fuente.
- [Cliente Android — CI](CLIENTE_ANDROID_CI.md) — release nativa 11.5-R10 y depuración de jobs v9.0.
- [PWA Cliente — instrucciones](../web-pasajero/README.md) — publicador, scripts, claves públicas y UX.
- [Mapa RELEASE CI](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/37924921923) — última RELEASE principal conocida.
- [Smoke GitHub Pages](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38005505447) — rutas públicas verificadas.

**Web Cloudflare**
- [Repositorio y readme](https://github.com/marcelofgx-ctrl/traslados-web).
- [Guía de rediseño y recogidas](https://github.com/marcelofgx-ctrl/traslados-web/blob/main/docs/traslados-premium-booking-pickup-2026-10-09.md).
- [CI última web](https://github.com/marcelofgx-ctrl/traslados-web/actions/runs/38000741722).
- Migraciones SQL de web en `traslados-web/supabase/migrations/`; contrastar su estado con **Supabase producción**, no deducir que están aplicadas solo por estar versionadas.

**Regla final:** Esta es la fuente **maestra de coordinación**, pero **no sustituye verificar el presente**. Si alguien cambia un repositorio después del corte, registrar ese cambio aquí antes de dar por terminado el trabajo.
