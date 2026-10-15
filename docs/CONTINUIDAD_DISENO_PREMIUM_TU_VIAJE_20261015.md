# CONTINUIDAD OBLIGATORIA — REDISEÑO PREMIUM DE “TU VIAJE”
**Fecha del corte: 15/10/2026, Uruguay.** Fuente visual: 2 láminas de 8 pantallas compartidas por el usuario en ChatGPT. Es imprescindible adjuntar de nuevo las imágenes en la siguiente sesión; este documento describe los requisitos pero NO sustituye las referencias visuales exactas.

## Pedido definitivo del usuario
«Quiero que la aplicación real quede EXACTAMENTE como en la foto». El usuario rechazó estéticamente la PWA v74: aunque el motor fue mejorado, la presentación final no se parecía al concepto de lujo acordado. NO defender v74 como entrega estética. NO hacer otro boceto genérico ni cambiar el lenguaje gráfico acordado; comenzar en código existente, con comprobaciones visuales por pantalla y capturas comparadas. Ser proactivo y resolver fallos sin convertir al usuario en tester de lo que puede automatizarse. No publicar hasta verificar visual y funcionalmente.

## Referencia maestra
La lámina aprobada tiene 8 pantallas de móvil:
1. **Portada**: cielo de Montevideo al atardecer, coche en primer plano, estilo oscuro petróleo + dorado champagne; emblemas **CIRCULARES** protagonistas y accesos directos independientes: Tu viaje, Tu reserva, Tu paquete (próximamente). Barra inferior Inicio / Mis traslados / Menú.
2. **Tu viaje – recorrido**: pantalla independiente, cabecera con emblema circular y barra de 4 etapas; origen, destino y paradas intermedias, selección automática de direcciones en Uruguay, mini mapa real, CTA dorado Continuar.
3. **Cuándo**: ahora por defecto; opciones rápidas (10/15/20/30 min, 1 h, otras dentro del rango habilitado); al lado icono reloj que abre selector nativo 24 h. Nada de cambiar automáticamente a Tu reserva.
4. **Tus datos**: nombre y celular obligatorios, email opcional, sesión existente completa datos, consentimiento explícito para guardar datos.
5. **Confirmar**: tarjeta elegante de origen/paradas/destino, km y duración de ruta verificados, ETA recogida únicamente cuando disponible, solicitud clara. El presupuesto NO es pago anticipado inventado; distinguir importe orientativo de propuesta del conductor.
6. **Buscando conductor**: mapa y animación premium tipo radar, fases con mensajes honestos desde servidor; no inventar conductor confirmado mientras está pendiente.
7. **Conductor en camino**: mapa protagonista, ubicación/ETA solo de datos reales, identificación vehículo BYD Yuan Pro y matrícula actual desde Supabase, comunicación llamar/WhatsApp/chat/ruta según capacidades reales. NO introducir fotos, puntuación ni ubicación ficticias.
8. **En viaje**: timeline clara y estados validados; acceso a historial, compartir viaje si está implementado, llegada/finalización marcadas por Conductor. Nunca ofrecer al pasajero un botón que finaliza unilateralmente el viaje si no existe tal permiso.

## Experiencias y navegación
Portada con tres servicios INDEPENDIENTES, cada icono circular abre su propia pantalla. Tu viaje = viaje inmediato o a corto plazo, no es reserva programada, no comparte su interfaz con Tu reserva. Tu reserva = agenda/traslados programados con su propio proceso. Tu paquete = Próximamente, sin fingir servicio disponible. No mostrar opciones ajenas dentro de Tu viaje. En Tu viaje, las etapas toman el foco y muestran resúmenes compactos de etapas anteriores sin borrar datos. Flujo con volver/editar coherente, sin pantallas interminables ni superposición de textos.

## Mantener funcionalidad existente
- Motor de direcciones con Uruguay/departamentos, GPS actual, puntos de interés, paradas múltiples reordenables y eliminables.
- Ruta real, km, duración, ETA de conductor y presencia distinguidas; no inventar cifras ni reemplazar silenciosamente fallas por «disponible».
- Formulario con nombre y celular o sesión/PIN, privacidad y estado persistente; selección de pasajeros, comentarios y pasos/validaciones.
- RPC Supabase, solicitud inmediata idempotente sin duplicados, presupuestos/aceptaciones, chat, actualización de estados, Mis traslados e historial; compatibilidad WebView Cliente.
- Conductor y Mapa Trayectos existentes: overlay/globito, navegación rumbo-arriba, estados de recogida, notificaciones/sonidos y telemetría; no rehacer proyectos.
- ÚNICO conductor: no copiar catálogo de múltiples autos de Uber.
- Matrícula editable desde configuración Conductor y compartida centralmente, inicial **APP-0061**; mostrarla solo al pasajero autorizado en traslado activo. Revalidar permisos.

