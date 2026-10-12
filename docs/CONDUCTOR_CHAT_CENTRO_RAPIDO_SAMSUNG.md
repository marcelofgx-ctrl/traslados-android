# Traslados · Conversación desde globito, aviso y respuestas rápidas

**Estado actualizado 12/10/2026:** PR #60 FUSIONADO y RELEASE Conductor 11.5-R5 / Mapa 0.1-R25.0 PUBLICADAS con firma original. PWA v59 vía PR #59. **QA real Samsung continúa pendiente**; guía de descarga, rollback y prueba: [RELEASE_TRASLADOS_20261012_PWA59_MAPA25_CONDUCTOR_R5.md](RELEASE_TRASLADOS_20261012_PWA59_MAPA25_CONDUCTOR_R5.md).

## Diagnóstico desde capturas reales Samsung
- Chat del pasajero en Chrome: reserva `PENDIENTE`, mensaje `Hola` enviado.
- Pantalla emergente del conductor: nueva solicitud, acciones «VER LISTA / PREPARAR PRESUPUESTO», **ningún acceso directo al chat**. Visualmente figura `v11.4 · R10.4 · build 122`.
- **Precaución:** esa etiqueta se encontraba codificada como texto fijo en `conductor/MainActivity.java`, incluso cuando el módulo era embebido dentro de Mapa R24.9. Por ello **la captura NO permite demostrar versión realmente instalada**; se reemplaza por versión de paquete leída desde Android.
- «COMENTARIOS null» aparecía porque se imprimía `null` serializado como texto. Se filtra, sin alterar dato remoto.

## Cambio funcional propuesto
1. **Aviso de nueva solicitud:** ventana compacta adaptada al contenido (sin altura vacía fija), con detalles desplazables y botones principales anclados. Botón «✉ CONVERSAR CON EL PASAJERO» abre el diálogo nativo ya existente (sin presupuesto, sin enviar nada al tocar el botón), mantiene VER LISTA y PREPARAR PRESUPUESTO.
2. **Centro rápido del único globito Mapa:** en una reserva expandida, «✉ CONVERSACIÓN DEL TRASLADO» abre la actividad Conductor **integrada** directamente sobre esa reserva. Si Mapa detecta conducción, pide detenerse primero.
3. **Notificación Android de nuevo mensaje:** al tocar, pasa `open_chat=true` y el ID de la reserva, navegando a la conversación correcta, no solo a la lista.
4. **Chat Conductor:** chips horizontales con tres mensajes sugeridos **editables**. Elegir un chip **solo llena el cuadro**; es imprescindible pulsar «Enviar» para ejecutar `driver_trip_chat_send_v1`. RPC idempotente y PIN siguen siendo los existentes. Botón «Actualizar» conservado. Mientras el diálogo permanezca visible, refresco limitado a **15 s**, sin notificaciones push simuladas y sin repintar ni desplazar la conversación si no cambió; se detiene al cerrarse, y se descartan respuestas después de logout/cambio de PIN. Si la reserva está archivada, mostrar chat de solo lectura sin composición.
5. **WhatsApp no es el chat interno:** accesos anteriores se renombran «WHATSAPP · …» para evitar confundir el envío externo con el chat Supabase.

## Arquitectura y guardas
- Ni PWA v58 ni APK Cliente v15 necesitan cambios para leer o enviar mensajes con las RPC actuales.
- El Conductor embebido en Mapa utiliza el mismo código fuente `conductor/src/**` transformado durante Gradle; el Conductor independiente sigue como respaldo. No generar una segunda burbuja.
- No se cambian credenciales PIN, tokens guest, RLS, número telefónico, ubicación, agenda, precio ni estado de reservas.
- Ninguna respuesta sale automáticamente; no hay mensajes falsos ni mutaciones de producción en tests.
- La red puede fallar: la pantalla no debe anunciar «enviado» hasta recibir confirmación del backend.
- No ofrecer diálogo editable en cerrado/archivado; el servidor sigue teniendo autoridad final sobre posibilidad de enviar.
- Las acciones del Centro rápido se bloquean al conducir; las notificaciones nativas no autorizan uso durante la marcha.

## QA antes de release
1. CI rama: `mapatrayectos/tests/chat-flow-contract-smoke.py`, Mapa DEBUG y Conductor DEBUG compilan con Java 17, SDK 35.
2. Verificar una sola burbuja y que «Traslados → reserva → Conversación» abra directamente el chat correcto.
3. Abrir otra reserva desde el aviso, confirmar que no se duplica la conversación ni se envía nada sin tocar «Enviar».
4. Escribir manualmente/respuesta rápida y comprobar idempotencia de enviar; si falla red, conservar texto.
5. Notificación de mensaje recién recibido abre esa conversación, no crea nueva solicitud y no se interpreta como push del Cliente.
6. Finalizar una reserva **solo si está autorizado**; verificar modo solo lectura y disponibilidad en historial.
7. Comprobar permisos/foco y teclado Samsung; no ocultar «Enviar» al abrir IME.
8. Compilar APK RELEASE con firma original, versionado y rollback; **no publicar ni instalar una APK DEBUG sobre RELEASE**.
9. Comparar con la PWA v58 y PR #59 sin cambios en su backend. Validación física de ida/vuelta con una reserva real requiere autorización explícita.

## Alcance fuera de esta fase
Badge de mensajes no leídos y push FCM cliente cerrado requieren diseño de lectura/acuse o infraestructura backend. No inventar «leído»/«entregado».
