# Quick Trip Web/PWA v87 — QA Samsung visual (25-10-2026)

## Alcance autorizado
Rama aislada `qa/quicktrip-webpwa-v87-samsung-visual-20261025`, base exacta `7b98f89a7d156d61ea6926959d7d4e448ea36fcc` (v86 staging QA). Sin merge, despliegue nuevo ni APK. Ninguna operación contra Supabase real. Capturas recibidas de Samsung del usuario únicamente como evidencia visual (no incluirlas en el repositorio).

## Incidencias observadas en cinco fotos reales
- Tu Viaje se alargaba demasiado: CTA/valor quedaban por debajo del mapa y controles secundarios.
- Mosaicos de OpenStreetMap sólo cargaban una parte, con un rectángulo vacío; no existía ruta verificada.
- Precio, distancia, duración, conductor y ETA no disponibles en modo CSP sin backend.
- Barra inferior fija y burbuja superpuesta en Android; reservar espacio derecho (la burbuja podría ser del sistema, no del sitio).
- Botón Historial invadía el nombre largo del destino. Agregada prueba de intersección geométrica.

## Solución v87 (presentación, no motores)
- `quicktrip-qa-samsung-v87.css`: compactación de encabezado, tarjetas, mapa no verificado, orden visual de CTA antes de accordions, safe-area y navegación.
- `quicktrip-qa-samsung-v87.js`: activación exclusivamente bajo meta QA más `connect-src 'none'`, aviso legible, estados no verificables sin inventar datos, uso de UI nativa y CTA desactivado para evitar solicitudes.
- `index.html` incorpora sólo los dos recursos v87 manteniendo los scripts de motores existentes, CSP sin conexiones y formulario deshabilitado por el estado QA.
- `sw.js` cambia caché QA a v87 e incluye los recursos nuevos; `manifest.webmanifest` muestra v87.
- Ningún archivo de motor estable de rutas, geocodificación, ETA, GPS, reservas, mapas ni Supabase modificado.

## Limitaciones intencionales
No se mostrará polilínea, vehículo, kilómetros, tiempo de conductor ni precio sin datos verificables. La ruta se habilitará sólo cuando un motor existente aporte datos realmente verificados; NO crear geometría falsa. La prueba no valida la disponibilidad real de conductores ni reservas. Las pruebas Playwright emulan tamaños de Samsung: sólo las fotos del usuario son de un equipo físico.

## Pruebas y capturas
Workflow `.github/workflows/qa-quicktrip-v87-samsung.yml` sobre rama QA (nunca despliega). Verifica el diff contra base, 11 blobs de motores/visual previa invariantes, CSP, tres servicios, botón único, ZIP estático sin APK, Service Worker v87 y Chromium con red externa denegada. Genera capturas 393 y 360 px de inicio, Tu Viaje, viewport y dos direcciones **ficticias QA**. Afirmar aprobación sólo tras comprobar el estado más reciente de Actions y revisar capturas reales del runner.

## Protección de Vercel
El proyecto `quicktrip-v86-qa-samsung-20261025` tiene los despliegues Preview automáticos desactivados durante este trabajo. Los cinco Previews iniciados automáticamente durante los primeros pushes v87 fueron cancelados. La v86 antigua continúa como Preview independiente. Prohibido publicar candidata v87 sin prueba completa y nueva autorización.

## Próximo paso
Revisar el artefacto final de capturas, comprobar que Historial no se cruza con destino largo, y someter el aspecto a aprobación visual antes de autorizar un nuevo Preview QA único. No fusionar con `main`.