## Estado real verificado el 15/10/2026
- Repo principal: https://github.com/marcelofgx-ctrl/traslados-android (main).
- Repo backend/migraciones: https://github.com/marcelofgx-ctrl/traslados-web.
- Web oficial: https://marcelofgx-ctrl.github.io/traslados-android/web-pasajero/ — **PWA v74** publicada, GitHub Pages Actions #38733669988 SUCCESS (15/10, 09:22 Uruguay).
- APK **Conductor 11.5-R8 / code130**, GitHub release workflow #38733331388 SUCCESS; archivo `web-pasajero/downloads/traslados-conductor-r8.apk`.
- APK **Mapa Trayectos 0.1-R25.25 / code77**, release #38733331329 SUCCESS; archivo `web-pasajero/downloads/mapa-trayectos-r25.25.apk`.
- APK **Cliente Premium v16** sigue siendo WebView de la PWA (sin necesidad de recompilar por cambio web), archivo `web-pasajero/downloads/traslados-cliente-premium-v16.apk`.
- PWA QA #38733331335 SUCCESS; pruebas CI y compilación Android realizadas. Faltan **QA visual equivalente a la referencia** y prueba Samsung en movimiento.
- PR frontend #98 fusionado commit `a3e0b7132815e5c2b6c71885bf0ed186c48037a8`. PR backend #17 fusionado commit `9d9b9d56b3a860797f9f3984d6847e7b068f8db9`.
- Supabase proyecto `zetaudvvutlouiqxopvg`: migraciones `20261015_quick_pickup_and_vehicle_plate.sql` y `20261015_restrict_vehicle_plate_to_active_passenger.sql` aplicadas; matrícula APP-0061 almacenada; API de lectura restringida a reserva autorizada en fase activa. Recogidas rápidas 1–120 min; programadas siguen separadas.
- Documentación histórica: `docs/REANUDAR_TRASLADOS.md`, `docs/CONTEXTO_MAESTRO_MAPA_TRAYECTOS.md`, `docs/CONFIGURACION_REMOTA_TRASLADOS_ARQUITECTURA.md`. Algunos títulos del contexto antiguo todavía dicen v73/R7; confiar primero en código y Actions actuales.
- **Importante**: la imagen estética de 8 pantallas que generó ChatGPT es SOLO un mockup, NO aplicación implementada. La v74 real publicada no cumplió la fidelidad estética; ese es el trabajo pendiente.

## Plan de ejecución acordado
1. Obtener y fijar las dos láminas visuales como referencia de QA (el usuario puede volver a adjuntarlas). Auditar HTML/CSS/JS existentes y dependencias; crear rama desde main, no reconstruir desde cero.
2. Reproducir **pantalla 1** (portada) y **pantallas 2–5** como wizard premium con diseño preciso: proporciones, fondos, emblemas, iconos, tipografía, bordes, dorado, botones, jerarquía, scroll. Pruebas reales a anchura Samsung.
3. Conservar/conectar contratos reales al recorrido, cuándo, tus datos y resumen; ejecutar regresión de inmediatos y programados. No hacer transiciones simuladas que envían solicitudes reales.
4. Implementar visualmente estados 6–8 a partir de backend real; no falsear GPS, conductor, coste, pago ni finalización.
5. Comparar capturas de cada etapa en emulador con imagen maestra, corregir sin esperar instrucciones del usuario, pruebas de desbordamiento y flujos completos. Publicar mediante GitHub Actions solo con todo verde. Comunicar claramente qué verificó CI/emulador y qué debe verificar Samsung físico.
6. Al terminar: enlaces oficiales de Web, Cliente, Conductor y Mapa, versiones y hora de compilación/despliegue; dejar actualizado este documento y `REANUDAR_TRASLADOS.md`.

