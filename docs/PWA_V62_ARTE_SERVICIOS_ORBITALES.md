# Traslados · PWA v62 · Identidad visual de servicios

**12/10/2026.** Continuidad directa de producción PWA v61. Esta fase cambia únicamente presentación del Inicio y el pequeño disclosure de Mensajería; preserva los contratos Android Cliente Premium v15 WebView, Mapa R25.0 y Conductor R5.

## Diseño implementado
- **Tu viaje**, **Tu reserva** y **Tu paquete**: nombres cortos y memorables. El contexto en cada tarjeta diferencia recogida inmediata, fecha/hora y servicio futuro.
- Tres **emblemas SVG originales** embebidos (ruta, calendario/reloj y paquete); incluyen **anillos concéntricos, texto siguiendo un arco**, reflejos y pincelada de luz. No se usan imágenes externas ni fuentes descargadas, ni se complica el caché offline.
- Portada «Tu viaje, bien llevado.» en petróleo, dorado y crema; tarjetas con capas, viñetas, profundidad y jerarquía tipográfica.
- Transiciones **solo ante interacción** (hover/focus/tap), con `prefers-reduced-motion` que desactiva transformaciones y animaciones. No se simulan cambios de posición del conductor.
- Tamaños sensibles a móviles 320–520 px, cuadrícula flexible `minmax(0,1fr)`, indicadores visuales legibles, objetivos táctiles amplios y `focus-visible` accesible.

## Funcionalidad preservada
- `data-service="now"` reutiliza `setWhen("now")`; `schedule` reutiliza `setWhen("schedule")`. El formulario y las funciones RPC de reserva, GPS, Maps y presupuesto **no se alteran**.
- Mensajería conserva `data-service="delivery"` sin reserva real. El aviso **Próximamente** ahora abre/cierra con `aria-expanded`; jamás llama al backend.
- Mis traslados, chat, historial, avisos, disponibilidad ETA y reservas siguen igual.
- Se mantiene la APK **Cliente Premium v15** firmada porque su WebView consume la misma web; **no** se recompilan Mapa ni Conductor.

## Versiones y verificación
- `styles.css?v=62`, `app.js?v=62` (idéntico código), `service-home.js?v=2`, `sw.js` `traslados-cliente-pwa-v62`.
- `service-art-v62-smoke.cjs` verifica emblemas, texto curvo, accesibilidad, offline, vista separada y ausencia de backend; las pruebas históricas se mantienen y se adecuan a la versión de caché.
- **QA físico Samsung Chrome y WebView pendiente**: legibilidad de curvatura, no overflow horizontal a 360/412 px, respuesta al toque, divulgación del paquete, modo reducido, reserva inmediato/programado y chat intacto.
- Publicar desde PR solo con CI PWA, revisión y paridad Android WebView en verde. Revertir el merge para rollback sin tocar APK ni datos.
