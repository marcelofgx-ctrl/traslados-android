# Quick Trip v90 — auditoría visual y propuesta PWA caché / GPS
**Fecha:** 26/10/2026 (UYT)
**Tipo:** informe + propuesta exclusivamente QA, NO implementación.
**Rama:** `qa/quicktrip-v90-acceleration-pilot-20261026`.
**Artefacto examinado:** GitHub Actions [#40263308893](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/40263308893), `QuickTrip-v90-Acceleration-QA-Evidence` (id 12625452504), dos JSON + 12 PNG originales.
**Base de producto v90:** `8d882e63e1a4e779f956868af64c9e6158c4c0ac`.
**Protecciones:** main, producción, Supabase, Worker, motores estables, GPS real, credenciales, reservas y APK quedan fuera del alcance. Sin despliegue, fusión o compilación.

## A. Qué se verificó de verdad
- `baseline/QA_V90_RESULT.json`: **PASS 26/26**, 6 capturas (360 y 393 CSS px). `pageErrors=[]`; `externalRequests=[]`; `realBackendRequests=0`.
- `negative/QA_V90_PILOT_RESULT.json`: **PASS 33/33**, otras 6 capturas. `pageErrors=[]`; `unexpectedExternal=[]`; `realBookingRequests=0`.
- Ruta sintética 8,6 km / 15 min / 344 UYU (40 UYU/km); una parada 12,5 km / 20 min / 500 UYU; dos paradas 18,0 km / 720 UYU. **No son estimaciones reales de calles**.
- Conductor ficticio disponible/no disponible, cierre de datos al fallar OSRM, recuperación después de desconexión. Botón `#urgent-send` deshabilitado.
- Las 12 imágenes son capturas de Chromium emulando anchos 360 y 393; **no** son capturas físicas de Samsung.
- Todas las pruebas Playwright v90 de este artefacto usan `serviceWorkers:"block"`; por tanto no ensayan activación, actualización ni caché real de la PWA. Tampoco ejercen el botón GPS ni permisos de Android. El campo `realGpsRequests:0` en el JSON es un valor inicial; no sustituye medición de llamadas.

## B. Hallazgos visuales basados en capturas
| ID | Nivel | Evidencia | Diagnóstico / próximo test |
|---|---|---|---|
| V90-UI-01 | **P1 por verificar** | En los PNG fullPage `negative/01-verified-360.png`, `baseline/03-route-failure-no-invented-data-393.png` la navegación inferior cruza visualmente la tarjeta Conductor y/o Horario/Tus datos. | Se aprecia superposición **en el PNG**, pero una captura `fullPage` con navegación `fixed/sticky` puede producirla en el punto de scroll inicial. **No afirmar aún que bloquee elementos en uso**. Añadir fotos de viewport con scroll a tarjetas, boundingRects y prueba táctil de controles sobre el área inferior. |
| V90-UI-02 | **P2 confirmado en captura** | `baseline/03-route-failure-no-invented-data-393.png`: aparecen simultáneamente `Mapa pendiente de verificación`, `Recorrido de prueba`, `VALOR A confirmar` y dos tarjetas `Sin estimación`. | Datos honestos, pero información visual redundante tras ruta inválida; evaluar una sola jerarquía/estado sin alterar lógica, solo si se aprueba cambio visual. |
| V90-UI-03 | **P2 confirmado en captura** | `negative/01-verified-360.png`: botón dorado `SOLICITAR VIAJE` mantiene apariencia de CTA aunque la propiedad `disabled` esté comprobada. | Riesgo de expectativa errónea en QA read-only. Probar opacidad, etiqueta accesible `No disponible en QA`, intento de clic y texto de contexto sin habilitar reservas. |
| V90-UI-04 | **P3, claridad** | Encabezado `Precio del viaje` junto a `Precio ilustrativo`; bloque `Tu conductor` con datos explícitamente ficticios. | Las advertencias son correctas, pero verificar que un observador rápido no confunda demo con cotización o estado real; no cambiar la tarifa ni la jerarquía aprobada sin autorización. |
| V90-UI-05 | **Cobertura ausente** | Capturas muestran el mapa plegado; no hay imágenes del trazado expandido, zoom de fuente grande ni contraste alternativo. | No es defecto probado. Extender captura y aserciones de accesibilidad en QA. |

**Conclusión visual:** sin evidencia de ruptura funcional P0. La prioridad es confirmar V90-UI-01 en el viewport, y reducir mensajes duplicados en fallo solo tras aprobación del ajuste visual.

## C. Propuesta de pruebas automáticas de PWA/caché (sin publicar)
**Preparación:** un servidor HTTP local aislado `127.0.0.1`, contexto Chromium limpio `serviceWorkers:"allow"`, scope temporal exclusivo; dos copias *temporales* de assets v90 y una versión simulada posterior para probar actualización. Nunca alterar `web-pasajero/sw.js` en el repo ni publicar. Instrumentar Cache Storage, `navigator.serviceWorker.controller`, versión de SW, recurso recibido y solicitudes externas reales. El SW real `sw.js` utiliza `traslados-pwa-qa-route-v90-20261025`; hay un test anterior `quicktrip-v86-canonical-pwa-cache.cjs`, a reutilizar/adaptar.

| ID | Escenario | Criterio de aceptación |
|---|---|---|
| PWA-01 | Primera visita y registro de SW en scope local | Cache v90 y assets precargados; no hay reservas ni llamadas externas. |
| PWA-02 | Cierre/reapertura bajo desconexión | Se muestra offline.html o recurso válido en caché, sin datos de reserva inventados. |
| PWA-03 | Actualización simulada v90→v91 en mismo origen QA local | Nuevo SW controla al cliente y recursos JS/CSS actualizados; cache v90 se depura según prefijos previstos, sin eliminar cachés ajenos. |
| PWA-04 | Assets con `?v=90` y `?v=91` desconectado | Nunca recupera por error un script antiguo si existe versión nueva; registrar respuesta real y clave CacheStorage. |
| PWA-05 | Red intermitente durante actualización | No mezcla HTML nuevo con JS/CSS antiguo; si no hay paridad, falla con mensaje claro, sin iniciar acciones sensibles. |
| PWA-06 | Caché y vuelta de pestaña instalada/standalone simulada | PWA reabierta respeta estado QA read-only, tres accesos y versión; esto **no** sustituye instalación en Samsung. |
| PWA-07 | Navegación sin conexión y posterior recuperación | La PWA no envía solicitudes pendientes automáticamente ni duplica reservas; su JS de solo lectura no llama a backend. |

**Punto técnico:** `context.route` no intercepta necesariamente respuestas servidas desde un Service Worker; medir fetches y caché mediante servidor local/control del worker, no interpretar "cero solicitudes" como "SW verificado". Bloquear acceso a Supabase/Worker y prohibir GPS real.

## D. Propuesta de pruebas automáticas de permisos GPS (sin GPS físico)
**Preparación:** contexto nuevo por caso, `serviceWorkers:"block"` para aislar permisionado; coordenadas y calles totalmente sintéticas; mock de `direcciones.ide.uy/api/v1/geocode/reverse`; todo otro origen bloqueado. Reutilizar el test existente `quicktrip-v89-gps-ux-qa.cjs` pero actualizar su aserción CSP de IDE-only a **IDE + OSRM en v90**, sin modificar motores estables.

| ID | Escenario | Criterio de aceptación |
|---|---|---|
| GPS-01 | Sin pulsar `Mi ubicación` | Cero lecturas de GPS o reverse-geocoding; destino no cambia. |
| GPS-02 | Permiso ya concedido y toque del usuario | Solo se actualiza origen con coordenadas ficticias; IDE mock una vez; sin prompts adicionales. |
| GPS-03 | Permiso `prompt` emulado | Ayuda se expande, texto y botón de autorización visibles; nunca afirmar que se mostró un diálogo Android real. |
| GPS-04 | Permiso denegado o revocado | Explica recuperación desde Chrome, no reemplaza origen por coordenadas inventadas. |
| GPS-05 | Geolocalización tarda, expira o no obtiene precisión | Mensaje honesto, botón recuperable, sin spinner permanente. |
| GPS-06 | IDE reverse falla, demora o devuelve dirección incompleta | Conserva coordenadas válidas si procede y muestra fallback explícito, jamás crea calle ficticia como real. |
| GPS-07 | Cambio de origen, nuevo cálculo y destino independiente | Recalcula solo ante cambio efectivo y nunca sustituye destino; red fallida borra distancia/precio obsoletos. |
| GPS-08 | Cambio de permisos/reentrada a pestaña | No dispara GPS sin gesto; no se solicitan permisos en bucle. |

**Separación física:** el diálogo de permisos de Android, precisión real, permisos del sitio en Chrome, uso con pantalla apagada y PWA instalada exigen prueba manual posterior en Samsung con autorización específica. No guardar coordenadas reales en logs/artefactos.

## E. Pruebas visuales adicionales del mismo piloto (solo propuesta)
- `VIS-01`: viewport real `fullPage:false` a 360×780 y 393×852, 3 posiciones de scroll; verificar que nav inferior no tape foco/clic de `Simular conductor`, `Horario de recogida` ni `Tus datos`. Captura `fullPage:true` se conserva como diagnóstico, no como única prueba.
- `VIS-02`: ruta válida, inválida, reconexión; verificar que el mensaje visible sea inequívoco y CTA no resulte engañosamente operable.
- `VIS-03`: mapa plegado y abierto, botones `Editar`, `Historial`, nombre de origen/destino largo, primera parada y dos paradas; medir desbordes.
- `VIS-04`: accesibilidad: 200 % fuente/zoom, foco teclado, estados aria y contraste. Captura con parámetros fijados para reproducibilidad.

## F. Método de ejecución propuesto (no iniciado)
1. Generar scripts QA nuevos en rama aislada desde el **SHA aprobado actual**, sin modificar archivos funcionales.
2. Dos suites independientes (caché con SW **permitido**; permisos con SW **bloqueado**); rutas externas denegadas y recursos locales.
3. Pruebas de fallos intencionales para comprobar que la suite detecta regresiones, luego restaurar fixture temporal.
4. Reportes JSON con estado por caso, capturas por viewport, logs de SW/Cache Storage, matriz de permisos simulados y SHA exacto. Emisión de artefacto GitHub por run.
5. Solo si todo está en verde, proponer correcciones visuales aisladas; jamás integrar/publicar/compilar APK sin consentimiento nuevo.

**Criterio para terminar etapa:** reproducibilidad en 360/393, cero conexiones reales, cero datos sensibles, evidencia de instalación/actualización SW, matriz GPS negativa/positiva y reporte de posibles problemas UI clasificados confirmado / probable / no reproducido.

## G. Estado de esta entrega
**Únicamente informe/propuesta**. No cambios de UI, CSS, HTML, JavaScript, SW, flujos GitHub existentes, Supabase, producción ni APK. No se ha ejecutado la futura ampliación PWA/GPS; el verde de #40263308893 valida únicamente las 59 comprobaciones originales.