## Comando para nueva sesión
**«Retomar Traslados desde GitHub. Leé docs/CONTINUIDAD_DISENO_PREMIUM_TU_VIAJE_20261015.md. Quiero implementar en la PWA real, pantalla por pantalla, EXACTAMENTE el diseño de las imágenes que adjunto. No quiero otra imagen ni un diseño genérico. Conservar todo el motor, aplicar QA visual con capturas comparativas y solo después publicar. Informame versión y fecha/hora últimas compilaciones antes de empezar.»**

---

## AVANCE REAL DE LA NUEVA SESIÓN — RAMA DE DESARROLLO, NO PUBLICADO

- **Branch:** `feature/tu-viaje-premium-visual-20261015`
- **PR:** https://github.com/marcelofgx-ctrl/traslados-android/pull/99 — abierto en modo **draft**, deliberadamente NO fusionado en `main`.
- **Último cambio visual/construcción:** verificar SHA del HEAD de la rama y los últimos Actions; el último observado después del commit de iconografía `8d7d1660` pasó cuatro workflows, y luego se incorporó una pequeña mejora del diagrama vial (`6b369809`), pendiente de contrastar con sus nuevos workflows.
- **Web pública:** sigue en PWA v74, sin despliegue premium ni cambio de caché/versionado. No llamar v75 a algo publicado.

### Implementado en código de la rama
1. Portada con hero costero local (por ahora ilustración vectorial, no fotografía de calidad equivalente a la lámina), emblema de auto circular principal, otros dos accesos circulares (reserva y paquete).
2. Flujo de `Tu viaje` con 4 estados: recorrido, cuándo, tus datos y confirmar, progresión accesible; validación al avanzar; regreso y edición. La agenda programada conserva su UI/motor.
3. Opciones de tiempo «Ahora», 10/15/20/30/45/60 minutos y hora manual de hasta 120 minutos reutilizando `setWhen()` y su lógica del servidor; el usuario no es trasladado a una reserva programada.
4. `premium-journey.js`, `premium-journey.css` y `premium-skyline.svg` separan la presentación nueva de los contratos, autenticación, ETA, validaciones, RPC y agenda existentes.
5. En Mis traslados, para `meta.tone === moving`, timeline de 3 fases basada solo en `status`/`pickup_phase` del servidor, matrícula autorizada si existe, enlace de llamada al número ya oficial y botón para abrir detalles/chat. No hay ETA, GPS, foto, puntuación ni finalización inventados.
6. Detalles de ruta abiertos automáticamente en Tu viaje para mostrar esquema vial real cuando el motor devuelve geometría verificada; el mapa cartográfico real equivalente a la lámina sigue pendiente.

### Validaciones efectuadas
- Workflow `Verify passenger PWA review screen`, `Validate Cliente Premium v15 Android WebView parity`, `Verify and package passenger PWA` y `QA premium visual (draft only)` pasaron en commit `8d7d1660`.
- QA Chromium probó anchos 360/393/412 px; 5 capturas por ancho; navegación simulada (NO solicitud real, NO Supabase), estado de error sin origen/destino, selector 10 min, pasos y scroll horizontal.
- Capturas del workflow `QA premium visual (draft only)`: https://github.com/marcelofgx-ctrl/traslados-android/actions/workflows/qa-premium-visual.yml (abrir ejecución asociada al HEAD más reciente, descargar artifact `premium-mobile-qa-capturas`).
- **IMPORTANTE:** que existan capturas en CI no significa que hayan sido revisadas a ojo contra el mockup; esta comparación final sigue pendiente. Tampoco hubo recorrido real en Samsung ni pruebas de backend de extremo a extremo.

### Pendientes bloqueantes antes de publicar
1. Comparación visual humana pantalla por pantalla contra el ZIP original del usuario, incluyendo el hero fotográfico, proporciones de tarjetas/emblemas, campos, mapa y botones; no dar por aprobada la ilustración costera provisional.
2. Finalizar diseño premium fiel de seguimiento 6, 7 y 8 con MAPA real, radar honesto, ETA de datos reales y estado final solo desde conductor (la timeline actual es paso intermedio, no entrega visual).
3. Integrar email opcional y consentimiento de guardado sin inventar parámetros de RPC: los contratos actuales de recogida no aceptan email. Requiere planificación de esquema/backend/privacidad.
4. Probar flujo real de cliente autorizado y visitante con backend de test, cálculo de ruta, recogidas, agenda, PIN, presupuestos, chat, idempotencia, matrícula y permisos; sin pedidos accidentales a producción.
5. Resolver fallos gráficos detectados por captura, actualizar versión de forma coordinada (HTML/JS/SW/tests) y recién entonces fusionar y desplegar. Dejar hora exacta de compilaciones y enlaces activos.

