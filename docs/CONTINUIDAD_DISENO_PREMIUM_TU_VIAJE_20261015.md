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
