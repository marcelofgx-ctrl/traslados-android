# Traslados · Portada de servicios y reserva enfocada — PWA v61

**12 de octubre de 2026.** Cambios web sobre la revisión contextual PWA v60; versión final del sitio **v61**, compatible con la **APK Cliente Premium v15 WebView** sin modificar su paquete o firma. Mapa Trayectos R25.0 y Conductor R5 permanecen iguales.

## Nueva navegación
- El primer acceso es **Inicio**; ya no muestra el formulario debajo del bloque comercial.
- Hero premium compacto y tres tarjetas: **Viajar ahora** → abre formulario actual y selecciona `now` (con `En 10 min` disponible); **Programar un viaje** → el mismo formulario con `schedule`; **Mensajería y paquetería** → aviso informativo `Próximamente` sin crear viajes, backend ni mensajes.
- El menú fijo se organiza en **Inicio / Reservar / Mis traslados / Mi cuenta**. **Instalar** se conserva como pantalla mediante accesos desde Inicio y Mi cuenta, y se ofrece volver a Inicio.
- El formulario dispone de navegación **← Ver servicios** y título/contexto del modo elegido. Al cambiar de pantalla no se borran dirección, paradas ni datos del usuario.
- Mis traslados, chat por reserva, avisos foreground, seguimiento, presupuestos, historial, GPS, tarifas, agenda y RPC operativas siguen sin cambios.

## Diseño y límites
- Diseño petróleo / dorado, cabeceras grandes únicamente en Inicio y botones táctiles accesibles, sin scroll horizontal por nuevas tarjetas.
- Módulo nuevo `service-home.js` de presentación/navegación (sin API ni GPS); `app.js` conecta la selección con el motor existente `setWhen`.
- CSS y `app.js` v61 y Service Worker `traslados-cliente-pwa-v61` precarga el módulo. Misma URL de Pages: https://marcelofgx-ctrl.github.io/traslados-android/web-pasajero/
- El botón de envíos no hace solicitudes de prueba ni requiere modificar Supabase. **No** se habilita push real con Cliente cerrado, pendiente FCM.

## Verificación
- `tests/service-home-v61-smoke.cjs` prueba los tres servicios, preservación de contratos, cache bust y accesos. Los tests anteriores de v60 y de las fases v45–v58 se actualizan para la versión v61.
- CI PWA, CI de interfaz, paridad Cliente v15 con Android DEBUG. Deben terminar `success` en el SHA fusionado.
- **Prueba manual Samsung todavía necesaria**: inicio en Chrome y APK v15, cambio ahora/programado y regreso, sin pérdida de campos, sin scroll lateral a 360–412 px, instalar accesible, tocar Envíos sin mutación, estados de recogida, chat archivado/activo y notificaciones legítimas.
- **Rollback**: revertir el merge de PWA v61 en GitHub `main` y esperar Pages + service worker; **no** desinstalar APK ni borrar datos.
- No hay una APK de Conductor/Mapa nueva en esta fase, porque sus contratos y fuentes no cambian.