### Comando de reanudación actualizado
«Retomar Traslados desde GitHub; leer `docs/CONTINUIDAD_DISENO_PREMIUM_TU_VIAJE_20261015.md` en `feature/tu-viaje-premium-visual-20261015`, abrir PR #99 y los resultados de QA visual. Continuar el rediseño fiel de las 8 pantallas desde código existente, sin publicar hasta comparar capturas con las dos imágenes adjuntas. No perder contratos, backend, APP-0061 ni flujo separado Tu viaje / Tu reserva.»

---

## AVANCE 15/10/2026, 11:00 URUGUAY — PR #99 SIN PUBLICAR

Se trabajó exclusivamente sobre la rama feature/tu-viaje-premium-visual-20261015.

### Cambios comprobados en código
- Reloj original de 24 horas visible junto a las opciones rápidas, conservando sus eventos y validaciones.
- Mejoras de composición de portada (círculos y paleta petróleo/champagne), navegación compacta y botones dorados. Se agregó fotografía de Montevideo por Unsplash como mejora progresiva con SVG local de respaldo. En CI la foto remota NO llegó a mostrarse: todavía falta incorporar imagen de calidad local verificable.
- Disponibilidad/pasajeros permanece accesible en panel plegable en «Cuándo», sin eliminar lógica de validación y recogida.
- Confirmación: eliminado el icono gigantesco heredado; se muestran origen, destino y paradas SOLO cuando el pasajero realmente los eligió; no se inventan precios/km.
- Solicitudes inmediatas pendientes: radar animado decorativo y mensaje veraz de espera de respuesta; NUNCA GPS, ubicación o conductor ficticios. Viajes en fase activa: timeline vinculada al status/pickup_phase real, matrícula autorizada, llamada, WhatsApp y chat.
- QA mejorado: capturas estables sin capturar transiciones; 360/393/412 px, cinco vistas por ancho, comprobación de reloj visible, cantidad de pasajeros accesible e icono acotado.

### Evidencia
- Commit 0123411 (antes de corrección menor de texto): pasaron cuatro workflows, incluido el visual: https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38746638755
- Artifact de QA: premium-mobile-qa-capturas (15 capturas: 5 etapas × 3 anchos). No son las 8 escenas completas ni una prueba real de Supabase.
- Se revisaron capturas humanas frente a ambas láminas: mejoró la jerarquía, pero todavía NO cumple fidelidad visual exacta. Falta fotografía/coche principal, mapa cartográfico y experiencias 6–8 de seguimiento.
- Aún no se hizo prueba física Samsung ni un E2E de producción o test completo. El PR debe seguir en draft.

### Bloqueantes antes de publicar
1. Hero fotográfico local de calidad comparable a la referencia y garantías offline/licencias.
2. Mapa real en recorrido, conductor en camino y viaje, solo con geometría o ubicación legítima.
3. Escenas 6–8 con una UI tan cuidada como la lámina, sin datos falsos ni botón de finalización desde pasajero.
4. Contrato backend para email opcional, privacidad y consentimiento si se guarda información.
5. Regresión de inmediata vs programada, identidad/PIN, presupuesto, cancelación, chat, matrícula APP-0061 y WebView.
6. Revisión final a 360/393/412 px y Samsung físico; recién después bump, SW, merge y publicación.

### Versiones públicas vigentes (sin cambios)
PWA v74; Cliente Premium v16; Conductor 11.5-R8 / code130; Mapa R25.25 / code77.

---

## ETAPA DE FIDELIDAD FOTOGRÁFICA + MAPA REAL (15/10/2026, PR #99)

Desarrollo **solo en rama draft, sin fusionar a main**.

