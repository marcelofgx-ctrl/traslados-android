# PWA v43 — Cierre premium del formulario de Traslados (11/10/2026)

## Alcance y criterio
Revisión solicitada a partir de captura real del Samsung en PWA v42: la flecha de los desplegables no armonizaba con la estética petróleo/dorado, el cierre de reserva parecía un botón aislado y WhatsApp competía conceptualmente con la confirmación. **Se preservan paleta, textura, estructura plegable de tres pasos y API de reservas.**

## Flujo implementado
1. **Paso 01: Recorrido** — buscador, paradas y motor ROAD originales sin cambios.
2. **Paso 02: ¿Cuándo viajás?** — fecha/hora, pasajeros y las opciones urgentes originales.
3. **Paso 03: Datos del pasajero** — nombre/teléfono e indicaciones; texto de contexto más discreto.
4. **Cierre:** tarjeta compacta «Tu viaje, a un paso», CTA «REVISAR Y CONFIRMAR», aclaración expresa de que no hay cobro ni aceptación automática.
5. **Vista previa:** «Confirmá tu solicitud», resumen de ruta/horario/pasajeros/tarifa orientativa y detalles expandibles. CTA «CONFIRMAR SOLICITUD DE RESERVA». La RPC original crea una **solicitud pendiente**, no un viaje aceptado.
6. **Modo Ahora / En 10 min:** sigue siendo consulta por WhatsApp y ETA; el cierre de reserva programada se oculta para evitar enviar una reserva urgente falsa.
7. **Edición después de revisar:** se compara la carga útil actual con la presentada en la revisión antes de llamar al backend; si cambió o quedó inválida, se exige revisar de nuevo. La carga útil registrada/invitado, cuotas, RLS, agenda y origen GPS no se modifican.
8. **Botón durante envío:** el indicador «Enviando solicitud…» deshabilita interacciones pero conserva los elementos visuales (flecha y spans) al restaurar su estado.

## Diseño
- Chevron del acordeón dibujado con CSS, no glifo tipográfico dependiente del sistema.
- Selects con `appearance:none` y vector dorado; conserva control nativo de selección, teclado y accesibilidad.
- Campos y microcopys afinados en móvil, sin rediseñar la identidad.
- CTA dorado más legible, con flecha en círculo sutil, explicación de estados debajo.
- PWA `app.js?v=43`, `styles.css?v=43`, SW `traslados-cliente-pwa-v43`.

## Compatibilidad
- Web publicada: `https://marcelofgx-ctrl.github.io/traslados-android/web-pasajero/`
- Cliente Premium Android v15: **misma PWA por WebView**; no necesita firmar una APK nueva para cambios HTML/CSS/JS.
- Mapa Trayectos R24.8: no modificado.
- Backend Supabase `pickup-eta` v5: no modificado.
- Presencia 15 s; ETA 25 s en movimiento / 45 s detenido; latidos Mapa adaptativos 15/25/30 s: no modificados.

## Verificaciones y pendientes
- Smoke nuevos de copia de acciones, acordeón, selects, revisión obsoleta, identidad visual y preservación de funciones críticas: `web-pasajero/tests/booking-finish-premium-smoke.cjs`.
- Ejecución esperada de `Verify and package passenger PWA` y `Verify passenger PWA review screen` en pull request. **No declarar PASS ni publicación hasta consultar las ejecuciones reales de Actions/Pages.**
- Prueba visual y funcional física aún pendiente en Samsung: apertura/plegado, selector nativo, teclado y desplazamiento, datos completos e incompletos, modo urgente, vuelta a editar, pérdida de conectividad, envío real solamente con autorización para crear reserva.
- No efectuar un envío de prueba en producción ni alterar datos reales sin autorización explícita.
