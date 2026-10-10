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

## 0.0.d INSTALACIÓN WEB PASAJERO — CORRECCIÓN BOTÓN (10/10/2026; PWA v17)

**Evidencia:** fotografía del Samsung en sección «Instalar», con botón «INSTALAR TRASLADOS» y reporte «No, solo mensaje error» tras pulsarlo. Se examinó `web-pasajero/app.js`: función `installUI()` solo ofrecía diálogo de instalación si Chrome emitía antes `beforeinstallprompt`; si no, mostraba un `toast` temporal que el usuario interpretó como fallo. En modo `standalone` respondía con otro `toast` aunque ya estuviera instalada. No se recibió aún texto literal del mensaje de error de instalación de Android/Chrome, por lo que no atribuirlo a un fallo específico del navegador sin evidencia.

**IMPLEMENTADO en `traslados-android/main`:**
- `web-pasajero/app.js`: `installUI()` nuevo; detecta aplicación abierta en modo standalone y ofrece **ABRIR MIS TRASLADOS** (lleva a «Mis traslados»); si hay `beforeinstallprompt`, presenta **INSTALAR TRASLADOS** y usa `prompt()` tras toque real, captura cancelación/error; si Chrome no envía el evento, presenta **VER CÓMO INSTALAR** con guía persistente en página, instrucciones específicas para Chrome Android, Samsung Internet, Safari iPhone y otros, además de **COPIAR ENLACE DE TRASLADOS**. No intenta inventar APIs para abrir el menú nativo. Evita reutilizar un evento `beforeinstallprompt` ya consumido. Registro del service worker preparado tanto si `load` ocurrió como si todavía está pendiente.
- `web-pasajero/index.html`: zona guía accesible `install-guide`, textos, lista de pasos y botón copiar enlace. `styles.css`: tarjeta con estética petróleo/champagne; recursos `app.js?v=17` y `styles.css?v=17`. `sw.js`: `traslados-cliente-pwa-v17`.
- `web-pasajero/tests/install-flow-smoke.cjs`: test funcional en Node VM de ausencia/presencia de diálogo nativo, modo instalado, error/cancelación, iOS y copiado de URL, además de estructura HTML/estilos/cache. Integrado en `deploy-web-pasajero-pwa.yml` y `check-web-pasajero-review.yml`; actualizado `public-branding-smoke.cjs` y `location-permission-smoke.cjs`. [CI PWA #38068632523](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38068632523) **SUCCESS**. [Pages #38068634728](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38068634728) en progreso al primer chequeo; actualizar estado según resultado.
- **LÍMITES:** una PWA no puede forzar el cuadro nativo de instalación si el navegador no emite `beforeinstallprompt`, ni saber siempre si hay otra instalación cuando está abierta en pestaña normal. Ofrecer guía y enlace es solución compatible, no APK. Antes de declarar solucionado en el teléfono, comprobar publicación Pages y probar en Samsung al estacionar. **No afecta** Workers principal, Supabase, reservas, GPS, Mapa Trayectos ni APK. Pages sigue auxiliar: para clientes compartir solo Workers una vez publicada R4; su URL GitHub visible no cumple la decisión de marca pública.

---

## 0.0.c GPS BLOQUEADO POR BURBUJAS EN SAMSUNG — 10/10/2026 (PWA V16 / WORKERS)

**Evidencia del usuario, a las 11:59 Uruguay:** pantalla de reserva de **PWA auxiliar GitHub Pages** con alerta nativa Android «Este sitio no puede solicitarte permiso. Cierra las burbujas o superposiciones de otras apps». En la captura se ven arriba a la derecha burbujas flotantes de **Uber y Cabify**, no solo Mapa Trayectos. Ocultar únicamente la de Mapa no resuelve esa pantalla. El permiso lo protege Android a nivel sistema, no lo puede conceder el sitio ni Supabase. El usuario estaba conduciendo: insistir en NO pedir manipular ajustes mientras conduce.

**IMPLEMENTADO en `traslados-android/main`:** `web-pasajero/app.js` ahora consulta `navigator.permissions.query({name:"geolocation"})` y, si un Android tiene permiso en `prompt` (pendiente), muestra **una tarjeta de preparación antes de abrir la ventana Android**. Explica específicamente Uber/Cabify/Mapa, requiere que el conductor esté estacionado y ofrece `⌖ Solicitar permiso` (un toque explícito) y `Elegir origen sin GPS` (buscador manual). Si ya está concedido se pide directamente la ubicación; si está denegado se presenta la guía de Chrome/Android sin bucle; se evitan varias ventanas de permisos simultáneas. Código `7a4c6ca6`, recursos PWA `app.js?v=16`, service worker cache `traslados-cliente-pwa-v16`. Prueba dinámica `web-pasajero/tests/location-permission-smoke.cjs`: simuló permisos pending/denied/granted y verificó **0 aperturas nativas en pending**, **1 apertura tras toque explícito**, guía tras denegación. [CI PWA #38062153790](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38062153790) **SUCCESS** y [Pages despliegue #38062152377](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38062152377) **SUCCESS**.

**IMPLEMENTADO en `traslados-web/main`:** `src/components/UyLocationPicker.tsx` recibió preparación equivalente y mantuvo botón «Elegir en mapa» además de «Escribir origen». `tests/location-permission.test.ts` ampliado; [CI Workers #38062167575](https://github.com/marcelofgx-ctrl/traslados-web/actions/runs/38062167575) **SUCCESS** (dos jobs). **NO PUBLICADO en Workers:** consulta de runs `event=workflow_dispatch` permanece en 0, se mantiene despliegue **manual** con URL canónica y sin alterar secretos. No redireccionar a Pages, ni cambiar identidad, ni pretender que CI=producción.

**PENDIENTE de verificación real y única acción del usuario AL ESTACIONAR:** desactivar/ocultar todas las burbujas Uber/Cabify/Mapa y, si no basta, **Ajustes → Aplicaciones → Acceso especial → Aparecer encima**, desactivar temporalmente la app que genera la superposición. Entrar de nuevo al sitio y tocar «Solicitar permiso» en nuestra tarjeta. La PWA no puede cerrar ni detectar las burbujas de apps ajenas y no hay técnica web legítima que eluda el bloqueo Android. Se puede continuar con origen manual sin GPS. No se tocó backend, reservas, APK, PIN, GPS de Mapa ni funcionamiento de Uber/Cabify.

---

## 0.0.b IDENTIDAD PÚBLICA SOLO TRASLADOS — 10/10/2026

**Decisión del usuario:** ningún pasajero debe ver referencias a GitHub, Cloudflare, Supabase, desarrolladores, workflows, compilaciones o proveedores técnicos en la interfaz de uso normal. La web principal permanece en la URL aprobada **https://traslados-web.marcelof-gx.workers.dev/**. **No** redirigir a GitHub Pages (revelaría `github.io`), ni cambiar la estética premium petróleo/champagne/dorado.

- **Workers / web principal:** implementada revisión interna `data-app-release="traslados-2026-10-10-r4"` (identificador solo diagnóstico). Footer ahora dice «Atención personal · Uruguay», QR muestra «sitio oficial de Traslados», explicaciones de ruta omiten motores y proveedores; se quitó enlace visible «Conductor (sistema anterior)» del pie de cliente. Se conservan atribuciones cartográficas legalmente necesarias. **CI [38060418736](https://github.com/marcelofgx-ctrl/traslados-web/actions/runs/38060418736) SUCCESS (dos jobs)** con prueba `tests/public-branding.test.ts` y prueba canónica actualizadas. Workflow manual de Cloudflare exige R4; **AÚN NO se confirmó ejecución ni publicación**. No alterar el Worker/código de reservas para sustituirlo por Pages.
- **PWA secundaria GitHub Pages:** se quitó el enlace visible a GitHub Actions/APK obsoleta y descripciones técnicas de instalación; se reemplazaron «Consultando Supabase» y nombres de motores por mensajes de usuario. Recursos del navegador versión 15 (`app.js?v=15`, `sw.js` cache v15), test `web-pasajero/tests/public-branding-smoke.cjs` y verificación en workflows. **Empaquetado [38060462002](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38060462002) SUCCESS y Pages [38060461006](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38060461006) SUCCESS**. **NO** se ha hecho prueba visual Samsung del nuevo cambio.
- **Política de enlace para pasajeros:** mostrar **solo** la URL principal Workers una vez su versión final esté publicada. Pages queda auxiliar/técnico y no se reparte por QR, tarjetas o WhatsApp.
- **Limitación de marca blanca:** el sufijo `.workers.dev` indica infraestructura a quien mire la URL. Para ocultar incluso el proveedor de hosting se necesita **dominio propio** que apunte al Worker, a definir/aprobar por el usuario, sin apagar la URL anterior. Una web siempre puede revelar integraciones en solicitudes de red y debe conservar créditos/cartografía según licencias: no prometer ocultamiento técnico absoluto. La limpieza actual es de elementos **visibles al pasajero**, no una red de proxy ni un nuevo dominio.

---

## 0.0.a PERMISOS GPS GUIADOS — 10/10/2026 (corrección UX cross-web)

**Incidente Samsung real:** captura de la PWA `/web-pasajero/` con bloqueo Android/Chrome «Este sitio no puede solicitarte permiso. Cierra las burbujas o superposiciones». Ocultar una burbuja y volver a intentar no siempre resuelve el permiso, que es controlado por Android.

- **GitHub Pages / PWA auxiliar:** `web-pasajero/app.js` reemplaza el `toast` genérico por tarjeta premium de ayuda persistente cuando falla `getCurrentPosition`, con causas diferenciadas (denegado/no disponible/timeout), ruta Android/Chrome para conceder ubicación, **Volver a intentar** y **Escribir dirección**. `styles.css`, `index.html` versión de recursos `v14`, `sw.js` caché `traslados-cliente-pwa-v14`; test `web-pasajero/tests/location-permission-smoke.cjs` incluido en CI. [Empaquetado 38026118498](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38026118498) SUCCESS y [GitHub Pages 38026121606](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38026121606) SUCCESS. **Publicado según Actions**, **no validado aún en Samsung**.
- **Cloudflare Workers principal (URL inmutable):** `src/components/UyLocationPicker.tsx` también muestra instrucciones GPS y permite reintentar/introducir dirección. `tests/location-permission.test.ts` añadido al CI y al workflow manual de deploy. [CI 38026179460](https://github.com/marcelofgx-ctrl/traslados-web/actions/runs/38026179460) SUCCESS doble para la primera revisión; commit posterior corrige indicador GPS fuera de Uruguay. **IMPLEMENTADO/COMPILADO, pero NO PUBLICADO aún en Workers** hasta `workflow_dispatch` manual y verificación HTTP.
- **Regla técnica:** ninguna PWA puede conceder automáticamente permisos Android, abrir de forma universal y fiable la pantalla de ajustes nativos ni eludir protección por overlays. La web solicita acceso solo al tocar GPS, muestra guía para Chrome y ofrece entrada manual; sin quitar permisos de apps ajenas ni alterar el backend, las reservas, Mapa/Conductor o las APK.
- **Comprobación física pendiente:** reabrir/actualizar la PWA Pages en Chrome y probar que aparece la guía y el reintento; después de desplegar Workers, repetir con esa web principal. No llamar probada a una autorización solo por CI verde.

---

## 0.0 REANUDACIÓN COMPROBADA — 10/10/2026 — WORKERS R3 (estado real)

**Prioridad y URL inmutables:** la web definitiva del pasajero es **https://traslados-web.marcelof-gx.workers.dev/**, código en `marcelofgx-ctrl/traslados-web`. GitHub Pages es auxiliar; **NO** sustituir ni redirigir la web principal. Mantener estética petróleo, champagne, dorado y texturas aprobadas. No alterar secretos ni `keep_vars:true`. Para detalle, consultar [WEB_PRINCIPAL_WORKERS.md](https://github.com/marcelofgx-ctrl/traslados-web/blob/main/docs/WEB_PRINCIPAL_WORKERS.md).

**Verificación GitHub realizada esta sesión antes de cualquier modificación funcional:**
- Código de R3: commit [`4d4152e1`](https://github.com/marcelofgx-ctrl/traslados-web/commit/4d4152e1ff0b6d10a57169cf3c6ea06eda29cd6a) con [CI 38024838373](https://github.com/marcelofgx-ctrl/traslados-web/actions/runs/38024838373) **SUCCESS en ambos jobs**. Último `main` leído en web: [`26dea1da`](https://github.com/marcelofgx-ctrl/traslados-web/commit/26dea1da8b5550a4d5c6f431d984b224c9981e84), actualización documental, [CI 38024918603](https://github.com/marcelofgx-ctrl/traslados-web/actions/runs/38024918603) **SUCCESS en ambos jobs**.
- **Despliegue manual:** GitHub Actions `actions/runs?event=workflow_dispatch` devolvió **0 ejecuciones** para `traslados-web` al corte. No se comprobó publicación de R3. La lectura HTTP externa de la web canónica no pudo completarse en esta sesión, así que **estado en producción DESCONOCIDO**, no asumir R3 publicado ni afirmar QA visual.
- Existe [`.github/workflows/publicar-cloudflare-manual.yml`](https://github.com/marcelofgx-ctrl/traslados-web/blob/main/.github/workflows/publicar-cloudflare-manual.yml): solo `workflow_dispatch`, verifica `name=traslados-web`, URL de `PASSKEY_PUBLIC_ORIGIN`, `keep_vars:true`, pruebas + TypeScript + build, credenciales vía secretos existentes, `wrangler deploy` y comprobación HTTP R3 + ORS/tarifa. El conector GitHub actual **NO tiene la acción para iniciar `workflow_dispatch`** ni conexión Cloudflare. **Paso externo pendiente del titular:** abrir [ejecución manual](https://github.com/marcelofgx-ctrl/traslados-web/actions/workflows/publicar-cloudflare-manual.yml) y pulsar `Run workflow → main → Run workflow`. No reemplazarlo por un trigger automático o nuevo nombre de Worker.
- **Código R3 inspeccionado, no QA móvil:** `PremiumHome` monta `DriverLiveStatus` (consulta de disponibilidad pública Supabase cada 40 s, sin GPS); `GuestRoutePlanner` obtiene A/B/paradas y `BookingQuickSummary` km/min/tarifa **cuando** hay ruta verificada; `PickupModePicker` incluye `DriverPickupEta compact` al elegir `Ahora / En 10 min`, pero esas dos modalidades **consultan por WhatsApp y no crean reservas inmediatas automáticas**. ETA de recogida conductor→origen está protegida con sesión; no confundir con km/min de A→B.
- **Backend leído sin escrituras:** Supabase operativo `zetaudvvutlouiqxopvg` = `ACTIVE_HEALTHY`; existen tablas con RLS para reservas, dispositivos/trayectos Mapa y presencia del conductor; Edge Function `pickup-eta` **ACTIVE**. Esto **NO** comprueba edad del último heartbeat, GPS fresco, reserva exitosa, presupuesto o flujo end-to-end.
- **Cliente Android:** la RELEASE 11.5-R12 [Actions 38021100002](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38021100002) terminó **SUCCESS** con artefacto `Traslados-Cliente-v11.5-R12-RELEASE` ID `11658906107`, SHA-256 `927b035c8223d51ac26b7a5615fdfb9121deff2a9c8139959edfa701f606b228`; **no equivale a APK instalada o probada**. Último `main` de Android antes de este asiento: [`4adf534e`](https://github.com/marcelofgx-ctrl/traslados-android/commit/4adf534ec833d73b01c0de01893fbe5172e79ed0), documental.
- **Protección operativa:** no se cambió el código visual R3, esquema, secretos, reservas ni build APK durante esta revisión. No crear reservas de prueba ni habilitar GPS público para eludir seguridad.

**Siguiente orden estricto:** (1) disparar workflow manual sin modificar URL/secretos; (2) revisar que el job terminó SUCCESS y corroborar HTML `data-web-release="workers-2026-10-10-r3"`, motor de rutas ORS y tarifa; (3) QA visual Samsung invitado→A/B→paradas→precio→autenticación→agenda/historial; (4) **una sola reserva real consentida** y seguimiento Conductor/Cliente/presupuesto; (5) corregir defectos observados preservando estética, con CI y nuevo corte. Una compilación verde no constituye publicación ni prueba real.

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

## 0E. RUTAS REALES REUTILIZADAS + PRECIO WEB DESDE CONDUCTOR (09/10/2026)

**Pedido atendido:** usar el motor de rutas YA existente, mostrar km/min/precio orientativo con estética premium en web, y editar tarifa interna desde Conductor, manteniendo presupuesto final independiente. **No se ha habilitado todavía el GPS en vivo ni una clave ORS.**

**Descubrimiento de código/producción:** la función privada `availability_reposition_v11_4` de Supabase `zetaudvvutlouiqxopvg` calcula mediante OSRM y escribe `route_reposition_cache` (16 registros ROAD verificados al inicio de la auditoría; aún pocos para cubrir rutas nuevas). Puede producir `method='ESTIMATED'` en ausencia de OSRM; **NUNCA** cotizar basándose en ese cálculo, y no exponer esa función privada ni reutilizar el servidor demo OSRM para tráfico público comercial.

**Implementación Supabase aplicada y comprobada REALMENTE:**
- `public_cached_route_preview_v1(p_points jsonb)`: RPC pública SECURITY DEFINER, **solo lectura** de tramos existentes `ROAD`, vigencia 7 días, 2–10 coordenadas verificadas en Uruguay; devuelve `distanceKm`, `durationMin`, `source:supabase_route_cache`, `geometry:[]`, fecha y tarifa cuando hay cache. No solicita rutas nuevas al demo OSRM ni expone datos de clientes/GPS. Pruebas SQL: ruta cacheada **18,1 km / 25 min** y rechazo explícito de puntos inválidos/cache miss.
- `driver_settings.reference_rate_uyu_per_km` parametrizable, valor inicial **40 UYU/km**. RPC `public_reference_quote_v1(km)` calcula únicamente valor orientativo, por ejemplo **8 km→$320** y **43 km→$1.720**; rango y redondeo validados. `driver_set_reference_rate_v1(p_pin,p_rate)` y `driver_get_reference_rate_v1(p_pin)` protegidas mediante `driver_pin_valid`. Rutas cacheadas 18,1 km cotizan $720. **No se modificó ninguna reserva ni precio final existente**.
- Archivos de migración archivados en **`traslados-web/operativa/migrations/`**. **MUY IMPORTANTE:** `traslados-web/supabase/config.toml` apunta al legado `xetklwcxebcpnkrdrmaa`, NO a la base operativa. Se retiraron estas cuatro migraciones de `supabase/migrations/` en [9043eb7](https://github.com/marcelofgx-ctrl/traslados-web/commit/9043eb76a977f4f639e64bdb667183c601f78818) para que una sincronización futura no las aplique al proyecto Lovable equivocado. Ya fueron aplicadas manualmente al proyecto correcto.

**Web y PWA (diferenciar código/producción):**
- **Web Premium** `traslados-web`: ruta principal ORS (si existe `ORS_API_KEY` privada); fallback al RPC de rutas ROAD reales cacheadas; un único backend de precio referencial Supabase. `BookingQuickSummary.tsx` rediseñada con dirección A/B protagonistas, métricas compactas, precio en champagne y sello de procedencia; `RoutePreview` indica si proviene de ORS o de cache y deja claro «precio orientativo ≠ presupuesto definitivo». No mostrar kilómetros en línea recta como presupuesto. CI estricto [38014190452](https://github.com/marcelofgx-ctrl/traslados-web/actions/runs/38014190452) **SUCCESS**, nuevos tests `verified-road-cache.test.ts`. **Despliegue Web Cloudflare de estos cambios NO verificado / no se ejecutó desde esta sesión**.
- **PWA GitHub Pages**, `web-pasajero/app.js`: intenta primero el endpoint Cloudflare, y si falla usa `public_cached_route_preview_v1` por API anon segura; si no está cacheado marca km/min **—**, precio «Pendiente», sin valores inventados. Diseño compacto v9 previo se mantiene. `app.js?v=10`, service worker `traslados-cliente-pwa-v10` (nuevo cache para Chrome). Test DOM `booking-summary-smoke.cjs` incluye fallback simulado 18,1 km/25 min/$720 y ORS 8 km/14 min/$320; [CI PWA 38013940646](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38013940646) **SUCCESS**, Pages [38014237800](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38014237800) **SUCCESS**; falta prueba visual final en Samsung y verificación anon via smoke nuevo.
- El código de **Conductor `conductor/`** agregó acceso a tarifa web en el menú **TARIFAS** (antes PRESETS): caja premium, valor por km del backend, ejemplo de 8 km, botón ACTUALIZAR TARIFA DE LA WEB, RPC PIN en segundo plano. Presets del presupuesto individual siguen guardados por separado. **Conductor independiente 11.5-R2/versionCode 124 RELEASE firmada** [Actions 38014134233](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38014134233) **SUCCESS**, artefacto [11655728252](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38014134233). DEBUG [38014134210](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38014134210) SUCCESS, ID 11655588253. Mantener APK RELEASE firmada; no instalar DEBUG encima sin controlar firma/datos.
- **Mapa Trayectos + Conductor integrado `0.1-R24.4` / versionCode 46** compila las mismas pantallas de Conductor y conserva todos los MP3, SQLite, GPS y PIN locales; [Actions 38014238559](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38014238559) **SUCCESS**, artefacto RELEASE [11655188613](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38014238559), firmado con la huella certificada esperada. **Prueba instalación/función Samsung pendiente**; no desinstalar ni borrar datos al actualizar.

**Bloqueos claros antes de prometer cálculo universal:**
1. El Worker de producción respondió anteriormente **503 `not_configured`**: falta configurar secret **`ORS_API_KEY`** y desplegar `traslados-web`. Sin eso se muestran **solo** las rutas previamente guardadas y verificadas que estén disponibles en caché; la gran mayoría de combinaciones nuevas A/B no tendrán km/precio automático. Nunca afirmar que la caché ya cubre todos los viajes.
2. El cálculo «Conductor → origen A» y ETA «Ahora / En 10 min» sigue PENDIENTE: requiere GPS publicado con consentimiento, autenticación del dispositivo y PIN, estado disponible/ocupado que incluya Uber/Cabify, RPC privada y caducidad. Hoy la PWA propone WhatsApp para urgentes. La tarifa web **no** habilita disponibilidad del conductor.
3. QA real de reserva consentida → Conductor integrado → presupuesto final → aceptación/rechazo → historial pendiente; nunca crear pedidos o modificar datos reales sin autorización específica.

**Fuente de detalle para próximas sesiones:** [MOTOR_RUTAS_REAL_Y_TARIFAS_COMPARTIDAS.md](https://github.com/marcelofgx-ctrl/traslados-web/blob/main/docs/MOTOR_RUTAS_REAL_Y_TARIFAS_COMPARTIDAS.md), y [activación ORS](https://github.com/marcelofgx-ctrl/traslados-web/blob/main/docs/ACTIVACION_MOTOR_RUTAS_Y_TARIFAS.md). Mantener contexto maestro ÚNICO y documentación por aplicación.

---

## 0F. CAPTURA PASAJERO 22:47 — RESUMEN V11 SIN DIRECCIONES DUPLICADAS (09/10/2026)

**Entrada del usuario:** captura Samsung de PWA pública v10 para «Roque Sáenz Peña 1711, Canelones → Aeropuerto Internacional de Carrasco». Ambos puntos están seleccionados, pero en el resumen aparecían **distancia —, duración —, precio Pendiente**. Además el resumen repetía las direcciones que ya se veían en las tarjetas superiores y ocupaba mucho espacio. **No atribuir el fallo al usuario ni afirmar que la ruta se calculó.** El Worker público carece de `ORS_API_KEY` configurada y la caché `route_reposition_cache` contiene pocas rutas `ROAD`; el trayecto de la captura no produjo un valor válido. Sin un proveedor seguro de rutas nuevas, no hay cálculo universal. La RPC de caché existente NO llama al servidor OSRM demo para rutas nuevas.

**Corrección real efectuada en `web-pasajero/`:**
- `app.js`: `renderRouteSummary` muestra solo un encabezado de **Estimación del viaje**, dos métricas compactas km/min y una franja champagne **Valor de referencia**. Ya NO vuelve a mostrar `DESDE/HASTA` con direcciones repetidas: esas direcciones permanecen editables en sus campos superiores. Se mantiene «Abrir en Maps» y un acordeón «Detalles de la estimación». Distancia/tiempo y tarifa se muestran únicamente con `available:true`, magnitudes finitas/positivas y fuente verificada `openrouteservice` o `supabase_route_cache`. Sin ruta real: km/min **—**, precio **A confirmar**, con aviso comprensible. Diagnóstico del backend principal y la caché limitada figura solo en detalles; no vender rutas aéreas como kilómetros por calles.
- **Seguridad de precios:** caché en el navegador ahora expira en 5 minutos, de modo que la tarifa modificada mediante PIN en Mapa Conductor no quede visible indefinidamente como precio antiguo. Se invalida al cambiar A/B/paradas. Si `data.geometry` no existe (cache Supabase), el mapa detallado no dibuja una línea falsa.
- `styles.css`: tarjetas A/B seleccionadas menos altas y panel premium de estimación compacto, contraste y control táctil móvil; estilo petróleo/champagne intacto. Se simplificó subtítulo de puntos: «Ubicación seleccionada».
- `index.html` usa **`app.js?v=11` y `styles.css?v=11`**; `sw.js` usa `traslados-cliente-pwa-v11` para vaciar cachés antiguos; `smoke-public-passenger.yml` exige v11 y nuevo componente. Commit de publicación [53a2184](https://github.com/marcelofgx-ctrl/traslados-android/commit/53a2184154c170cd0b8d338a43c748766958aeb2).
- `booking-summary-smoke.cjs` se actualizó para exigir cero direcciones duplicadas, confirmar datos del motor de caché previo y ORS preferente, y expiración positiva de la tarifa. El primer test v11 falló únicamente porque el nodo DOM simulado no implementaba `classList` (la API nativa sí la tiene). Corregido el simulador en [bcb3d30](https://github.com/marcelofgx-ctrl/traslados-android/commit/bcb3d30723b3f3533cf3270cdb056418e099a38b). **CI PWA [38014754040](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38014754040) SUCCESS** con JS parseado, tests y paquete exportado. **Verificar el deploy Pages v11 y una captura real Samsung por separado** antes de marcar UI publicada/probada.

**Limitaciones explícitas:** ORS necesita una credencial real de HeiGIT como secreto `ORS_API_KEY` en Cloudflare y desplegar la versión actual del Worker. Si no está, la mayoría de recorridos nuevos seguirán sin cotización. Tampoco hay GPS comercial conductor→A ni ETA hasta recogida; «Ahora/En 10 min» usa consulta WhatsApp por diseño, sin crear reserva automática inválida. No modificar funciones de backend de reservas, datos, firma APK o sonidos para solucionar un problema exclusivamente de Web.

---

## 0G. MOTOR DE RUTAS ACTIVADO (09/10/2026, 23:11 Uruguay)

El usuario configuró el proveedor de rutas desde Cloudflare. GitHub Actions [38016029489](https://github.com/marcelofgx-ctrl/traslados-web/actions/runs/38016029489) comprobó la respuesta real del Worker público: **HTTP 200, source openrouteservice, distancia por carretera, duración estimada y tarifa de referencia válidas**. Ya no es correcto afirmar que el proveedor principal sigue sin configurar: se activó para la ruta de control. Esto no demuestra que el itinerario del pasajero mostrado en su captura se haya probado en Samsung.

La configuración aparece como variable de texto visible en la captura. Recomendación de seguridad: utilizar un Secret en Cloudflare y renovar la credencial en el proveedor, sin introducir sus valores en documentos, chats o repositorios.

Segundo control CI [38016164113](https://github.com/marcelofgx-ctrl/traslados-web/actions/runs/38016164113) **SUCCESS**: prueba HTTP 200 con encabezado Origin de GitHub Pages, **CORS permitido**, fuente **openrouteservice**, 19,5 km, 28 minutos, valor referencial $780 en una ruta técnica de prueba. No son los valores del viaje Roque Sáenz Peña → Aeropuerto: siguen pendientes de comprobación con los puntos seleccionados en el Samsung. Se consultó una ruta pública, sin leer, guardar ni imprimir secretos.

PWA Pasajero v11: resumen premium sin A/B repetidos, km/min/precio calculados y botón Maps; solo por comprobar en Chrome Android. La oferta final sigue perteneciendo al Conductor. No se habilitó ETA de conductor hasta pasajero.

---

## 0H. DISPONIBILIDAD AUTOMÁTICA POR JORNADA Y CONDUCCIÓN RECIENTE — 09/10/2026

**Nueva definición del usuario:** «Si estoy con jornada iniciada y en conducción reciente, de algún modo se lo mostrará como disponible y mi distancia y tiempo». Quiere **estado automático** y **km/min Conductor→origen pasajero** en «Ahora / En 10 minutos» sin introducir distancia manualmente.

**Auditoría del código real:** `mapatrayectos/TrackingService.java` ya registra `shiftActive`, `shiftPaused`, `tripActive`, `vehicleMoving`, `lastAcceptedTs`, `lastAcceptedAccuracy` y coordenadas válidas; `broadcastState` transmite esas señales **dentro del teléfono**. El sincronizador Mapa/Supabase existente sube jornadas/puntos al finalizar viajes, **NO** es telemetría comercial en vivo. No se ha construido el servidor ni la APK que publique ubicación comercial; no comunicar que la función está activa.

**Política de producto acordada para desarrollar:** con consentimiento inicial visible y revocable («Compartir disponibilidad para recogidas», OFF por defecto), jornada iniciada y no pausada, GPS ≤90 s/precisión ≤45m, conducción verificada recientemente (≤5 min), sin viaje Mapa en curso y sin conflictos de agenda, el sistema muestra **«Disponible para consultas»** y solicita km/min reales por ruta ORS desde ubicación del conductor al origen A. No exige estar moviéndose exactamente en ese segundo (puede detenerse en semáforo). En jornada pausada/cerrada, viaje activo, falta GPS, dato viejo o «Ocupado manual», oculta distancia y ETA. Uber/Cabify externos no son detectados por Mapa: por eso debe haber «Ocupado» manual de un toque y la solicitud no se considera aceptada hasta confirmar. Para reservas programadas no predecir desde dónde estará el conductor en el futuro.

**Arquitectura necesaria:** heartbeat Android→Supabase cada 20–30s con autenticación PIN/dispositivo, tabla privada, caducidad server 90s; servicio Cloudflare a Supabase autenticado server-to-server para leer ubicación privada y consultar ORS sin revelar lat/lon en la web, con cuota/caché; respuesta pública únicamente estado/km/ETA. Dejar la función apagada por defecto hasta consentir. La lógica urgente requiere tratamiento separado de `lead_time_min=30` de Supabase, sin reservar de modo automático.

**Documento técnico específico creado:** [DISPO_AUTOMATICA_Y_ETA_RECOGIDA.md](DISPO_AUTOMATICA_Y_ETA_RECOGIDA.md), commit [08594ed](https://github.com/marcelofgx-ctrl/traslados-android/commit/08594eda6bb9feeb4bd1c5c8fa919710267ce9cc). **Esto es especificación técnica respaldada por código existente, NO implementación ni release nueva de ubicación viva**. Prioridad P0 para la siguiente intervención: programar captura/heartbeat privado con consentimiento; P1: motor ETA seguro y visualización PWA/Web; P2: conflictos de agenda y confirmaciones inmediatas.

---

## 0I. ENTREGA GPS EN VIVO / ETA REAL — 10/10/2026 (último corte)

**Solicitud del usuario:** «Adelante con todas las modificaciones en todo». Se realizaron cambios reales en **Supabase operativo, Mapa Android, Cliente Android nativo, PWA Pasajero GitHub Pages y Web Premium Cloudflare**, preservando reservas, datos GPS históricos, SQLite y sonidos. **No se activó ni simuló una ubicación personal del conductor.** Hacen falta opt-in voluntario desde la nueva APK y prueba física Samsung. Se mantiene un único contexto maestro y el documento especializado [DISPO_AUTOMATICA_Y_ETA_RECOGIDA.md](DISPO_AUTOMATICA_Y_ETA_RECOGIDA.md).

### Arquitectura implementada y probada

**Backend Supabase `zetaudvvutlouiqxopvg`, YA APLICADO:** migración [pickup_live_presence_v1.sql](https://github.com/marcelofgx-ctrl/traslados-web/blob/main/operativa/migrations/20261010023000_pickup_live_presence_v1.sql), `driver_live_presence` privada RLS sin SELECT anon/authenticated, OFF por defecto, vinculada al dispositivo Mapa y PIN validado de Conductor. El heartbeat `mapa_presence_ping_v1` guarda ubicación solo con jornada activa, no pausada, no viaje en Mapa, no «ocupado manual», GPS ≤90s con precisión ≤45m y conducción ≤5 minutos. No confundir conducir con estar libre haciendo Uber/Cabify: el estado es **«Disponible para consultas, sujeto a aprobación»** y existe override ocupado. Cuando no está activo no conserva lat/lon publicables. Nuevo `driver_pickup_eta_context_v1` solo ejecutable desde `service_role`, verifica sesión Cliente real, comprueba 60min de agenda y limita 3 consultas/min y 20/h. Pruebas SQL: sin registro de presencia inicial, SELECT de tabla y RPC de coordenadas denegados a anon, sesión inválida rechazada.

**Edge Supabase `pickup-eta` versión 1 YA DESPLEGADA ACTIVE:** requiere token de sesión propio de Cliente en POST; `verify_jwt=false` justificado exclusivamente por autenticación custom PIN + consulta verificada de sesión, nunca acepta cualquier visitante como autorizado. Consulta ORS a través del Worker Cloudflare **servidor-a-servidor**, devuelve km redondeados a 0,5 km, ETA redondeada a 5 min, antigüedad y disponibilidad **sin lat/lng**. Test público de sesión inválida [Actions 38019205391](https://github.com/marcelofgx-ctrl/traslados-web/actions/runs/38019205391) **SUCCESS**, HTTP 401 y sin coordenadas; matriz Bun 1.2/1.3 con build/TypeScript y contratos de seguridad aprobados. El flujo positivo necesita opt-in del teléfono antes de poder probarse.

**Mapa Trayectos R24.5 / versionCode 47:** compilado con `TrackingService` GPS ya filtrado, nuevo modo `Menú → Disponibilidad de recogida`: consentimiento inicial con PIN de conductor, compartir OFF por defecto, ocupado manual Uber/Cabify, desactivar/borrar posición. Heartbeats no bloqueantes cada ~25s y al cambiar jornada, pausa, viaje y modo ocupado; sin PIN guardado, sin lat/lng públicas. Firma APK estable. [Actions 38019254898](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38019254898) **SUCCESS** (nombre del ZIP corregido a R24.5, huella SHA256 firma d91f4b9a37f4c77653fdf18fe792e7011a046dd2c09fded1e13fd29d4267269d). Parche adicional [ae81b5a](https://github.com/marcelofgx-ctrl/traslados-android/commit/ae81b5adb63c5b3aa90b0c8aa396f6af15f54900) garantiza que desactivar el permiso **corta localmente** las subidas GPS aun sin Internet. **RELEASE DEFINITIVA comprobada** [Actions 38019431505](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38019431505) SUCCESS, artefacto **11657985357**, `Mapa-Trayectos-v0.1-R24-5-RELEASE`. APK descargada y comprobada SHA256 **156e6e1b75b1db9b0ed0a162846bd08afae09e45c69d88ba380e5f67ae4d0c05**, firma v2 verdadera con certificado permanente d91f4b9a37f4c77653fdf18fe792e7011a046dd2c09fded1e13fd29d4267269d. No desinstalar ni borrar datos para actualizar.

**Cliente nativo Android 11.5-R11/versionCode 121:** `cliente-pasajero/scripts/build_cliente_r11.py` y `ClienteTripEnhancements.showPickupEta`: los accesos AHORA/+10 MIN con una sesión real y punto de origen abren diálogo de km/ETA mediante la Edge Function; no crean reserva inválida (lead time de 30min); WhatsApp para consultar. **APK RELEASE firmada** [Actions 38019069484](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38019069484) **SUCCESS** (artefacto 11657810025). CI R10 antiguo desactivado salvo ejecución manual; hubo fallos de compilación intermedios antes de añadir el import de HttpURLConnection, corregidos. Integración de GUI y uso real Samsung todavía no probados.

**Pasajero Web/PWA GitHub Pages v12:** `web-pasajero/app.js` llama la Edge Function autenticada si el usuario marca A y «Ahora/En 10 min», refrescando a intervalos prudentes en pantalla activa; muestra km y minutos aproximados de Conductor→A solo cuando hay posición válida. Sin sesión requiere iniciar sesión; conserva WhatsApp y no registra automáticamente un viaje urgente. JS y caché PWA v12. CI [38019114673](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38019114673) **SUCCESS** y publicación Pages [38019114312](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38019114312) **SUCCESS**. Probar v12 en Samsung, no confundir publicación con prueba visual.

**Web Premium Cloudflare:** nuevos `src/components/DriverPickupEta.tsx`, importado en `src/routes/index.tsx` durante modos inmediatos, UX petróleo/champagne con km/min/estado, login requerido. [Actions 38019205391](https://github.com/marcelofgx-ctrl/traslados-web/actions/runs/38019205391) **SUCCESS**. **El deploy de la nueva Web Premium en Cloudflare no fue ejecutado**, aunque ORS_API_KEY ya estaba configurada y los kilómetros A→B funcionaban antes: todavía hay que hacer el despliegue vía el flujo manual del repositorio o desde la cuenta Cloudflare. NO afirmar que el UI Premium en producción refleja estos cambios.

**Conductor independiente:** mantiene gestión de reservas y presupuestos y conserva su release previa; no publica GPS por separado, porque la fuente oficial de jornada y GPS es Mapa. No sustituir el origen de señales creando un segundo tracking.

### Condición real de disponibilidad y privacidad

1. Consentimiento explícito inicial una sola vez en Mapa con PIN; después basta jornada + conducción GPS reciente. Un viaje Uber/Cabify **no detectado por Mapa** exige marcar «Ocupado» manualmente.
2. Solo sesión Cliente real obtiene ETA redondeada a partir del origen seleccionado; no ve la posición del vehículo. Nunca se promete recogida sin aprobación.
3. Si cierra/pausa jornada, viaje activo, ocupado, última conducción >5min, GPS >90s, conflictos de agenda o cliente sin sesión, **no publicar ETA**. Al optar por OFF se interrumpe subida desde el dispositivo incluso sin red y presencia remota se invalida por TTL.
4. Programados usan su agenda, **nunca** proyección de posición actual a fecha futura. «Ahora/10 min» son consultas con confirmación personal hasta crear flujo específico aprobado para reservas urgentes.

### QA de entrega y pendientes estrictos

- **COMPILACIONES APK y firma verificadas**: Mapa R24.5 final del run 38019431505, SHA256 156e6e1b75b1db9b0ed0a162846bd08afae09e45c69d88ba380e5f67ae4d0c05; Cliente R11 de 38019069484, SHA256 b4ba2ffa7947ac44a1ed714203923c8350ca797d6e0a61f9fb593f7b3ca3b1a1. Ambas usan el certificado de producción y contienen AndroidManifest/classes.dex. Distribuidas como dos APK descargables en la respuesta al usuario de esta sesión; la instalación física queda pendiente.
- Usuario instala Mapa R24.5 encima de la versión actual (no desinstalar) y entra en Menú → Disponibilidad de recogida → PIN/compartir. Sin autorización no hay presencia: es una propiedad de privacidad, no un fallo.
- Usuario inicia jornada y conduce normalmente, luego inicia sesión en Cliente Web v12 y selecciona A + Ahora. Comprobar distancia conductor→A con ORS y ocultación al cerrar, pausar, ocupar y perder GPS; validar red móvil, pantalla bloqueada.
- Probar Cliente R11 en Samsung, y luego publicar Web Premium Cloudflare nueva versión. **Ningún tramo positivo real de ETA se ha comprobado con GPS personal hasta que el conductor active y pruebe la APK**.
- Probar reserva programada real consentida → Conductor → presupuesto final → aceptación → historial sin datos de prueba persistentes. No cambiar backend para auto-confirmación inmediata sin definir reglas de negocio y consentimiento.

---

## 0J. DISPONIBILIDAD EN VIVO SIN LOGIN EN PASAJERO (10/10/2026)

**Último reclamo y captura:** la PWA v12 mostraba «Iniciá sesión en Mi cuenta para consultar la llegada aproximada». El usuario aclaró: **la disponibilidad pública tiene que tomarse en tiempo real desde su aplicación Mapa Trayectos**, sin obligar a los visitantes a crearse una cuenta. La distancia/ETA específica origen→conductor continúa restringida a sesiones de cliente para evitar rastreo triangulando varios orígenes.

**Backend actualizado y aplicado REALMENTE al Supabase operativo:** [`public_driver_availability_v1.sql`](https://github.com/marcelofgx-ctrl/traslados-web/blob/main/operativa/migrations/20261010034000_public_driver_availability_v1.sql) (commit `0c8d669`), RPC pública `public_driver_availability_v1()` retorna únicamente **`available`, `status`, `confirmationRequired`**. Calcula según presencia opt-in de Mapa R24.5, GPS/heartbeat ≤90s, conducción ≤5min, jornada iniciada/no pausada, no viaje/ocupado manual, precisión ≤45m y sin conflicto de agenda. **No devuelve coordenadas, ubicación exacta, dispositivo, datos de pasajeros ni historial**. Seguridad corroborada: `anon` puede ejecutar solo RPC de estado, NO puede consultar `driver_live_presence` ni ejecutar la RPC privada que entrega GPS al backend. **Estado real durante auditoría: 0 dispositivos autorizados/0 señales GPS actuales → `not_available`**. Es necesario que el usuario abra **Mapa R24.5 → Menú → Disponibilidad de recogida**, autorice una vez con PIN y tenga jornada activa/conducción GPS reciente. NO inventar «Disponible» por tener la web abierta.

**PWA pública GitHub Pages v13:** `web-pasajero/app.js` consulta directamente el RPC público **ANTES** de `authReady()`. Si la jornada está activa muestra «Conductor disponible para consultas» aunque no haya login, refresca cada 40s con cache ≤25s y mantiene WhatsApp para urgencias. Si además hay cliente autenticado y origen A válido, usa `pickup-eta` para km/min redondeados; a visitantes anónimos NO se filtra ubicación ni permite calcular origen→vehículo repetidamente. Cambios visuales discretos en CSS, HTML `app.js?v=13`, `styles.css?v=13`, SW v13. Test VM ejecuta función real `public-driver-presence-smoke.cjs`: estado público invitado, desactivado, cliente firmado y no repetición A/B. **CI [38020967639](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38020967639) SUCCESS, Pages [38020967392](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38020967392) SUCCESS**. Ruta pública: https://marcelofgx-ctrl.github.io/traslados-android/web-pasajero/?v=13. Últimos commits `c02ac3f`, `05c38b8`, `f99f6e5`.

**Web Premium:** componente `DriverPickupEta.tsx` consume el mismo RPC público sin login, y Edge ETA privada solo con sesión/origen. [CI Web 38020875344](https://github.com/marcelofgx-ctrl/traslados-web/actions/runs/38020875344) **SUCCESS** sobre commit `c3bcb93`. **Todavía falta desplegar la Web Premium actualizada en Cloudflare**; CI verde no representa producción.

**Cliente Android actualizado a 11.5-R12 / versionCode 122:** `ClienteTripEnhancements.showPickupEta` ahora consulta el estado de Mapa **sin inicio de sesión** y, cuando hay sesión cliente/origen, los km/min de recogida desde la Edge privada. APK RELEASE firmada y CI [38021100002](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38021100002) **SUCCESS**; artefacto GitHub **11658906107**, SHA256 `ac4dc6ae06705d9764fc25333f7b876f10ea8ab8586c7c6dac471bb26e8545c4`, certificado v2 de producción SHA256 `d91f4b9a37f4c77653fdf18fe792e7011a046dd2c09fded1e13fd29d4267269d`. Los fallos intermedios de compilación R11/R12 por final String fueron corregidos en `be1d8e9`; **no confundir con R12 éxito**. R11 pasó a ser workflow histórico manual; R12 es la versión nativa nueva. APK de R12 generada localmente como `/mnt/data/Traslados_Cliente_R12_RELEASE.apk` y verificada con ZIP y hash. **La instalación y prueba Android real siguen pendientes**.

**Mapa Trayectos** permanece R24.5 (no fue recompilado en este cambio; ya envía señal privada con consentimiento). Distinguir siempre: **backend activo / web publicada / compilaciones firmadas / GPS real no autorizado aún / QA Samsung pendiente**. Detalle técnico actualizado en [`DISPO_AUTOMATICA_Y_ETA_RECOGIDA.md`](DISPO_AUTOMATICA_Y_ETA_RECOGIDA.md). Poner como prioridad activar compartición consentida desde Mapa y probar desde PWA anónima el cambio de estado sin inventar posición; luego probar ETA con cliente autenticado.

---

## 0K. Solicitud km y minutos públicos para recogida (10/10/2026)

**Captura y necesidad:** la web PWA v13 muestra correctamente «Conductor disponible para consultas · Jornada activa» desde Mapa; el usuario quiere que **también un visitante sin iniciar sesión** vea kilómetros y minutos aproximados del conductor hasta el origen A elegido. No confundir con el cálculo de tarifa A→B, que ya funciona.

**Verificación REAL actual en Supabase:** `driver_live_presence` contiene **1 dispositivo habilitado con heartbeat reciente** y `public_driver_availability_v1()` devuelve `available:true, status:"available_for_requests"`. La conexión Mapa → Supabase → Web está funcionando con datos reales. Solo `service_role` puede leer la posición o ejecutar `driver_pickup_eta_context_v1`; anónimos no pueden.

**Preparación de privacidad, aún sin interfaz operativa:** se versionó y aplicó en Supabase la migración [anonymous_pickup_eta_coarse_v1.sql](https://github.com/marcelofgx-ctrl/traslados-web/blob/main/operativa/migrations/20261010042000_anonymous_pickup_eta_coarse_v1.sql), commit `8c78e72`. Crea una RPC **solo para service_role** con redondeo privado de coordenadas del conductor a celdas ~1 km, limitación global y por visitante, máximo 3 celdas de origen distintas por hora. La prueba confirmó `anon_can_read_coarse=false`. **IMPORTANTE: el código de Edge / PWA / Cliente NO fue actualizado ni desplegado para consumir esta RPC**: la operación de actualización del servicio fue bloqueada por una verificación de seguridad y no hay distancia/ETA pública anónima activa. No declarar entregada esa funcionalidad ni inventar los números; continuar solo con un diseño que supere las comprobaciones de privacidad y autorización.

**Actual hoy:** web puede afirmar «Disponible» sin sesión; para km/min Conductor→A el servicio `pickup-eta` todavía requiere sesión válida. La nueva migración privada no cambia esa política por sí sola. Evitar confundir «base preparada» con «producto terminado».

---

## 0L. CORRECCIÓN DE PRIORIDAD DEL USUARIO — WEB PRINCIPAL WORKERS (10/10/2026)

**Decisión inequívoca del usuario:** «Pero se supone que yo quiero la versión más completa en la ruta workers». **ÚNICA WEB PÚBLICA PRINCIPAL Y OBJETIVO FINAL:** [https://traslados-web.marcelof-gx.workers.dev/](https://traslados-web.marcelof-gx.workers.dev/), repositorio `marcelofgx-ctrl/traslados-web`, app TanStack/Cloudflare Workers. **No recomendar GitHub Pages como principal**: `marcelofgx-ctrl.github.io/traslados-android/web-pasajero/` es PWA auxiliar/beta para QA y comparaciones mientras se comprueba paridad. Evitar que un pasajero reciba enlaces diferentes como equivalentes.

**Comprobación real del código:** Workers contiene el formulario avanzado con paradas, A→B km/min y tarifa, reservas/agendas/historial, Passkeys y el estado de llegada para clientes autenticados. Gap detectado: disponibilidad en vivo estaba dentro de `Booking` que exige sesión. Se creó `src/components/DriverLiveStatus.tsx` y se insertó en `PremiumHome.tsx`, para mostrar disponibilidad Mapa sin login en la portada principal con refresco cada 40s y al retomar pestaña, sin exponer GPS. Tests `tests/workers-canonical.test.ts` y CI actualizados. **No confundir código en main / CI SUCCESS con Worker publicado**.

**Regla de publicación:** workflow `traslados-web/.github/workflows/publicar-cloudflare-manual.yml` conserva activación exclusivamente manual, verifica build/tests/TS/estado Mapa/ORS y requiere secretos `CLOUDFLARE_API_TOKEN`, `CLOUDFLARE_ACCOUNT_ID` en GitHub; no se tienen las credenciales Cloudflare conectadas a este chat ni existe invocación de workflow dispatch entre las acciones GitHub disponibles. **El despliegue de los últimos cambios NO está verificado**, de modo que no asegurar que la URL Workers muestre ya el banner nuevo. `keep_vars:true` conserva las variables remotas y no debe incluirse la API ORS en GitHub.

**Gap de producto a resolver:** en Workers `go("reserva")` redirige a iniciar sesión antes de dejar elegir A/B; la PWA Pages permite seleccionar A/B previamente. Lograr paridad premium en Workers sin activar solicitudes anónimas reales: mostrar cálculo de ruta y presupuesto de referencia antes de autenticarse, pero exigir login/confirmación antes de guardar reserva. No sustituir la web Workers por PWA ni redirigir Pages antes de validación integral. Km/min Conductor→A sin login continúan restringidos por privacidad, ya que la API pública anónima no está desplegada.

**Documento web específico:** [WEB_PRINCIPAL_WORKERS.md](https://github.com/marcelofgx-ctrl/traslados-web/blob/main/docs/WEB_PRINCIPAL_WORKERS.md). Este apartado anula sugerencias antiguas que promovían GitHub Pages como web canónica.

---

## 0M. PRESUPUESTO PREVIO A LOGIN EN WEB WORKERS (10/10/2026)

**A pedido expreso del usuario:** habilitar en la URL PRINCIPAL Workers que el visitante elija A/B, opcionalmente hasta ocho paradas, y vea **km por carretera, minutos y tarifa de referencia ANTES de registrarse o iniciar sesión**; solo exigir acceso antes de enviar la solicitud. La PWA GitHub Pages continúa como secundaria.

**IMPLEMENTADO en `marcelofgx-ctrl/traslados-web` main y CI aprobado:**
- Componentes nuevos `src/components/GuestRoutePlanner.tsx`, `src/components/GuestRouteDetails.tsx` y módulo `src/lib/guest-route-draft.ts`, reutilizando buscador oficial `UyLocationPicker`, tarjeta premium `BookingQuickSummary`, ORS público seguro y paradas configurables (máximo ocho, reordenables). Sin km ni tarifas inventados; motor no disponible → «A confirmar».
- `src/routes/index.tsx` ahora abre `GuestRoutePlanner` con `go("reserva")` también sin sesión; NO llama a `createReservation` ni a funciones de disponibilidad privada para visitantes. CTA «Continuar para solicitar» guarda sólo direcciones y paradas en estado React y `sessionStorage` con TTL de dos horas; al iniciar sesión `Booking` restaura las selecciones en sus estados iniciales, conserva la lógica programada de agenda/presupuesto privado, y recién un cliente autenticado puede confirmar y enviar la solicitud. Tras envío satisfactorio se elimina el borrador.
- Tests `tests/guest-booking.test.ts` con casos Uruguay/lat-lng/paradas/límite de 8, cálculo público previo a login, restauración y ausencia de reservas anónimas. Se integraron a CI general y al flujo de publicación manual. **[GitHub Actions 38023590926](https://github.com/marcelofgx-ctrl/traslados-web/actions/runs/38023590926) SUCCESS**: Bun 1.2/1.3, pruebas, compilación Workers/TanStack y TypeScript estricto. HEAD en ese run commit `065901098`.
- **DESPLIEGUE EN PRODUCCIÓN WORKERS PENDIENTE:** no se ejecutó el workflow manual `.github/workflows/publicar-cloudflare-manual.yml`; este chat carece de credenciales Cloudflare conectadas para lanzar `wrangler deploy`, y no hay herramienta GitHub `workflow_dispatch` disponible. El usuario debe ejecutarlo en GitHub Actions (con `CLOUDFLARE_API_TOKEN` y `CLOUDFLARE_ACCOUNT_ID` en secrets) y comprobar `https://traslados-web.marcelof-gx.workers.dev/` desde Samsung. CI verde no equivale a Worker publicado. El secreto ORS debe permanecer configurado como Secret remoto; `wrangler.jsonc` lo mantiene fuera de GitHub con `keep_vars:true`.

**Verificación final posterior:** ajuste de seguridad de cotización en `GuestRoutePlanner`: cuando una parada agregada aún no tiene ubicación, se suprime la estimación hasta que se complete o elimine; de otro modo se cotizaría por error una ruta A→B sin esa parada. Commit Workers [6872670](https://github.com/marcelofgx-ctrl/traslados-web/commit/6872670ea3ee6ef1b261b9db780e431a7a75e305), [CI 38023721789](https://github.com/marcelofgx-ctrl/traslados-web/actions/runs/38023721789) **ambos jobs SUCCESS** (Bun, tests, TypeScript, compilación y smoke HTTP local). Publicación manual Cloudflare **todavía no ejecutada**.

**El pedido anterior de ETA anónimo Conductor→origen A es DIFERENTE** de este precio A→B: no prometer esos km/min del conductor a visitantes sin sesión mientras la Edge privada no tenga autorización/privacidad verificadas. El nuevo formulario público permite cotizar el **recorrido del pasajero**, no rastrear la posición del conductor.

---

## 0N. RELEASE WORKERS PREPARADA; FALTA SOLAMENTE PUBLICACIÓN MANUAL (10/10/2026)

**Pedido actual del usuario:** «Deja ya todo listo». Se verificó el estado real de `marcelofgx-ctrl/traslados-web`: commit de código principal **[`6872670`](https://github.com/marcelofgx-ctrl/traslados-web/commit/6872670ea3ee6ef1b261b9db780e431a7a75e305)** con `GuestRoutePlanner`, cálculo de carretera km/min y tarifa antes de login, hasta ocho paradas y alerta cuando hay paradas incompletas; borrador A/B/paradas de hasta 2 h en `sessionStorage`, y reservas únicamente tras autenticación. `DriverLiveStatus` toma jornada de Mapa sin GPS público en portada. Workers continúa como **única web principal**; Pages es auxiliar.

**Verificación técnica:** [CI GitHub Actions **38023721789**](https://github.com/marcelofgx-ctrl/traslados-web/actions/runs/38023721789) **SUCCESS en 2 jobs** (Bun 1.2.15 y 1.3.4), TypeScript, tests de guest booking, privacidad, agenda, motor ORS y build. Artifacts `traslados-web-cloudflare` disponibles. Workflow de despliegue [`publicar-cloudflare-manual.yml`](https://github.com/marcelofgx-ctrl/traslados-web/actions/workflows/publicar-cloudflare-manual.yml) incluye pruebas, chequeo de credenciales, Wrangler deploy y prueba HTTP contra URL principal.

**Bloqueador único de publicación:** GitHub App conectada NO ofrece acción `workflow_dispatch` ni lectura de los secretos de Actions, y no hay Cloudflare conectado. Se intentó habilitar publicación puntual mediante la modificación del workflow, pero la operación fue rechazada por controles de permisos y **NO cambió el workflow de publicación ni desplegó Workers**. **No intentar sortear esa autorización por vías laterales.** El propietario debe abrir el workflow y elegir **Run workflow → main → Run workflow**; requiere los secrets `CLOUDFLARE_API_TOKEN` y `CLOUDFLARE_ACCOUNT_ID` previamente configurados. Si faltan, asignarlos en GitHub Settings → Secrets and variables → Actions (nunca compartir claves en chat). `wrangler.jsonc` contiene `keep_vars:true` y no contiene ORS_API_KEY. **Nunca afirmar «Workers publicado» sin run manual SUCCESS y verificación de producción.**

**Documento de entrega para siguiente sesión:** [RELEASE_WORKERS_PREPARADO_20261010.md](https://github.com/marcelofgx-ctrl/traslados-web/blob/main/docs/RELEASE_WORKERS_PREPARADO_20261010.md), con versiones exactas, enlaces, checklist de Samsung, limitaciones y ruta de publicación. No quedan cambios de código obligatorios para publicar la mejora A/B antes del login; sí queda QA física posterior al deploy. No confundir cálculo público A→B con ETA privado Conductor→A para evitar triangulación GPS.

---

## 0P. URL ESTABLE WORKERS Y REVISIÓN PREMIUM R3 — 10/10/2026

**Captura del usuario:** dirección `traslados-web.marcelof-gx.workers.dev`, formulario firmado con sección «¿Cuándo querés que te pasemos a buscar?», recuadro grande «Recogida lo antes posible» y panel inferior «Disponibilidad de Mapa Trayectos». El usuario percibe que es versión antigua aunque le gusta portada. Quiere que **la versión más completa y perfeccionada esté SIEMPRE en esa misma dirección**; no cambiar hostname ni abrir un Worker con nuevo nombre.

**Verificación:** `traslados-web/wrangler.jsonc` fija `"name":"traslados-web"`, `PASSKEY_PUBLIC_ORIGIN:"https://traslados-web.marcelof-gx.workers.dev"`, `keep_vars:true`. No hay evidencia en GitHub Actions de una publicación manual reciente, aunque sí CI exitosa para el nuevo formulario de invitado. La captura es compatible con la vista autenticada **Booking**, que seguía usando componentes de recogida anteriores a `GuestRoutePlanner`; NO concluir solamente de la captura que el código publicado sea exactamente un commit u otro. La burbuja circular con 9+ de Mapa en la captura es superposición Android externa a Workers, no parte del layout web.

**Cambios reales en main en esta sesión:**
- `src/components/PickupModePicker.tsx`: rediseño más compacto y premium de Ahora / En 10 min / Programar, sin texto gigante ni bloque repetido, enlaces WhatsApp discretos y detalles opcionales. Mantiene consulta sujeta a confirmación.
- `src/routes/index.tsx`: `DriverPickupEta compact` integrado como **hijo dentro de una sola tarjeta** de horario y recogida, en vez de dos grandes recuadros consecutivos. Conserva booking, paradas y pagos sin cambios; añade marcador de revisión visible en pie «Web principal · Workers R3» y `data-web-release="workers-2026-10-10-r3"`.
- `src/components/DriverPickupEta.tsx`: variante compacta dentro de la tarjeta, basada en estado de Mapa y API de ETA sin exponer coordenadas; origen/session según privacidad.
- `.github/workflows/publicar-cloudflare-manual.yml`: bloquea nombre del Worker distinto de `traslados-web`, PASSKEY_PUBLIC_ORIGIN distinto, pérdida de `keep_vars` o inclusión indebida de ORS_API_KEY en config; tras deploy exige marca R3 del HTML en producción, motor ORS válido y tarifa. Sigue siendo `workflow_dispatch` MANUAL; **no** crea Workers adicionales ni toca el hostname.
- `tests/workers-canonical.test.ts`: contrato de hostname, variante compacta y marcador de versión. Último commit de código `4d4152e1ff0b6d10a57169cf3c6ea06eda29cd6a`.

**Estado de release comprobado:** [CI 38024838373](https://github.com/marcelofgx-ctrl/traslados-web/actions/runs/38024838373) SUCCESS en los dos jobs Bun; verificación de tipado/build/tests Workers R3 sobre commit `4d4152e1f`. La actualización posterior de documentación no cambia código del producto. **La URL no cambia**, pero la revisión R3 de código solo aparecerá tras publicar desde el workflow manual [Publicar Traslados Web en Cloudflare](https://github.com/marcelofgx-ctrl/traslados-web/actions/workflows/publicar-cloudflare-manual.yml), confirmar SUCCESS y ver en pantalla el marcador «Workers R3». Actualmente no hay acceso a Cloudflare ni acción GitHub `workflow_dispatch` entre las herramientas disponibles. No anunciar despliegue completado sin comprobarlo. Si el usuario ya tiene sesión, debe revisar específicamente el flujo autenticado de horario, además del nuevo preview previo a login.

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