- Portada: se creó un archivo WEBP local de la rambla al atardecer (foto de Pedro Fleitas, Unsplash, publicada el 28/08/2023, licencia Unsplash, disponible desde images.unsplash.com/photo-1693192357825-b50aa54738ab). Archivo `web-pasajero/premium-montevideo.webp` (29.474 bytes). El diseño ya no requiere imagen de fondo desde CDN y el SW prevé precargarla al futuro release.
- QA humano rechazó un intento experimental de vehículo vectorial: se **retiró** del código y del SW. El mockup aprobado muestra un coche fotográfico, no un dibujo; no declarar resuelta esta diferencia. Mantener el hero con foto de Montevideo mientras se encuentra un activo fotográfico adecuado de vehículo, no mostrar otro boceto.
- En Recorrido: se añadió `premium-map.js` para cartografía OpenStreetMap con mosaicos limitados (máximo 12 por escena), atribución y geometría Mercator. La **polilínea únicamente proviene de `roadEngine.verified(data)`**; no hay líneas ficticias, ni ETA falsa. Si falta geometría o mosaicos, se conserva esquema vial/indicadores/reintento.
- Durante un viaje: si **la respuesta de reserva autorizada** contiene lat/lng de origen y destino válidos y el motor verifica ruta, puede mostrarse mapa del *recorrido contratado*, claramente etiquetado como **NO ubicación del conductor**. Ninguna foto, rating, coche sobre mapa, ETA ni GPS inventados. Timeline y botones de llamar/WhatsApp/chat usan contratos existentes.
- Se reforzó `qa-premium-visual.yml` con `premium-map-smoke.cjs` (proyección, entradas inválidas, encuadre y límites) y visual móvil Chromium (360/393/412 px), incluido archivo fotográfico local y montaje de cartografía con fixture sintético, **sin enviar solicitudes de transporte**.
- Se eliminó de la rama el workflow temporal con permiso de escritura usado únicamente para incorporar la fotografía local.

**QA:** los tests de QA premium, revisión PWA y paridad de WebView pasaron en el último commit de código `e8e7f8c`. El empaquetado de esta ejecución estaba en progreso al último control; consultar HEAD de Actions antes de afirmar verde total. No hubo prueba Samsung físico ni verificación de un viaje real. Los mapas externos pueden no estar disponibles sin conexión y no son seguimiento del coche en vivo.

**La portada sigue SIN FIDELIDAD EXACTA:** el fondo fotográfico se acercó al lenguaje visual pero no existe aún el automóvil fotográfico aprobado ni composición idéntica. Seguimiento 6–8 no tiene streaming autorizado de posición del conductor; solo estado real y mapa de ruta si las coordenadas están disponibles. Email opcional y consentimiento de guardado requieren contrato claro antes de persistencia.

**Bloqueantes absolutos para merge:** fotografía+composición aprobadas, cartografía confirmada con origen/destino reales, flujos 6–8 finales con backend autorizado, privacidad/email, E2E seguros de inmediata vs programada, chat, matrícula APP-0061, presupuesto, pruebas Samsung y QA de 8 escenas. Publicar solo tras verificación integral. La web pública continúa PWA v74.


---

## QA DE OCHO ESCENAS — 15/10/2026, 12:18 UY — PR #99, TODAVÍA DRAFT

**Revisión de estado al retomar:** HEAD código de esta iteración `6a1faed41832c433da8fa0f400b08ecc72113bea`. No fusionar ni publicar aún. Branch: `feature/tu-viaje-premium-visual-20261015`.

**Correcciones reales guardadas en el PR:**
- Se reparó la política `Content-Security-Policy`: la directiva `img-src` ahora habilita **solo** `https://tile.openstreetmap.org` para los mosaicos cartográficos del módulo premium. Antes el navegador bloqueaba las teselas aunque las rutas se renderizaban.
- En `Tu viaje / Recorrido`, el buscador ahora ocupa un ancho útil completo con un botón de lupa accesible; el GPS queda en segunda línea. La estructura de `Tu reserva` no se modificó.
- Se ampliaron los tests móviles Chromium a **8 escenas × 3 anchos**: las cinco pantallas de la solicitud y tres de seguimiento (Buscando, En camino, En viaje). Las últimas tres son **fixtures sintéticos explícitamente rotulados SOLO QA**, no reservas reales ni promesas de posición de vehículo/ETA.
- Se agregaron aserciones para permiso CSP a OSM, ancho mínimo del buscador (165px a 360/393/412) y visibilidad del GPS. La QA nunca envía reservas reales ni escribe a Supabase.

**Resultado de GitHub Actions:** los cuatro workflows sobre `6a1faed` finalizaron **SUCCESS**: PWA review, WebView parity, Verify and package passenger PWA, y QA premium visual. QA específica: https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38758791414 ; artifact `premium-mobile-qa-capturas` (24 PNG, tres resoluciones, cinco escenas iniciales + tres de seguimiento simulado).

**Observación visual comparada con las referencias adjuntas:** mejoras verificables en escritura de direcciones, mapa técnicamente permitido y estados veraces. **Aún no reproduce exactamente el mockup de ocho pantallas**: portada no tiene coche fotográfico en primer plano, mapa de recorrido depende de geometría real y tiles, seguimiento de conductor no recibe GPS en vivo/ETA autorizada, «Tus datos» no tiene email persistido ni consentimiento backend. No falsificar estos elementos para que una captura parezca bonita.

**Siguiente trabajo imprescindible:** seleccionar/generar un asset de vehículo de calidad equivalente a la referencia con autorización; terminar enfoque full-screen de seguimiento; conectar email y consentimiento tras fijar contrato de backend; E2E en entorno seguro + Samsung físico. Revalidar streaming de posición/ETA autorizado o presentar solo estados existentes y mapa de ruta, sin inventar coche en movimiento.

**Versión pública deliberadamente intacta:** PWA v74, Cliente v16, Conductor 11.5-R8 y Mapa R25.25. No se actualizó service worker ni versión publicada.

---

## AVANCE PRODUCTO — 15/10/2026, 12:50 UY

Se continuó el desarrollo en PR #99 **solo en rama draft**, sin publicar ni fusionar. No confundir CI con producto final.

### Cambios de código
- Pantalla «Tus datos» en Tu viaje incorpora email opcional validado y un consentimiento explícito para recordar nombre, teléfono y correo **solo en este dispositivo**, con borrado al desactivar. El email NO se envía al conductor/Supabase porque la RPC vigente no lo admite. La UI de Tu reserva permanece independiente.
- Pantalla específica `view-seguimiento` de viaje activo, accesible desde cada traslado autorizado en fase de movimiento; conserva estado real, origen/destino, datos del vehículo con matrícula solo si está autorizada, botones telefónico/WhatsApp/chat, timeline basada en `status` y `pickup_phase`. No hay posición falsa, ETA falsa ni botón para finalizar el viaje desde el cliente.
- Mapa de seguimiento usa geometría vial verificada cuando la reserva trae coordenadas; sin ellas se muestra mensaje de ausencia honesto. El módulo cartográfico acepta altura compacta para Android sin distorsionar el encuadre.
- La barra fija de navegación ya no oculta los botones de contacto; se usan acciones en un dock independiente y espaciado de scroll.
- Portada: incorporado asset local `premium-car-crop.webp` derivado de la referencia aprobada (Git blob SHA `b90ffd42ea1f4fad1d3e230266970b3997f16b5e`), junto con fondo local de Montevideo y precache SW; tras captura QA se ajustó el tamaño para no tapar el cielo. La fidelidad visual sigue siendo evaluada y NO está aprobada.
- QA visual: ahora revisa email, consentimiento/borrado local, pantalla completa en estados EN_CAMINO/EN_VIAJE, ausencia de finalizar del pasajero, acciones visibles por encima de navegación en 360/393/412 px, y recursos fotográficos offline. Chrome QA se cierra correctamente ante aserciones fallidas (no se queda bloqueado hasta el timeout).

### Estado
Los cuatro workflows habían pasado en el commit `c26baf9` y capturas de la pantalla de seguimiento corregida fueron revisadas (sin choque con barra inferior). Consultar Actions sobre HEAD más reciente `207c923` antes de declarar un estado final.
No se enviaron viajes ni se escribieron datos en la DB durante QA. PWA pública continúa en v74. Sin despliegue ni entrega final.

### Bloqueantes reales para lanzamiento
- QA visual final de portada completa contra referencia (fragmento fotográfico integrado es provisional); otras pantallas deben alcanzar el mismo nivel.
- Contrato backend para email opcional de reserva si se quiere almacenar/enviar: consentimiento claro, validaciones y migración auditada. Persistencia actual únicamente local bajo consentimiento.
- Seguimiento **en vivo** de vehículo / ETA con permisos de lectura por reserva: no inventar ni reutilizar GPS abierto para todos.
- Pruebas de extremo a extremo contra entorno seguro de Supabase, rutas, horarios, presupuesto, cancelación, chat, identidad y matrícula APP-0061, sin generar viajes accidentales en producción.
- Prueba física Samsung y paridad APK Cliente con WebView. Solo después: versionado coordinado, service worker, merge y publicación.

Criterio: la aplicación final no es un informe ni un mockup; terminar funcionalidades, QA visual humana y pruebas reales antes de decir «lista».

---
## PRIMERA APK PR99 AISLADA — 15/10/2026, 13:23:04 UYT

- Entrega real: `preview-artifacts/traslados-cliente-premium-pr99-v17-prueba.apk`, 898.216 bytes. Build firmado SUCCESS: https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38768273373 .
- Paquete conservado `uy.com.traslados.cliente`, versionName `17.0-PR99-Prueba`, versionCode **205**. Certificado SHA256 idéntico a v16: `d91f4b9a37f4c77653fdf18fe792e7011a046dd2c09fded1e13fd29d4267269d`. Verificados firma v2, manifiesto y checksum del APK efectivamente descargado.
- El APK incluye los **29 recursos web** del PR (fotografías, CSS, JS y catálogo local Uruguay de 2.209.865 bytes / 22.503 lugares). Los hashes de cada recurso se contrastaron contra su contenido dentro del APK.
- Es una compilación **para revisión aislada**. Sirve los recursos empaquetados mediante WebView en `https://appassets.androidplatform.net/traslados-android/web-pasajero/`, origen separado del almacenamiento de producción. CSP restringe conexiones a self; el cliente nativo bloquea Supabase, Worker, web pública y contactos externos; solo admite imágenes públicas OSM. Registro de service worker eliminado del paquete. No envía reservas ni usa sesiones de la web real.
- Limitaciones deliberadas de esta prueba: no autenticación real, geocodificación remota, cálculo remoto de ruta/ETA, chat ni llamadas/WhatsApp. No confundir revisión instalable del PR con producto operativo completo. No se añadieron fixtures de viajes a esta APK.
- No se fusionó ni publicó main ni se modificó el catálogo de descargas productivo. La web pública sigue v74 / Cliente público v16.
- La próxima APK operativa deberá tener **versionCode >205** para poder actualizar encima de esta prueba sin perder datos. No intentar volver a v16/code204 mediante downgrade.
- Revisión posterior a la primera APK: se corrigió omisión del catálogo JSON y el manejo nativo de la URL raíz sin slash; pipeline de publicación en rama tolera cambios concurrentes sin force push.
- Frontend: pasaron **46 scripts smoke** locales, incluyendo búsqueda por departamento/POI, rutas, ETA, navegación, cuenta, chat y privacidad de presentación. No equivalen a E2E con backend.
- QA nativa nueva: `cliente-premium/preview-tests/PremiumPreviewTest.java` + workflow `qa-cliente-pr99-android.yml` en emulador Android 35. Primera ejecución pasó aislamiento nativo y falló navegación por pulsar antes del montaje; la espera se corrigió para exigir readyState complete y control dinámico montado. **Consultar resultado de ejecución 38768895653 antes de afirmar PASS**.
- Referencias originales recuperadas del ZIP y comparadas con capturas reales de HEAD b62da246 (8 escenas × 360/393/412). La portada y el seguimiento aún difieren considerablemente de la referencia. Seguimiento GPS/ETA autorizado por reserva y E2E seguro siguen pendientes. Supabase no tiene ramas de desarrollo al consultar; no se escribieron reservas de prueba en producción.

### Actualización final de la APK de prueba: reloj corregido
La APK vigente se recompiló desde `8d158913832416fd742422352e0178533e24ade5` el **15/10/2026 13:34:58 UYT**: 898.244 bytes; SHA256 `a4f7d632359bdecc13c9d652d6087630cebe466f87e38a0b2ef459cd3f5328bd`. Mismo paquete, code205 y certificado original. Archivo descargado y contenido interno contrastados otra vez; copia entregable actualizada.

Correcciones reales adicionales en `app.js`: formato de hora manual 24h (la expresión anterior rechazaba dígitos), variable `input` definida en el listener correcto y recogida dentro de dos horas cuando cruza medianoche. Nueva regresión `premium-clock-24h-smoke.cjs`: horas válidas/incorrectas, cierre del selector, límite de dos horas y medianoche, sin solicitudes reales. **47 smoke locales PASS**. QA visual móvil **SUCCESS** sobre ese código: https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38770079973 ; prueba de hora manual añadida a los 3 anchos.

**Android 35 real en emulador:** las dos pruebas de carga/navegación y bloqueo de producción finalizaron correctamente (Gradle BUILD SUCCESSFUL) en ejecución https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38770274128 a las 13:38:33 UYT. El workflow completo FALLÓ después por el volcado XML auxiliar de accesibilidad, no por esas aserciones. Se corrigió su script para conservar resultado real, reinstalar después de limpieza AGP, esperar arranque y capturar PNG/logcat aunque XML no esté disponible. Consultar ejecución de HEAD tras esta corrección antes de declarar verde del workflow completo. No hubo prueba física Samsung; no confundir este hito con producto operativo terminado.


## Revisión v18 — recorrido compacto (15/10/2026 14:14:15 UYT)

- APK `18.0-PR99-Prueba`, code206, fuente `e8947b148225d4392b4631713a20d3b5d07e7bae`. Archivo real 903.079 bytes, SHA256 `b39a660d22a52c985a9daa0fb357da972e127f79acaa34610b04feb54e1145af`, firma v2 y certificado original verificados. La próxima versión operativa requiere code >206.
- Recorrido inmediato: tarjeta conjunta compacta, departamentos progresivos al editar, GPS legible, paradas desplegables y mapa geográfico visible. Reutiliza los controles reales; Tu reserva conserva sus controles. El mapa inicial no representa GPS ni ruta calculada; solo dibuja recorrido cuando existe geometría verificada.
- Compilación firmada SUCCESS: https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38775384634 ; QA Android35 SUCCESS: https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38775384621 ; escenas móviles 360/393/412 SUCCESS: https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38775390439 . 47 smoke locales PASS. Selección real de POIs locales Carrasco/Punta Carretas sin crear reserva. Escenas de seguimiento del harness siguen siendo fixtures de QA, ausentes en APK.
- Continúa aislamiento de backend/autenticación/rutas remotas/contactos; no reservas reales. No despliegue main ni web pública. No prueba física Samsung. El producto completo y la fidelidad de las ocho pantallas siguen pendientes; esta entrega mejora Recorrido.
- Descarga independiente: https://raw.githubusercontent.com/marcelofgx-ctrl/traslados-android/feature/tu-viaje-premium-visual-20261015/preview-artifacts/traslados-cliente-premium-pr99-v18-prueba.apk


## UX v19: disponibilidad visible y errores seguros (15/10/2026)

- El estado real de recogida se mueve fuera del panel plegable y permanece visible durante los cuatro pasos de Tu viaje. Al volver a Tu reserva se restituye su nodo original. Disponible, sin disponibilidad inmediata y consulta no verificada son estados diferentes; un resultado público negativo no se presenta como una causa concreta.
- Errores remotos RPC marcados y traducidos a mensajes de producto, conservando validación local y mensajes de horario. El fallo ambiguo de envío indica revisar Mis traslados; elimina explicaciones de UUID/servidor y toast técnico duplicado. La idempotencia y los reintentos manuales conservan su lógica.
- La prueba v19 permite SOLO GET/OPTIONS sin parámetros hacia public_driver_availability_v1, función STABLE auditada y contrato HTTP GET comprobado. No recibe coordenadas ni identidad del conductor. Envíos desactivados tanto en interfaz como funciones y política nativa; autenticación, reservas, rutas/ETA remotas y contactos siguen bloqueados. La disponibilidad pública sí es real; ETA no se simula.
- Firma original, paquete original, code207 y hashes de recursos contrastados contra APK descargada: 904.403 bytes; SHA256 7fb219a43fc64e10db53f50282e210b1f67d5811fb27a8ff8d23f1182fdf2308; fuente e99097a8a5c17fa3aab3fe768832157b3cd9f47d. Compilación 15/10/2026 16:02:18 UYT: https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38790359376 . Próxima APK operativa requiere code >207.
- QA: 47 smoke locales PASS; errores internos sintéticos ocultos y validación accionable conservada; escenas 360/393/412 PASS https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38790365901 . Se corrigió una regresión detectada: estilos de recorrido agrandaban Atrás y tapaban Continuar en Cuándo a 360px. No se quitaron aserciones.
- Android35 PASS incluyendo consulta pública real a través de GET/CORS dentro de WebView y bloqueo de escritura: https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38790155607 (misma lógica nativa/HTTP; antes del último ajuste CSS). Ejecución del APK con CSS final: https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38790359298 ; consultar resultado final. No prueba física Samsung ni reserva real. Main/web pública sin despliegue; producto visual completo sigue pendiente.
- APK: https://raw.githubusercontent.com/marcelofgx-ctrl/traslados-android/feature/tu-viaje-premium-visual-20261015/preview-artifacts/traslados-cliente-premium-pr99-v19-prueba.apk
